#!/usr/bin/env bash
# ============================================================
# test-frontend.sh — Testa páginas e assets estáticos
# Requer a app rodando em http://localhost:8000
# ============================================================
export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"
set -uo pipefail

BASE="${BASE:-http://localhost:8000}"
PASS=0; FAIL=0; TOTAL=0

if [ -t 1 ]; then
  G="\033[0;32m"; R="\033[0;31m"; Y="\033[0;33m"; B="\033[1;34m"; N="\033[0m"
else
  G=""; R=""; Y=""; B=""; N=""
fi

ok()   { PASS=$((PASS+1)); TOTAL=$((TOTAL+1)); printf "  ${G}[OK]${N}   %s\n" "$1"; }
ko()   { FAIL=$((FAIL+1)); TOTAL=$((TOTAL+1)); printf "  ${R}[FAIL]${N} %s  ${Y}(esperado %s, obtido %s)${N}\n" "$1" "$2" "$3"; }
info() { printf "\n${B}== %s ==${N}\n" "$1"; }

status_is() {
  local desc="$1" url="$2" esperado="$3"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -L "$url")
  [ "$code" = "$esperado" ] && ok "$desc (HTTP $code)" || ko "$desc" "$esperado" "$code"
}

body_has() {
  local desc="$1" url="$2" needle="$3"
  if curl -s -L "$url" | grep -qi -- "$needle"; then
    ok "$desc contem \"$needle\""
  else
    ko "$desc contem \"$needle\"" "encontrado" "nao encontrado"
  fi
}

# Aceita application/javascript OU text/javascript (RFC 9239 / Tomcat 10.1)
content_type_is() {
  local desc="$1" url="$2" esperado="$3"
  local ct
  ct=$(curl -s -o /dev/null -w "%{content_type}" "$url" | cut -d';' -f1 | tr -d ' ')
  case "$ct" in
    "$esperado"*)           ok "$desc Content-Type ($ct)" ;;
    text/javascript)        ok "$desc Content-Type ($ct)" ;;
    application/javascript) ok "$desc Content-Type ($ct)" ;;
    *)                      ko "$desc Content-Type" "$esperado*|text/javascript" "$ct" ;;
  esac
}

# ---------- 0. Server ----------
info "0. Servidor no ar?"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/login" || echo "000")
if [ "$CODE" = "000" ]; then
  printf "${R}Servidor nao responde em %s.${N}\n" "$BASE"
  exit 1
fi
ok "Servidor responde em $BASE"

# ---------- 1. Páginas públicas ----------
info "1. Paginas publicas"

status_is "GET /login"     "$BASE/login"     "200"
status_is "GET /registrar" "$BASE/registrar" "200"
status_is "GET /puzzle"    "$BASE/puzzle"    "200"

body_has "login.html"     "$BASE/login"     "Bem-vindo"
body_has "login.html"     "$BASE/login"     "loginForm"
body_has "login.html"     "$BASE/login"     "puzzleModal"
body_has "registrar.html" "$BASE/registrar" "Criar conta"
body_has "registrar.html" "$BASE/registrar" "registroForm"
body_has "puzzle.html"    "$BASE/puzzle"    "Desafio de Xadrez"
body_has "puzzle.html"    "$BASE/puzzle"    "puzzleForm"

# ---------- 2. Assets ----------
info "2. Assets estaticos"

status_is "GET /css/style.css"                "$BASE/css/style.css"                "200"
status_is "GET /js/script.js"                 "$BASE/js/script.js"                 "200"
status_is "GET /js/puzzle-modal.js"           "$BASE/js/puzzle-modal.js"           "200"
status_is "GET /css/puzzle-modal.css"         "$BASE/css/puzzle-modal.css"         "200"
status_is "GET /css/themes/gruvbox-dark.css"  "$BASE/css/themes/gruvbox-dark.css"  "200"
status_is "GET /css/themes/gruvbox-light.css" "$BASE/css/themes/gruvbox-light.css" "200"

content_type_is "style.css"       "$BASE/css/style.css"       "text/css"
content_type_is "script.js"       "$BASE/js/script.js"        "application/javascript"
content_type_is "puzzle-modal.js" "$BASE/js/puzzle-modal.js"  "application/javascript"

body_has "style.css"       "$BASE/css/style.css"                       "#sidebar"
body_has "script.js"       "$BASE/js/script.js"                        "toggleSubmenu"
body_has "puzzle-modal.js" "$BASE/js/puzzle-modal.js"                  "PuzzleModal"
body_has "gruvbox-dark"    "$BASE/css/themes/gruvbox-dark.css"         "gruvbox"

# ---------- 3. Rotas protegidas ----------
info "3. Rotas protegidas sem token"

for rota in "/" "/chamados" "/clientes" "/ajustes-horarios" "/admin/usuarios" "/tecnico/chamados"; do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE$rota")
  case "$CODE" in
    302|303) ok "GET $rota -> redirect /login (HTTP $CODE)" ;;
    401|403) ok "GET $rota -> bloqueado (HTTP $CODE)" ;;
    *)       ko "GET $rota" "302/303/401/403" "$CODE" ;;
  esac
done

# ---------- 4. Página de erro ----------
info "4. Pagina de erro"

CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/rota_que_nao_existe_xyz")
case "$CODE" in
  404) ok "GET rota inexistente -> 404" ;;
  403) ok "GET rota inexistente -> 403 (anyRequest().authenticated)" ;;
  *)   ko "GET rota inexistente" "404 ou 403" "$CODE" ;;
esac

# ---------- 5. Cadastro pela API (simula form HTML) ----------
info "5. Cadastro via JSON"

USERNAME="front_$(date +%s)_$RANDOM"
EMAIL="${USERNAME}@front.local"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/registrar" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"senha12345\",\"nomeCompleto\":\"Front Teste\"}")
[ "$CODE" = "201" ] && ok "Cadastro via JSON retorna 201" || ko "Cadastro via JSON" "201" "$CODE"

# ---------- 6. Resumo ----------
info "Resumo"
printf "  Total: %d   ${G}OK: %d${N}   ${R}Falhas: %d${N}\n" "$TOTAL" "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
