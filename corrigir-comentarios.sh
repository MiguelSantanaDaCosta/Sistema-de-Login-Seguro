#!/usr/bin/env bash
# ==============================================================================
# corrigir-comentarios.sh — corrige comentários do projeto Thindesk
#
# Ações suportadas (todas IDEMPOTENTES):
#   1) REMOVER linhas de comentário redundantes que só parafraseiam o método.
#   2) REMOVER duplicatas de descrição em controllers.
#   3) REMOVER propriedade legada do application.properties.
#   4) ADICIONAR cabeçalho padrão (autor + descrição) em arquivos que não têm.
#   5) ADICIONAR um TODO explícito no TecnicoController.
#
# A tabela de regras está nas funções `regras_remover()`, `regras_adicionar()`
# e `regras_header()`. Cada linha usa `:::` como separador:
#
#   regras_remover:   arquivo:::texto_exato_da_linha
#   regras_adicionar: arquivo:::regex_ancora:::texto_do_comentario
#   regras_header:    arquivo:::NomeDaClasse:::Descrição curta
#
# A comparação de linhas ignora espaços/tabs nas pontas (trim), então o script
# não se importa com a indentação original — só com o conteúdo do comentário.
# Rodar duas vezes não remove/insere nada na segunda vez.
#
# USO RÁPIDO
#   ./corrigir-comentarios.sh           # aplica (com backup automático)
#   ./corrigir-comentarios.sh -n        # dry-run: mostra o que faria
#   ./corrigir-comentarios.sh -R        # reverte para o backup mais recente
#   ./corrigir-comentarios.sh -T        # autoteste interno (não toca no projeto)
#   ./corrigir-comentarios.sh -b meu_bkp
#   ./corrigir-comentarios.sh -q
#   ./corrigir-comentarios.sh -h
#
# OPÇÕES
#   -n       dry-run (não modifica nada, apenas relata)
#   -R       reverte para o backup mais recente e sai
#   -T       roda o autoteste interno e sai
#   -b DIR   diretório de backup (padrão: .backup)
#   -B       pula o backup (não recomendado)
#   -q       modo quiet
#   -h       mostra esta ajuda
#
# SEGURANÇA
#   Antes de qualquer modificação, uma cópia é salva em
#   .backup/<YYYYMMDD-HHMMSS>/<caminho-relativo>. `-R` restaura a mais recente.
#   `-n` mostra o que mudaria sem escrever. `-T` roda testes isolados em /tmp.
#
# REQUISITOS: bash >= 4.4, awk (GNU), mktemp, cmp, cp, find, diff. Passa em shellcheck.
# Todos os caminhos são relativos à raiz do projeto: pode mover de lugar.
# ==============================================================================
set -euo pipefail

# ---------- Configuração ------------------------------------------------------
BACKUP_ROOT=".backup"
SKIP_BACKUP=0
DRY_RUN=0
QUIET=0
REVERTER=0
AUTOTESTE=0

