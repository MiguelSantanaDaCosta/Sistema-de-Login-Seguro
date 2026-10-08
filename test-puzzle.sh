#!/usr/bin/env bash
# Testa: rotação após 5 erros, puzzleId obsoleto, limite por sessão e (opcional) timeout.
# Requer a app em http://localhost:8000, admin/admin123 e mongosh (ou o container mongo-thindesk).
#
# Para testar o timeout: suba a app com PUZZLE_TEMPO_LIMITE=5 e rode:
#   TESTAR_TIMEOUT=1 ./test-puzzle.sh
set -e

BASE=http://localhost:8000
MONGO_URI=${MONGO_URI:-$(grep -E '^MONGO_URI=' .env 2>/dev/null | cut -d= -f2-)}

extract_header_token() {
  grep -i '^Authorization:' | sed 's/^[Aa]uthorization: *//' | sed 's/^Bearer *//' | tr -d '\r'
}
campo_puzzle_id() { grep -o '"puzzleId":"[^"]*"' | head -1 | cut -d'"' -f4; }

buscar_lance() {
  local js="db.puzzles_xadrez.findOne({_id: '$1'}).lanceCorreto"
  if command -v mongosh >/dev/null 2>&1 && [ -n "$MONGO_URI" ]; then
    mongosh "$MONGO_URI" --quiet --eval "$js" | tr -d '\r\n'
  else
    docker exec mongo-thindesk mongosh thindesk --quiet --eval "$js" | tr -d '\r\n'
  fi
}

login() {
  LOGIN=$(curl -s -i -X POST "$BASE/api/auth/login?username=admin&password=admin123")
  PRE=$(echo "$LOGIN" | extract_header_token)
  PUZZLE_ID=$(echo "$LOGIN" | tail -1 | campo_puzzle_id)
}

# Envia um lance errado; preenche CODE e BODY
errar() {
  local extra=${1:-}
  local resp
  resp=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/resolver-puzzle" \
    -H "Authorization: Bearer $PRE" -d "lanceFen=a1a1$extra")
  CODE=${resp##*$'\n'}
  BODY=${resp%$'\n'*}
}

echo "===== 1. Login: puzzle inicial ====="
login
echo "puzzleId inicial: $PUZZLE_ID"

echo
echo "===== 2. Cinco erros seguidos (esperado: 403 x5; o 5º traz novoPuzzle) ====="
for i in 1 2 3 4 5; do
  errar
  echo "erro $i -> HTTP $CODE"
done
echo "$BODY"
NOVO_ID=$(echo "$BODY" | campo_puzzle_id)
echo "novo puzzleId: $NOVO_ID"
[ -n "$NOVO_ID" ] && [ "$NOVO_ID" != "$PUZZLE_ID" ] && echo "OK: puzzle trocado" || echo "FALHOU: puzzle não trocou"

echo
echo "===== 3. puzzleId obsoleto (espera 409) ====="
errar "&puzzleId=$PUZZLE_ID"
echo "HTTP $CODE"

echo
echo "===== 4. Lance correto do NOVO puzzle (espera 200) ====="
LANCE=$(buscar_lance "$NOVO_ID")
echo "lanceCorreto: $LANCE"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE/api/auth/resolver-puzzle" \
  -H "Authorization: Bearer $PRE" -d "lanceFen=$LANCE&puzzleId=$NOVO_ID"

echo
echo "===== 5. Limite por sessão: 15 erros em um login (espera 429 no 15º) ====="
login
for i in $(seq 1 15); do
  errar
  [ "$i" -eq 5 ] || [ "$i" -eq 10 ] || [ "$i" -eq 15 ] && echo "erro $i -> HTTP $CODE"
done
echo "$BODY"

if [ "${TESTAR_TIMEOUT:-0}" = "1" ]; then
  echo
  echo "===== 6. Timeout (app precisa estar com PUZZLE_TEMPO_LIMITE=5) ====="
  login
  echo "puzzleId: $PUZZLE_ID — aguardando 6 s..."
  sleep 6
  errar
  echo "resolver após o prazo -> HTTP $CODE (espera 410 com novoPuzzle)"
  echo "$BODY"
  sleep 6
  RESP=$(curl -s -X POST "$BASE/api/auth/novo-puzzle" -H "Authorization: Bearer $PRE")
  echo "novo-puzzle após o prazo -> $RESP"
fi
