#!/usr/bin/env bash
# Publica no GitHub as releases de Thindesk a partir das tags e de docs/releases/.
# Requer o GitHub CLI autenticado (gh auth login) e as tags já enviadas (git push origin main --tags).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSOES=("1.0.0-alpha" "1.0.0-beta" "1.0.0-beta.2" "1.0.0-beta.3")   # da mais antiga para a mais nova

for v in "${VERSOES[@]}"; do
  tag="v$v"
  if gh release view "$tag" >/dev/null 2>&1; then
    echo "= $tag já existe, pulando"
    continue
  fi
  gh release create "$tag" --verify-tag --prerelease \
     --title "Thindesk $v" --notes-file "docs/releases/$tag.md"
  echo "+ $tag publicada"
done