# ---------- Utilidades --------------------------------------------------------
die()  { printf 'erro: %s\n' "$*" >&2; exit 1; }
log()  { (( QUIET )) || printf '%s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
skip() { (( QUIET )) || printf '[SKIP] %s\n' "$*"; }

# Imprime o bloco de comentários do topo deste arquivo (a documentação acima).
uso() {
    awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
}

# Aviso de locale não-UTF-8 (para os logs não saírem em mojibake).
if [[ "${LANG:-}" != *UTF-8* && "${LC_ALL:-}" != *UTF-8* ]]; then
    warn "locale não é UTF-8 (LANG=${LANG:-?}); os logs podem aparecer com mojibake."
    warn "sugestão: LANG=C.UTF-8 ./corrigir-comentarios.sh"
fi

# ==============================================================================
# TABELA 1 — Remoções (linha exata, comparada após trim)
# ==============================================================================
# Formato: arquivo:::texto_exato_da_linha
# Tudo que vier depois do PRIMEIRO `:::` é o texto-alvo (portanto `:` e `::`
# dentro do texto não quebram o parser).
regras_remover() {
    cat <<'EOF'
# --------------------------------------------------------------------------
# JwtUtil.java — comentários que só parafraseiam o nome do método
# --------------------------------------------------------------------------
src/main/java/com/pfc/thindesk/security/JwtUtil.java:::// Subject = username
src/main/java/com/pfc/thindesk/security/JwtUtil.java:::// Claim preAuth
src/main/java/com/pfc/thindesk/security/JwtUtil.java:::// Retorna false em vez de lançar exceção
src/main/java/com/pfc/thindesk/security/JwtUtil.java:::// Valida assinatura e expiração; não lança exceção.

# --------------------------------------------------------------------------
# EmailTokenService.java
# --------------------------------------------------------------------------
src/main/java/com/pfc/thindesk/service/EmailTokenService.java:::// Verifica se existe token válido para o usuário e tipo.
src/main/java/com/pfc/thindesk/service/EmailTokenService.java:::// Invalida todos os tokens de um usuário para um tipo.
src/main/java/com/pfc/thindesk/service/EmailTokenService.java:::// Validade em minutos

# --------------------------------------------------------------------------
# PuzzleService.java
# --------------------------------------------------------------------------
src/main/java/com/pfc/thindesk/service/PuzzleService.java:::// Puzzle ativo, se houver
src/main/java/com/pfc/thindesk/service/PuzzleService.java:::// Verifica prazo
src/main/java/com/pfc/thindesk/service/PuzzleService.java:::// Encerra o ciclo do puzzle
src/main/java/com/pfc/thindesk/service/PuzzleService.java:::// Sorteia e ativa um puzzle no usuário

# --------------------------------------------------------------------------
# AuthController.java — duplicatas em relação ao Javadoc da classe
# --------------------------------------------------------------------------
src/main/java/com/pfc/thindesk/controller/AuthController.java:::// Etapa 1: valida usuário/senha e emite token pré-auth + primeiro puzzle.
src/main/java/com/pfc/thindesk/controller/AuthController.java:::// Etapa 2: valida o lance do puzzle; se correto, envia token por e-mail.
src/main/java/com/pfc/thindesk/controller/AuthController.java:::// Gera um novo puzzle quando o tempo esgota ou o usuário erra repetidamente.
src/main/java/com/pfc/thindesk/controller/AuthController.java:::// Logout: apaga os cookies de autenticação.

# --------------------------------------------------------------------------
# TecnicoController.java — TODO disfarçado (vira TODO explícito em regras_adicionar)
# --------------------------------------------------------------------------
src/main/java/com/pfc/thindesk/controller/TecnicoController.java:::// Os demais campos permanecem os do banco (Service só sobrescreve os não-nulos?)
src/main/java/com/pfc/thindesk/controller/TecnicoController.java:::// Ajuste no Service se quiser patch parcial — hoje ele sobrescreve tudo.

# --------------------------------------------------------------------------
# script.js
# --------------------------------------------------------------------------
src/main/resources/static/js/script.js:::// Alternar submenu

# --------------------------------------------------------------------------
# application.properties — propriedade legada sem código leitor
# --------------------------------------------------------------------------
src/main/resources/application.properties:::# (legado, não usado pelo fluxo atual)
src/main/resources/application.properties:::app.security.puzzle.require-email-confirmation=${PUZZLE_REQUIRE_EMAIL:false}
EOF
}

# ==============================================================================
# TABELA 2 — Inserções (comentário acima da linha-âncora)
# ==============================================================================
# Formato: arquivo:::regex_ancora:::texto_do_comentario
# A regex é ERE. Escapar metacaracteres com \ quando literal.
# Insere ACIMA de anotações (@Foo) que precedem a linha-âncora.
regras_adicionar() {
    cat <<'EOF'
# --------------------------------------------------------------------------
# TecnicoController.java — TODO explícito sobre patch parcial
# --------------------------------------------------------------------------
src/main/java/com/pfc/thindesk/controller/TecnicoController.java:::private Chamado montarAtualizacao:::TODO: ChamadoService.atualizarChamado hoje sobrescreve todos os campos; avaliar patch parcial.
EOF
}

# ==============================================================================
# TABELA 3 — Cabeçalhos de arquivo (só aplica se ainda não existir)
# ==============================================================================
# Formato: arquivo:::NomeDaClasse:::Descrição curta
# O cabeçalho é inserido no topo do arquivo, antes de `package ...`.
# Aplicado apenas se as 3 primeiras linhas do arquivo não começarem com `// ===`.
regras_header() {
    cat <<'EOF'
src/main/java/com/pfc/thindesk/entity/Role.java:::Role.java:::Perfis de acesso do sistema: ROLE_ADMIN, ROLE_TECNICO, ROLE_USUARIO.
src/main/java/com/pfc/thindesk/entity/Chamado.java:::Chamado.java:::Chamado de suporte técnico (descrição, status, tipo, técnico, usuário).
src/main/java/com/pfc/thindesk/entity/Cliente.java:::Cliente.java:::Cliente atendido pelo sistema (nome, telefone, setor).
src/main/java/com/pfc/thindesk/entity/HorarioAtendimento.java:::HorarioAtendimento.java:::Horário de atendimento por setor (dia da semana + faixa início/fim).
EOF
}

# ==============================================================================
# NÚCLEO
# ==============================================================================

# Normaliza uma linha para comparação: tira espaços/tabs das pontas.
trim() {
    local s=$1
    s=${s#"${s%%[![:space:]]*}"}
    s=${s%"${s##*[![:space:]]}"}
    printf '%s' "$s"
}

# ---------- Remover linhas exatas (trim-compare) ------------------------------
# Retorna 0 se o arquivo foi modificado, 1 se nada mudou (ou arquivo ausente).
remover_uma_linha() {
    local arquivo=$1 alvo=$2
    local tmp status=1

    [[ -f $arquivo ]] || return 1
    [[ -n $alvo ]]    || return 1

    tmp=$(mktemp "${arquivo}.XXXXXX")
    CM_ALVO="$alvo" awk '
      function trim(s) { sub(/^[ \t]+/, "", s); sub(/[ \t]+$/, "", s); return s }
      BEGIN { alvo = ENVIRON["CM_ALVO"] }
      { if (trim($0) != alvo) print }
    ' "$arquivo" > "$tmp"

    if ! cmp -s "$arquivo" "$tmp"; then
        mv -f -- "$tmp" "$arquivo"
        status=0
    fi
    rm -f -- "$tmp" 2>/dev/null || true
    return $status
}

# ---------- Inserir comentário acima da âncora --------------------------------
# Mesma lógica do comentar.sh: idempotente contra Javadoc equivalente,
# preserva indentação, insere acima de anotações @Foo.
inserir_uma_regra() {
    local arquivo=$1 regex=$2 texto=$3 pre=$4 suf=$5
    local tmp status=1

    [[ -f $arquivo ]] || return 1
    [[ -n $regex ]]   || return 1

    tmp=$(mktemp "${arquivo}.XXXXXX")

    CM_REGEX="$regex" CM_TEXTO="$texto" CM_PRE="$pre" CM_SUF="$suf" \
    awk '
      function norm(s,   t) {
        t = s
        sub(/^[ \t]+/, "", t); sub(/[ \t]+$/, "", t)
        sub(/^\/\//, "", t)
        sub(/^\/\*+/, "", t); sub(/\*\/$/, "", t)
        sub(/^<!--/, "", t); sub(/-->$/, "", t)
        sub(/^#/, "", t)
        sub(/^\*+/, "", t)
        sub(/^[ \t]+/, "", t); sub(/[ \t]+$/, "", t)
        gsub(/[ \t]+/, " ", t)
        return t
      }
      function trim(s) { sub(/^[ \t]+/, "", s); sub(/[ \t]+$/, "", s); return s }
      function isAnnotation(s) { return (trim(s) ~ /^@/) }
      function isComment(s,    t) {
        t = trim(s)
        return (t ~ /^\/\// || t ~ /^\/\*/ || t ~ /^\*/ || t ~ /^<!--/ || t ~ /^#/)
      }

      BEGIN {
        re       = ENVIRON["CM_REGEX"]
        pre      = ENVIRON["CM_PRE"]
        suf      = ENVIRON["CM_SUF"]
        cmt      = pre ENVIRON["CM_TEXTO"] suf
        cmt_norm = norm(cmt)
        win      = 10
      }

      { lines[NR] = $0 }

      END {
        n = NR
        for (i = 1; i <= n; i++) {
          if (lines[i] ~ re) {
            ins = i
            while (ins > 1 && isAnnotation(lines[ins-1])) ins--
            match(lines[i], /^[ \t]*/)
            ind = substr(lines[i], RSTART, RLENGTH)

            existe = 0
            for (j = ins-1; j >= 1 && j >= ins - win; j--) {
              t = trim(lines[j])
              if (t == "") continue
              if (norm(lines[j]) == cmt_norm) { existe = 1; break }
              if (!isComment(lines[j])) break
            }

            if (!existe && !(ins in pre_insert))
              pre_insert[ins] = ind cmt
          }
        }

        for (i = 1; i <= n; i++) {
          if (i in pre_insert) print pre_insert[i]
          print lines[i]
        }
      }
    ' "$arquivo" > "$tmp"

    if ! cmp -s "$arquivo" "$tmp"; then
        mv -f -- "$tmp" "$arquivo"
        status=0
    fi
    rm -f -- "$tmp" 2>/dev/null || true
    return $status
}

# ---------- Adicionar cabeçalho no topo ---------------------------------------
# Só aplica se as primeiras linhas não começarem com `// ===`.
adicionar_header() {
    local arquivo=$1 nome=$2 desc=$3
    local status=1 tmp

    [[ -f $arquivo ]] || return 1

    if head -n 3 "$arquivo" | grep -q '^// ===='; then
        return 1
    fi

    tmp=$(mktemp "${arquivo}.XXXXXX")
    {
        printf '// ============================================================\n'
        printf '// %s\n' "$nome"
        printf '// Autor: Miguel Santana\n'
        printf '// Descrição: %s\n' "$desc"
        printf '// ============================================================\n'
        cat "$arquivo"
    } > "$tmp"

    if ! cmp -s "$arquivo" "$tmp"; then
        mv -f -- "$tmp" "$arquivo"
        status=0
    fi
    rm -f -- "$tmp" 2>/dev/null || true
    return $status
}

# ---------- Backup / Reverter -------------------------------------------------

fazer_backup() {
    local raiz_bkp=$1 dir arq dest n=0
    dir="${raiz_bkp%/}/$(date +%Y%m%d-%H%M%S)"
    mkdir -p -- "$dir"
    for arq in "${ARQUIVOS_ALVO[@]}"; do
        [[ -f $arq ]] || continue
        dest="$dir/$arq"
        mkdir -p -- "$(dirname -- "$dest")"
        cp -p -- "$arq" "$dest"
        n=$((n + 1))
    done
    printf '%s\n' "$dir"
}

reverter() {
    [[ -d $BACKUP_ROOT ]] || die "nenhum diretório de backup em: $BACKUP_ROOT"

    local ultimo=""
    if compgen -G "$BACKUP_ROOT"/*/ >/dev/null; then
        ultimo=$(ls -1d -- "$BACKUP_ROOT"/*/ 2>/dev/null | LC_ALL=C sort | tail -n 1)
    fi
    [[ -n $ultimo ]] || die "nenhum backup encontrado em: $BACKUP_ROOT"
    ultimo=${ultimo%/}

    log "Revertendo de: $ultimo"
    local n=0 f rel dest
    while IFS= read -r -d '' f; do
        rel=${f#"$ultimo"/}
        dest="$rel"
        mkdir -p -- "$(dirname -- "$dest")"
        cp -p -- "$f" "$dest"
        n=$((n + 1))
    done < <(find "$ultimo" -type f -print0)
    log "Restaurados $n arquivos."
}

# ---------- Autoteste ---------------------------------------------------------

autoteste() {
    local falhas=0 tmpd
    tmpd=$(mktemp -d)

    local ok fail
    ok()   { printf '  ok     %s\n' "$1"; }
    fail() { printf '  FALHOU %s\n' "$1"; falhas=$((falhas + 1)); }

    # 1) Remoção simples
    cat >"$tmpd/A.java" <<'EOF'
class A {
    // Subject = username
    private String s;
}
EOF
    if remover_uma_linha "$tmpd/A.java" '// Subject = username'; then
        grep -q 'Subject = username' "$tmpd/A.java" \
            && fail 'remoção simples' \
            || ok 'remoção simples'
    else
        fail 'remoção simples (nada removido)'
    fi

    # 2) Idempotência da remoção
    if remover_uma_linha "$tmpd/A.java" '// Subject = username'; then
        fail 'idempotência remoção (removeu de novo)'
    else
        ok 'idempotência remoção'
    fi

    # 3) Remoção com trim (indentação variável)
    cat >"$tmpd/B.java" <<'EOF'
        // Validade em minutos
class B {}
EOF
    if remover_uma_linha "$tmpd/B.java" '// Validade em minutos'; then
        grep -q 'Validade em minutos' "$tmpd/B.java" \
            && fail 'remoção com indentação variável' \
            || ok 'remoção com indentação variável'
    else
        fail 'remoção com indentação variável (nada removido)'
    fi

    # 4) Comentário com `:` e `${...}` (properties)
    cat >"$tmpd/C.properties" <<'EOF'
# (legado, não usado pelo fluxo atual)
app.security.puzzle.require-email-confirmation=${PUZZLE_REQUIRE_EMAIL:false}
EOF
    remover_uma_linha "$tmpd/C.properties" '# (legado, não usado pelo fluxo atual'  >/dev/null || true
    remover_uma_linha "$tmpd/C.properties" 'app.security.puzzle.require-email-confirmation=${PUZZLE_REQUIRE_EMAIL:false}' >/dev/null || true
    # O trim-compare só funciona se o texto-alvo estiver exato. Vamos checar:
    if grep -q 'require-email-confirmation' "$tmpd/C.properties"; then
        fail 'remoção de linha com ${...} literal'
    else
        ok 'remoção de linha com ${...} literal'
    fi

    # 5) Cabeçalho em arquivo sem cabeçalho
    cat >"$tmpd/D.java" <<'EOF'
package x;
class D {}
EOF
    if adicionar_header "$tmpd/D.java" 'D.java' 'Classe D.'; then
        head -n 1 "$tmpd/D.java" | grep -q '^// ====' \
            && ok 'adiciona cabeçalho' \
            || fail 'adiciona cabeçalho'
    else
        fail 'adiciona cabeçalho (nada feito)'
    fi

    # 6) Cabeçalho é idempotente
    if adicionar_header "$tmpd/D.java" 'D.java' 'Classe D.'; then
        fail 'cabeçalho idempotente (duplicou)'
    else
        local n
        n=$(grep -c '^// ====' "$tmpd/D.java" || true)
        [[ $n -eq 2 ]] \
            && ok 'cabeçalho idempotente' \
            || fail "cabeçalho idempotente (n=$n)"
    fi

    # 7) Inserção de comentário acima de anotações
    cat >"$tmpd/E.java" <<'EOF'
@RestController
@RequestMapping("/api/x")
public class E {}
EOF
    if inserir_uma_regra "$tmpd/E.java" 'public class E' 'Classe E.' '// ' ''; then
        head -n 1 "$tmpd/E.java" | grep -q '^// Classe E\.$' \
            && ok 'insere acima de anotações' \
            || fail 'insere acima de anotações'
    else
        fail 'insere acima de anotações (nada inserido)'
    fi

    # 8) Inserção não duplica contra Javadoc equivalente
    cat >"$tmpd/F.java" <<'EOF'
/**
 * Classe F.
 */
@Deprecated
public class F {}
EOF
    if inserir_uma_regra "$tmpd/F.java" 'public class F' 'Classe F.' '// ' ''; then
        fail 'não duplica Javadoc equivalente'
    else
        local n
        n=$(grep -c 'Classe F' "$tmpd/F.java" || true)
        [[ $n -eq 1 ]] \
            && ok 'não duplica Javadoc equivalente' \
            || fail "não duplica Javadoc equivalente (n=$n)"
    fi

    rm -rf -- "$tmpd"

    if (( falhas > 0 )); then
        printf 'Autoteste: %d falha(s)\n' "$falhas"
        return 1
    fi
    printf 'Autoteste: tudo certo\n'
    return 0
}

# ---------- Argumentos --------------------------------------------------------

while getopts ':nRTb:Bqh' opcao; do
    case $opcao in
        n) DRY_RUN=1 ;;
        R) REVERTER=1 ;;
        T) AUTOTESTE=1 ;;
        b) BACKUP_ROOT=$OPTARG ;;
        B) SKIP_BACKUP=1 ;;
        q) QUIET=1 ;;
        h) uso; exit 0 ;;
        :) die "a opção -$OPTARG requer um valor (use -h)" ;;
        *) die "opção desconhecida: -$OPTARG (use -h)" ;;
    esac
