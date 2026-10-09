#!/usr/bin/env bash
# ==============================================================================
# fix-layout.sh — Refactor de layout.html + templates fragmentados
#
# O que faz:
#   1. Cria config/GlobalModelAdvice.java (injeta temaAtivo/username em toda view)
#   2. Reescreve layout.html para fragmento dinâmico via ${content}
#   3. Converte 5 templates em fragmentos que só têm o conteúdo interno
#   4. Ajusta o `return` de 5 controllers para "layout"
#
# Uso:
#   ./fix-layout.sh              # aplica (com backup)
#   ./fix-layout.sh -n           # dry-run: mostra o que mudaria
#   ./fix-layout.sh -y           # não pede confirmação
#   ./fix-layout.sh -B           # pula backup (não recomendado)
#   ./fix-layout.sh -R           # reverte o último backup
#   ./fix-layout.sh -h           # ajuda
#
# Backup em: .backup-layout/YYYYMMDD-HHMMSS/
# ==============================================================================
set -euo pipefail

# ---------- Flags ----------
DRY_RUN=0
SKIP_BACKUP=0
ASSUME_YES=0
REVERT=0
BACKUP_ROOT=".backup-layout"

# ---------- Cores ----------
if [[ -t 1 ]]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'; C_DIM=$'\033[2m';    C_RESET=$'\033[0m'
else
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_DIM=""; C_RESET=""
fi

ok()   { printf '%s✓%s %s\n' "$C_GREEN"  "$C_RESET" "$*"; }
warn() { printf '%s!%s %s\n' "$C_YELLOW" "$C_RESET" "$*"; }
err()  { printf '%s✗%s %s\n' "$C_RED"    "$C_RESET" "$*" >&2; }
info() { printf '%s→%s %s\n' "$C_BLUE"   "$C_RESET" "$*"; }
dim()  { printf '%s%s%s\n'   "$C_DIM"    "$*"       "$C_RESET"; }

# ---------- Help ----------
uso() {
  sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

# ---------- Argumentos ----------
while getopts ':nyBRh' op; do
  case $op in
    n) DRY_RUN=1 ;;
    y) ASSUME_YES=1 ;;
    B) SKIP_BACKUP=1 ;;
    R) REVERT=1 ;;
    h) uso; exit 0 ;;
    *) err "opção desconhecida: -$OPTARG (use -h)"; exit 2 ;;
  esac
done

# ---------- Preflight ----------
[[ -f pom.xml ]] || { err "pom.xml não encontrado. Rode da raiz do projeto."; exit 1; }
[[ -f src/main/resources/templates/layout.html ]] || { err "layout.html não encontrado."; exit 1; }

BASE_JAVA="src/main/java/com/pfc"
BASE_THINDESK="src/main/java/com/pfc/thindesk"
BASE_TPL="src/main/resources/templates"

LAYOUT="$BASE_TPL/layout.html"
GLOBAL_ADV="$BASE_JAVA/config/GlobalModelAdvice.java"
HOME_CTRL="$BASE_THINDESK/controller/HomeController.java"
ADMIN_CTRL="$BASE_THINDESK/controller/AdminController.java"
TEC_CTRL="$BASE_THINDESK/controller/TecnicoController.java"

TEMPLATES=(
  "$BASE_TPL/chamados.html"
  "$BASE_TPL/clientes.html"
  "$BASE_TPL/ajustes-horarios.html"
  "$BASE_TPL/admin/usuarios.html"
  "$BASE_TPL/tecnico/chamados.html"
)

ARQUIVOS_MODIFICADOS=("$LAYOUT" "$GLOBAL_ADV" "$HOME_CTRL" "$ADMIN_CTRL" "$TEC_CTRL" "${TEMPLATES[@]}")

