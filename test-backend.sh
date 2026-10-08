#!/usr/bin/env bash
# ============================================================
# test-backend.sh — Testa a API do Thindesk (backend)
# Requer a app rodando em http://localhost:8000
# ============================================================
export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"
set -uo pipefail

BASE="${BASE:-http://localhost:8000}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"

PASS=0; FAIL=0; TOTAL=0

if [ -t 1 ]; then
  G="\033[0;32m"; R="\033[0;31m"; Y="\033[0;33m"; B="\033[1;34m"; N="\033[0m"
else
  G=""; R=""; Y=""; B=""; N=""
fi

ok()   { PASS=$((PASS+1)); TOTAL=$((TOTAL+1)); printf "  ${G}[OK]${N}   %s\n" "$1"; }
ko()   { FAIL=$((FAIL+1)); TOTAL=$((TOTAL+1)); printf "  ${R}[FAIL]${N} %s  ${Y}(esperado %s, obtido %s)${N}\n" "$1" "$2" "$3"; }
skip() { TOTAL=$((TOTAL+1)); printf "  ${Y}[SKIP]${N} %s\n" "$1"; }
info() { printf "\n${B}== %s ==${N}\n" "$1"; }

assert_status() {
  local desc="$1" esperado="$2" obtido="$3"
  [ "$esperado" = "$obtido" ] && ok "$desc (HTTP $obtido)" || ko "$desc" "$esperado" "$obtido"
}

# Extrai string do JSON
json_str() {
  local campo="$1"
  grep -o "\"$campo\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" \
    | head -1 | sed 's/.*:[[:space:]]*"\([^"]*\)"/\1/'
}

# Extrai boolean ou número simples
json_bool() {
  local campo="$1"
  grep -o "\"$campo\"[[:space:]]*:[[:space:]]*\(true\|false\)" \
    | head -1 | sed 's/.*:[[:space:]]*\(true\|false\)/\1/'
}

header_token() {
  grep -i '^Authorization:' | sed 's/^[Aa]uthorization: *//' | sed 's/^Bearer *//' | tr -d '\r'
}

# ---------- 0. Server no ar? ----------
info "0. Verificando se o servidor responde"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/login" || echo "000")
if [ "$CODE" = "000" ]; then
  printf "${R}Servidor nao responde em %s. Suba a app antes (./mvnw spring-boot:run).${N}\n" "$BASE"
  exit 1
fi
assert_status "GET /login responde" "200" "$CODE"

# ---------- 1. Cadastro público ----------
info "1. Cadastro publico (/api/auth/registrar)"
USERNAME="teste_$(date +%s)_$RANDOM"
EMAIL="${USERNAME}@teste.local"
PASSWORD="senha12345"
NOME="Usuario de Teste"

CODE=$(curl -s -o /tmp/reg.json -w "%{http_code}" -X POST "$BASE/api/auth/registrar" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"nomeCompleto\":\"$NOME\"}")
assert_status "POST /api/auth/registrar cria usuario" "201" "$CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/registrar" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"nomeCompleto\":\"$NOME\"}")
assert_status "POST registrar duplicado -> 409" "409" "$CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/registrar" \
  -H "Content-Type: application/json" \
  -d '{"username":"a","email":"nao-email","password":"1","nomeCompleto":""}')
assert_status "POST registrar invalido -> 400" "400" "$CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/registrar" \
  -H "Content-Type: application/json" -d 'nao-e-json')
assert_status "POST registrar JSON quebrado -> 400" "400" "$CODE"

# ---------- 2. Login 2FA ----------
info "2. Login com 2FA (/api/auth/login)"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/login" \
  -d "username=$ADMIN_USER&password=senha_errada")
assert_status "Login com senha errada -> 401" "401" "$CODE"

RESP=$(curl -s -i -X POST "$BASE/api/auth/login" \
  -d "username=$ADMIN_USER&password=$ADMIN_PASS")
CODE=$(printf '%s' "$RESP" | head -1 | awk '{print $2}')
PRE_AUTH=$(printf '%s' "$RESP" | header_token)
PUZZLE_ID=$(printf '%s' "$RESP" | tail -1 | json_str puzzleId)

assert_status "Login valido -> 200" "200" "$CODE"
[ -n "$PRE_AUTH" ]  && ok "Login retorna header Authorization" || ko "Login retorna header Authorization" "presente" "vazio"
[ -n "$PUZZLE_ID" ] && ok "Login retorna puzzleId ($PUZZLE_ID)" || ko "Login retorna puzzleId" "presente" "vazio"

# ---------- 3. Puzzle ----------
info "3. Puzzle (/api/auth/resolver-puzzle e /novo-puzzle)"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/resolver-puzzle" -d "lanceFen=a1a1")
assert_status "Resolver puzzle sem token -> 401" "401" "$CODE"