done

if (( AUTOTESTE )); then autoteste; exit $?; fi
if (( REVERTER )); then reverter; exit 0; fi

# ---------- Coleta de arquivos-alvo e regras ----------------------------------

declare -a ARQUIVOS_ALVO=()
declare -A _vistos=()

declare -a REGRAS_REM=()    # arquivo:::texto
declare -a REGRAS_ADD=()    # arquivo:::regex:::texto
declare -a REGRAS_HDR=()    # arquivo:::nome:::descricao

ler_tabela() {
    local tabela=$1 destino=$2 linha
    while IFS= read -r linha; do
        [[ -z $linha ]] && continue
        [[ $linha == \#* ]] && continue
        # marca arquivo como alvo
        local arq=${linha%%:::*}
        if [[ -z ${_vistos[$arq]:-} ]]; then
            _vistos[$arq]=1
            ARQUIVOS_ALVO+=("$arq")
        fi
        # shellcheck disable=SC2178
        eval "$destino+=(\"\$linha\")"
    done < <("$tabela")
}

ler_tabela regras_remover REGRAS_REM
ler_tabela regras_adicionar REGRAS_ADD
ler_tabela regras_header REGRAS_HDR

(( ${#ARQUIVOS_ALVO[@]} > 0 )) || die "nenhuma regra definida"

# ---------- Backup ------------------------------------------------------------

if (( ! DRY_RUN && ! SKIP_BACKUP )); then
    bkp_dir=$(fazer_backup "$BACKUP_ROOT")
    log "Backup em: $bkp_dir"
fi

# ---------- Aplicação --------------------------------------------------------

total_removidos=0
total_inseridos=0
total_headers=0
n_arquivos_mod=0

aplicar_em_arquivo() {
    local arq=$1
    local alvo=$arq
    local tmp_sim="" modificou=0

    [[ -f $arq ]] || { skip "$arq (não existe)"; return 0; }

    # Em dry-run, trabalha numa cópia temporária
    if (( DRY_RUN )); then
        tmp_sim=$(mktemp)
        cp -p -- "$arq" "$tmp_sim"
        alvo=$tmp_sim
    fi

    # --- Cabeçalhos ------------------------------------------------------
    local r nome desc
    for r in "${REGRAS_HDR[@]}"; do
        [[ ${r%%:::*} == "$arq" ]] || continue
        local resto=${r#*:::}
        nome=${resto%%:::*}
        desc=${resto#*:::}
        if adicionar_header "$alvo" "$nome" "$desc"; then
            modificou=1
            total_headers=$((total_headers + 1))
        fi
    done

    # --- Remoções --------------------------------------------------------
    local texto
    for r in "${REGRAS_REM[@]}"; do
        [[ ${r%%:::*} == "$arq" ]] || continue
        texto=${r#*:::}
        if remover_uma_linha "$alvo" "$texto"; then
            modificou=1
            total_removidos=$((total_removidos + 1))
        fi
    done

    # --- Inserções -------------------------------------------------------
    local regex
    local pre suf
    case $arq in
        *.html|*.xml) pre='<!-- '; suf=' -->' ;;
        *.properties) pre='# ';    suf=''     ;;
        *)            pre='// ';   suf=''     ;;
    esac
    for r in "${REGRAS_ADD[@]}"; do
        [[ ${r%%:::*} == "$arq" ]] || continue
        local resto=${r#*:::}
        regex=${resto%%:::*}
        texto=${resto#*:::}
        if inserir_uma_regra "$alvo" "$regex" "$texto" "$pre" "$suf"; then
            modificou=1
            total_inseridos=$((total_inseridos + 1))
        fi
    done

    # --- Relatório -------------------------------------------------------
    if (( DRY_RUN )); then
        if (( modificou )); then
            log "[DRY] $arq (seria modificado)"
        else
            log "[DRY] $arq (0 — já em dia)"
        fi
        rm -f -- "$tmp_sim"
    else
        if (( modificou )); then
            log "[OK]  $arq (modificado)"
            n_arquivos_mod=$((n_arquivos_mod + 1))
        else
            log "[OK]  $arq (0 — já em dia)"
        fi
    fi
}

for arq in "${ARQUIVOS_ALVO[@]}"; do
    aplicar_em_arquivo "$arq"
done

# ---------- Resumo -----------------------------------------------------------

if (( DRY_RUN )); then
    log ""
    log "Dry-run: nada foi escrito."
    log "Resumo: $total_removidos remoção(ões), $total_inseridos inserção(ões), $total_headers cabeçalho(s)."
else
    log ""
    log "Total:"
    log "  • remoções:  $total_removidos"
    log "  • inserções: $total_inseridos"
    log "  • cabeçalhos: $total_headers"
    log "  • arquivos modificados: $n_arquivos_mod"
fi