# ---------- Reverter ----------
if (( REVERT )); then
  [[ -d $BACKUP_ROOT ]] || { err "nenhum backup em $BACKUP_ROOT"; exit 1; }
  LAST=$(ls -1d -- "$BACKUP_ROOT"/*/ 2>/dev/null | LC_ALL=C sort | tail -n1)
  [[ -n $LAST ]] || { err "nenhum backup encontrado"; exit 1; }
  LAST=${LAST%/}
  info "Revertendo de: $LAST"
  while IFS= read -r -d '' f; do
    rel=${f#"$LAST"/}
    mkdir -p -- "$(dirname -- "$rel")"
    cp -p -- "$f" "$rel"
    dim "  restaurado: $rel"
  done < <(find "$LAST" -type f -print0)
  ok "Reversão concluída."
  exit 0
fi

# ---------- Confirmação ----------
echo
info "Refactor que será aplicado:"
echo "  • Cria:  $GLOBAL_ADV"
echo "  • Reescreve: $LAYOUT"
for t in "${TEMPLATES[@]}"; do echo "  • Fragmenta: $t"; done
echo "  • Ajusta returns em: HomeController, AdminController, TecnicoController"
echo
(( DRY_RUN )) && warn "MODO DRY-RUN — nada será escrito."
(( SKIP_BACKUP )) && warn "Backup desabilitado (-B)."
echo

if (( ! ASSUME_YES && ! DRY_RUN )); then
  read -rp "Aplicar agora? [y/N] " resp
  [[ "$resp" =~ ^[Yy]$ ]] || { warn "Cancelado."; exit 0; }
fi

# ---------- Backup ----------
if (( ! DRY_RUN && ! SKIP_BACKUP )); then
  BKP_DIR="$BACKUP_ROOT/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$BKP_DIR"
  for f in "${ARQUIVOS_MODIFICADOS[@]}"; do
    [[ -f $f ]] || continue
    dest="$BKP_DIR/$f"
    mkdir -p -- "$(dirname -- "$dest")"
    cp -p -- "$f" "$dest"
  done
  ok "Backup em: $BKP_DIR"
fi

# ---------- Utilitário: escreve arquivo (idempotente) ----------
# Uso: write_file <destino> <<'EOF' ... EOF
write_file() {
  local dest=$1
  local tmp
  tmp=$(mktemp)
  cat > "$tmp"

  if [[ -f $dest ]] && cmp -s "$dest" "$tmp"; then
    dim "  = $dest (já está atualizado)"
    rm -f "$tmp"
    return 0
  fi

  if (( DRY_RUN )); then
    info "  ~ $dest (seria reescrito)"
    if [[ -f $dest ]]; then
      diff -u "$dest" "$tmp" | head -40 || true
    fi
    rm -f "$tmp"
    return 0
  fi

  mkdir -p -- "$(dirname -- "$dest")"
  mv -f -- "$tmp" "$dest"
  ok "  + $dest"
}

# ---------- Utilitário: substitui um padrão exato em arquivo ----------
# Uso: sub_exact <arquivo> <padrão_literal> <substituto>
sub_exact() {
  local file=$1 from=$2 to=$3
  [[ -f $file ]] || { warn "  ! $file não existe (pulando)"; return 0; }
  if ! grep -qF -- "$from" "$file"; then
    dim "  = $file (nada a substituir: '$from' ausente)"
    return 0
  fi
  if (( DRY_RUN )); then
    info "  ~ $file: '$from' → '$to'"
    return 0
  fi
  # Usa perl para fazer substituição literal (sem regex)
  FROM="$from" TO="$to" perl -i -pe 's/\Q$ENV{FROM}\E/$ENV{TO}/g' "$file"
  ok "  * $file: '$from' → '$to'"
}

echo
info "Aplicando alterações..."
echo

# =============================================================================
# 1. GlobalModelAdvice
# =============================================================================
write_file "$GLOBAL_ADV" <<'JAVA_EOF'
package com.pfc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.pfc.thindesk.service.ThemeService;

/**
 * Injeta em TODA view renderizada por um @Controller:
 *   - temaAtivo → o tema salvo do usuário logado (ou null se anônimo)
 *   - username  → o nome do usuário logado (ou null)
 *
 * Isso evita repetir `model.addAttribute("temaAtivo", ...)` em cada controller.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @Autowired
    private ThemeService themeService;

    @ModelAttribute("temaAtivo")
    public String temaAtivo(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return themeService.temaDoUsuario(auth.getName());
    }

    @ModelAttribute("username")
    public String username(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }
}
JAVA_EOF

# =============================================================================
# 2. layout.html
# =============================================================================
write_file "$LAYOUT" <<'HTML_EOF'
<!doctype html>
<html xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Thindesk</title>
    <link
      href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha3/dist/css/bootstrap.min.css"
      rel="stylesheet"
    />
    <link
      href="https://cdn.jsdelivr.net/npm/bootstrap-icons/font/bootstrap-icons.css"
      rel="stylesheet"
    />
    <link rel="stylesheet" th:href="@{/css/style.css}" />

    <!-- Tema ativo do usuário (injetado pelo GlobalModelAdvice). -->
    <link
      rel="stylesheet"
      th:href="@{'/css/themes/' + (${temaAtivo} ?: 'gruvbox-dark') + '.css'}"
    />
  </head>

  <body th:attr="data-theme=${temaAtivo} ?: 'default'">
    <div class="toggle-btn btn btn-dark d-md-none">
      <span>&#9776;</span>
    </div>

    <aside>
      <div th:replace="~{fragments/sidebar :: sidebar}"></div>
    </aside>

    <!-- Fragmento dinâmico: o controller injeta "template :: content" -->
    <main class="container-fluid mt-4">
      <div th:replace="${content}"></div>
    </main>

    <script th:src="@{/js/script.js}"></script>
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha3/dist/js/bootstrap.bundle.min.js"></script>
  </body>
</html>
HTML_EOF

# =============================================================================
# 3. Templates como fragmentos
# =============================================================================
write_file "$BASE_TPL/chamados.html" <<'HTML_EOF'
<div th:fragment="content" class="container-fluid"
     xmlns:th="http://www.thymeleaf.org">
  <h2 class="mb-4">Lista de Chamados</h2>

  <table class="table table-striped">
    <thead>
      <tr>
        <th>Descrição</th>
        <th>Status</th>
        <th>Tipo</th>
        <th>Técnico</th>
        <th>Usuário</th>
        <th>Ações</th>
      </tr>
    </thead>
    <tbody>
      <tr th:each="chamado : ${chamados}">
        <td th:text="${chamado?.descricao}"></td>
        <td th:text="${chamado?.status}"></td>
        <td th:text="${chamado?.tipo}"></td>
        <td th:text="${chamado?.tecnico}"></td>
        <td th:text="${chamado?.usuario}"></td>
        <td>
          <a th:href="@{'/chamados/novo'}" class="btn btn-primary btn-sm">Novo</a>
          <a th:href="@{'/chamados/atualizar/' + ${chamado.id}}"
             class="btn btn-warning btn-sm">Editar</a>
          <a th:href="@{'/chamados/cancelar/' + ${chamado.id}}"
             class="btn btn-danger btn-sm">Cancelar</a>
        </td>
      </tr>
    </tbody>
  </table>
</div>
HTML_EOF

write_file "$BASE_TPL/clientes.html" <<'HTML_EOF'
<div th:fragment="content" class="container-fluid"
     xmlns:th="http://www.thymeleaf.org">
  <h1 class="mt-4">Clientes</h1>

  <table class="table table-bordered table-striped mt-3">
    <thead>
      <tr>
        <th>Nome</th>
        <th>Telefone</th>
        <th>Setor</th>
        <th>Ações</th>
      </tr>
    </thead>
    <tbody>
      <tr th:each="cliente : ${clientes}">
        <td th:text="${cliente.nome}"></td>
        <td th:text="${cliente.telefone}"></td>
        <td th:text="${cliente.setor}"></td>
        <td>
          <a th:href="@{'/clientes/novo'}" class="btn btn-primary btn-sm">Novo</a>
          <a th:href="@{'/clientes/editar/' + ${cliente.id}}"
             class="btn btn-warning btn-sm">Editar</a>
          <a th:href="@{'/clientes/deletar/' + ${cliente.id}}"
             class="btn btn-danger btn-sm">Excluir</a>
        </td>
      </tr>
    </tbody>
  </table>
</div>
HTML_EOF

write_file "$BASE_TPL/ajustes-horarios.html" <<'HTML_EOF'
<div th:fragment="content" class="container-fluid"
     xmlns:th="http://www.thymeleaf.org">
  <h1 class="mt-4">Ajustes de Horários por Setor</h1>

  <form th:action="@{/ajustes-horarios/salvar}" th:object="${novoHorario}"
        method="post" class="mb-4">
    <div class="row">
      <div class="col-md-3">
        <label for="setor" class="form-label">Setor</label>
        <input type="text" id="setor" name="setor" th:value="*{setor}"
               class="form-control" required />
      </div>
      <div class="col-md-2">
        <label for="diaSemana" class="form-label">Dia</label>
        <input type="text" id="diaSemana" name="diaSemana" th:value="*{diaSemana}"
               class="form-control" required />
      </div>
      <div class="col-md-2">
        <label for="horarioInicio" class="form-label">Início</label>
        <input type="time" id="horarioInicio" name="horarioInicio"
               th:value="*{horarioInicio}" class="form-control" required />
      </div>
      <div class="col-md-2">
        <label for="horarioFim" class="form-label">Fim</label>
        <input type="time" id="horarioFim" name="horarioFim"
               th:value="*{horarioFim}" class="form-control" required />
      </div>
      <div class="col-md-3 d-flex align-items-end">
        <button type="submit" class="btn btn-primary">Salvar</button>
      </div>
    </div>
  </form>

  <table class="table table-striped">
    <thead>
      <tr><th>Setor</th><th>Dia</th><th>Início</th><th>Fim</th><th>Ações</th></tr>
    </thead>
    <tbody>
      <tr th:each="horario : ${horarios}">
        <td th:text="${horario?.setor}"></td>
        <td th:text="${horario?.diaSemana}"></td>
        <td th:text="${horario?.horarioInicio}"></td>
        <td th:text="${horario?.horarioFim}"></td>
        <td>
          <a th:href="@{/ajustes-horarios/deletar/{id}(id=${horario.id})}"
             class="btn btn-danger btn-sm">Deletar</a>
        </td>
      </tr>
    </tbody>
  </table>
</div>
HTML_EOF

write_file "$BASE_TPL/admin/usuarios.html" <<'HTML_EOF'
<div th:fragment="content" class="container-fluid"
     xmlns:th="http://www.thymeleaf.org">
  <h2 class="mb-4">Gerenciar Usuários</h2>

  <table class="table table-striped align-middle">
    <thead>
      <tr>
        <th>Usuário</th><th>Email</th><th>Nome</th>
        <th>Perfil</th><th>Ativo</th><th>Ações</th>
      </tr>
    </thead>
    <tbody>
      <tr th:each="u : ${usuarios}">
        <td th:text="${u.username}"></td>
        <td th:text="${u.email}"></td>
        <td th:text="${u.nomeCompleto}"></td>
        <td>
          <form th:action="@{'/admin/usuarios/' + ${u.id} + '/role'}"
                method="post" class="d-flex">
            <select name="role" class="form-select form-select-sm me-2">
              <option th:each="r : ${T(com.pfc.thindesk.entity.Role).values()}"
                      th:value="${r.name()}"
                      th:text="${r.name()}"
                      th:selected="${u.roles.contains(r)}"></option>
            </select>
            <button class="btn btn-sm btn-primary">Salvar</button>
          </form>
        </td>
        <td>
          <span th:if="${u.ativo}" class="badge bg-success">Sim</span>
          <span th:unless="${u.ativo}" class="badge bg-danger">Não</span>
        </td>
        <td>
          <form th:action="@{'/admin/usuarios/' + ${u.id} + '/toggle-ativo'}"
                method="post" class="d-inline">
            <button class="btn btn-sm btn-warning">
              <span th:if="${u.ativo}">Desativar</span>
              <span th:unless="${u.ativo}">Ativar</span>
            </button>
          </form>
        </td>
      </tr>
    </tbody>
  </table>
</div>
HTML_EOF

write_file "$BASE_TPL/tecnico/chamados.html" <<'HTML_EOF'
<div th:fragment="content" class="container-fluid"
     xmlns:th="http://www.thymeleaf.org">
  <h2 class="mb-4">
    Fila de Chamados — <span th:text="${usuarioAtual}"></span>
  </h2>

  <table class="table table-striped">
    <thead>
      <tr>
        <th>Descrição</th><th>Status</th><th>Técnico</th>
        <th>Usuário</th><th>Ações</th>
      </tr>
    </thead>
    <tbody>
      <tr th:each="c : ${chamados}">
        <td th:text="${c.descricao}"></td>
        <td><span class="badge bg-secondary" th:text="${c.status}"></span></td>
        <td th:text="${c.tecnico}"></td>
        <td th:text="${c.usuario}"></td>
        <td>
          <form th:action="@{'/tecnico/chamados/' + ${c.id} + '/assumir'}"
                method="post" class="d-inline">
            <button class="btn btn-sm btn-primary">Assumir</button>
          </form>
          <form th:action="@{'/tecnico/chamados/' + ${c.id} + '/status'}"
                method="post" class="d-inline">
            <input type="hidden" name="status" value="Concluído" />
            <button class="btn btn-sm btn-success">Concluir</button>
          </form>
        </td>
      </tr>
    </tbody>
  </table>
</div>
HTML_EOF

# =============================================================================
# 4. Controllers — ajustar return para "layout"
# =============================================================================
echo
info "Ajustando returns dos controllers..."

# HomeController: /chamados, /clientes, /ajustes-horarios
sub_exact "$HOME_CTRL" 'return "chamados";'         'return "layout";'
sub_exact "$HOME_CTRL" 'return "clientes";'         'return "layout";'
sub_exact "$HOME_CTRL" 'return "ajustes-horarios";' 'return "layout";'

# AdminController
sub_exact "$ADMIN_CTRL" 'return "admin/usuarios";'  'return "layout";'

# TecnicoController
sub_exact "$TEC_CTRL" 'return "tecnico/chamados";'  'return "layout";'

# =============================================================================
# 5. Verificação (somente se aplicou de verdade)
# =============================================================================
if (( DRY_RUN )); then
  echo
  warn "DRY-RUN — nada foi escrito. Verificação de estado pulada."
  echo
  dim "Próximo passo: rode novamente sem -n para aplicar."
  exit 0
fi

echo
info "Verificando..."
echo

check_file() {
  local f=$1 label=$2
  if [[ -f $f ]]; then
    dim "  [ok] $label"
    return 0
  else
    warn "  [!!] $label — ausente"
    return 1
  fi
}

FALHAS=0
check_file "$GLOBAL_ADV" "GlobalModelAdvice.java" || FALHAS=$((FALHAS+1))
check_file "$LAYOUT"     "layout.html"             || FALHAS=$((FALHAS+1))
for t in "${TEMPLATES[@]}"; do
  check_file "$t" "$(basename "$t")" || FALHAS=$((FALHAS+1))
done

if grep -q 'th:replace="\${content}"' "$LAYOUT" 2>/dev/null; then
  dim "  [ok] layout.html usa th:replace=\${content}"
else
  warn "  [!!] layout.html NÃO usa th:replace=\${content}"
  FALHAS=$((FALHAS+1))
fi

for c in "$HOME_CTRL" "$ADMIN_CTRL" "$TEC_CTRL"; do
  if grep -qE 'return "(chamados|clientes|ajustes-horarios|admin/usuarios|tecnico/chamados)";' "$c" 2>/dev/null; then
    warn "  [!!] $c ainda tem return para view antiga"
    FALHAS=$((FALHAS+1))
  else
    dim "  [ok] $(basename "$c") — sem returns antigos"
  fi
done

echo
if (( FALHAS == 0 )); then
  ok "Refactor aplicado com sucesso."
  echo
  dim "Próximos passos:"
  dim "  1. Reinicie a app:  ./mvnw spring-boot:run"
  dim "  2. Faça login e escolha um tema em /tema"
  dim "  3. Navegue por /chamados, /clientes, /admin/usuarios"
  dim "  4. Confirme que o CSS do tema aparece no <head> de cada página"
else
  warn "Concluído com $FALHAS aviso(s). Revise acima."
fi
echo
dim "Para reverter: ./fix-layout.sh -R"
