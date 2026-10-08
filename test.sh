#!/usr/bin/env bash
set -e

BASE=http://localhost:8000

extract_header_token() {
  grep -i '^Authorization:' | sed 's/^[Aa]uthorization: *//' | sed 's/^Bearer *//' | tr -d '\r'
}

extract_json_field() {
  # $1 = json string, $2 = field name
  echo "$1" | sed -n 's/.*"'"$2"'":"\([^"]*\)".*/\1/p'
}

echo "===== 1. Sem token (espera 403) ====="
curl -s -o /dev/null -w "HTTP %{http_code}\n" $BASE/api/chamados

echo
echo "===== 2. Login ====="
LOGIN=$(curl -s -i -X POST "$BASE/api/auth/login?username=admin&password=admin123")
echo "$LOGIN" | head -1
PRE=$(echo "$LOGIN" | extract_header_token)
PUZZLE_ID=$(echo "$LOGIN" | extract_json_field "$(echo "$LOGIN" | tail -1)" puzzleId)
echo "preAuth token: ${PRE:0:50}..."
echo "puzzleId:      $PUZZLE_ID"

echo
echo "===== 3. preAuth em endpoint protegido (espera 403) ====="
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $PRE" $BASE/api/chamados

echo
echo "===== 3.5. Lance ERRADO (espera 403 + tentativas) ====="
curl -s -X POST "$BASE/api/auth/resolver-puzzle?puzzleId=$PUZZLE_ID&lanceFen=a1a1" \
  -H "Authorization: Bearer $PRE"
echo

echo
echo "===== 4. Lance CORRETO (e2e4) ====="
RESOLVE=$(curl -s -i -X POST "$BASE/api/auth/resolver-puzzle?puzzleId=$PUZZLE_ID&lanceFen=e2e4" \
        -H "Authorization: Bearer $PRE")
echo "$RESOLVE" | head -1
FINAL=$(echo "$RESOLVE" | extract_header_token)
echo "final token: ${FINAL:0:50}..."

echo
echo "===== 5. Acessa protegido com token final (espera 200) ====="
curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $FINAL" $BASE/api/chamados

echo
echo "===== 6. Criar chamado ====="
curl -s -X POST $BASE/api/chamados \
  -H "Authorization: Bearer $FINAL" \
  -H "Content-Type: application/json" \
  -d '{"descricao":"Impressora","status":"Aberto","tipo":"Manutencao","tecnico":"Joao","usuario":"Alberto"}'
echo

echo
echo "===== 7. Listar chamados ====="
curl -s -H "Authorization: Bearer $FINAL" $BASE/api/chamados
echo

echo
echo "===== 8. Criar cliente ====="
curl -s -X POST $BASE/api/clientes \
  -H "Authorization: Bearer $FINAL" \
  -H "Content-Type: application/json" \
  -d '{"nome":"Maria","telefone":"11999998888","setor":"Financeiro"}'
echo

echo
echo "===== 9. Listar clientes ====="
curl -s -H "Authorization: Bearer $FINAL" $BASE/api/clientes
echo