if [ -n "$PRE_AUTH" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/resolver-puzzle" \
    -H "Authorization: Bearer $PRE_AUTH" -d "lanceFen=a1a1&puzzleId=$PUZZLE_ID")
  assert_status "Lance errado -> 403" "403" "$CODE"

  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/resolver-puzzle" \
    -H "Authorization: Bearer $PRE_AUTH" -d "lanceFen=a1a1&puzzleId=id_que_nao_existe")
  assert_status "puzzleId invalido -> 409" "409" "$CODE"

  RESP=$(curl -s -X POST "$BASE/api/auth/novo-puzzle" -H "Authorization: Bearer $PRE_AUTH")
  RENOVADO=$(printf '%s' "$RESP" | json_bool renovado)
  case "$RENOVADO" in
    false) ok "/novo-puzzle dentro do prazo -> renovado:false" ;;
    true)  skip "/novo-puzzle retornou renovado:true (prazo ja tinha expirado)" ;;
    *)     ko "/novo-puzzle dentro do prazo" "renovado:false" "renovado:$RENOVADO" ;;
  esac
else
  skip "Puzzle (sem PRE_AUTH do login)"
fi

# ---------- 4. Rotas protegidas sem token ----------
info "4. Rotas protegidas — sem token"

for rota in "/" "/chamados" "/clientes" "/ajustes-horarios" "/admin/usuarios" "/tecnico/chamados"; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$rota")
  case "$CODE" in
    302|303) ok "GET $rota -> redirect /login (HTTP $CODE)" ;;
    401|403) ok "GET $rota -> bloqueado (HTTP $CODE)" ;;
    *)       ko "GET $rota" "302/303/401/403" "$CODE" ;;
  esac
done

for api in "/api/chamados" "/api/clientes"; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$api")
  case "$CODE" in
    401|403) ok "GET $api sem token (HTTP $CODE)" ;;
    *)       ko "GET $api sem token" "401/403" "$CODE" ;;
  esac
done

# ---------- 5. Token final (resolve puzzle) ----------
info "5. Rotas protegidas com token final"

RESP=$(curl -s -i -X POST "$BASE/api/auth/login" -d "username=$ADMIN_USER&password=$ADMIN_PASS")
PRE_AUTH=$(printf '%s' "$RESP" | header_token)
PUZZLE_ID=$(printf '%s' "$RESP" | tail -1 | json_str puzzleId)

MONGO_URI="${MONGO_URI:-$(grep -E '^MONGO_URI=' .env 2>/dev/null | cut -d= -f2-)}"
buscar_lance() {
  local js="db.puzzles_xadrez.findOne({_id: '$1'}).lanceCorreto"
  if command -v mongosh >/dev/null 2>&1 && [ -n "$MONGO_URI" ]; then
    mongosh "$MONGO_URI" --quiet --eval "$js" 2>/dev/null | tr -d '\r\n'
  elif command -v docker >/dev/null 2>&1; then
    docker run --rm --network host mongodb/mongodb-community-server:8.0-ubi8 \
      mongosh "$MONGO_URI" --quiet --eval "$js" 2>/dev/null | tr -d '\r\n'
  fi
}

if [ -n "$PRE_AUTH" ] && [ -n "$PUZZLE_ID" ]; then
  LANCE=$(buscar_lance "$PUZZLE_ID" || true)
  if [ -n "$LANCE" ]; then
    RESP=$(curl -s -i -X POST "$BASE/api/auth/resolver-puzzle" \
      -H "Authorization: Bearer $PRE_AUTH" \
      -d "lanceFen=$LANCE&puzzleId=$PUZZLE_ID")
    CODE=$(printf '%s' "$RESP" | head -1 | awk '{print $2}')
    FINAL_TOKEN=$(printf '%s' "$RESP" | header_token)
    assert_status "Resolver puzzle corretamente -> 200" "200" "$CODE"
    [ -n "$FINAL_TOKEN" ] && ok "Login 2FA devolve token final" || ko "Token final" "presente" "vazio"

    if [ -n "$FINAL_TOKEN" ]; then
      CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $FINAL_TOKEN" "$BASE/api/chamados")
      assert_status "GET /api/chamados com token" "200" "$CODE"

      CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $FINAL_TOKEN" "$BASE/api/clientes")
      assert_status "GET /api/clientes com token" "200" "$CODE"

      CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $FINAL_TOKEN" "$BASE/admin/usuarios")
      assert_status "GET /admin/usuarios com ROLE_ADMIN" "200" "$CODE"
    fi
  else
    skip "Resolucao do puzzle (mongosh/docker indisponivel para ler lanceCorreto)"
  fi
else
  skip "Token final (sem PRE_AUTH ou PUZZLE_ID)"
fi

CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/logout")
assert_status "POST /api/auth/logout" "200" "$CODE"

# ---------- 6. Resumo ----------
info "Resumo"
printf "  Total: %d   ${G}OK: %d${N}   ${R}Falhas: %d${N}\n" "$TOTAL" "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
