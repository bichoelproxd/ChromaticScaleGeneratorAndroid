#!/usr/bin/env bash
# Descarga el codigo fuente de Parselmouth (que incluye el codigo fuente de Praat
# y la dependencia fmt) en third_party/Parselmouth, con versiones fijas.
#
# Este codigo fuente es el que se compila para Android con el NDK
# (ver app/src/main/cpp/CMakeLists.txt). No se sube al repositorio (esta en .gitignore).
#
# Uso:  scripts/fetch_praat.sh [carpeta_destino]
set -euo pipefail

PARSELMOUTH_REPO="https://github.com/YannickJadoul/Parselmouth.git"
PARSELMOUTH_COMMIT="b5e97e77e74b6f0a064f1bf2743f3067bacc9ac0"   # Praat 6.4.16
FMT_REPO="https://github.com/fmtlib/fmt.git"
FMT_COMMIT="407c905e45ad75fc29bf0f9bb7c5c2fd3475976f"            # fmt 12.1.0

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${1:-$ROOT/third_party/Parselmouth}"

# Clona un commit concreto sin historial: fetch_commit <url> <sha> <carpeta>
fetch_commit() {
  local url="$1" sha="$2" dir="$3"
  if [ -d "$dir/.git" ] && [ "$(git -C "$dir" rev-parse HEAD 2>/dev/null || true)" = "$sha" ]; then
    echo "[fetch_praat] $dir ya esta en $sha"
    return 0
  fi
  rm -rf "$dir"
  mkdir -p "$dir"
  git -C "$dir" init -q
  git -C "$dir" remote add origin "$url"
  git -C "$dir" fetch -q --depth 1 origin "$sha"
  git -C "$dir" checkout -q FETCH_HEAD
}

echo "[fetch_praat] Parselmouth $PARSELMOUTH_COMMIT -> $DEST"
fetch_commit "$PARSELMOUTH_REPO" "$PARSELMOUTH_COMMIT" "$DEST"

# En Parselmouth, fmt es un submodulo en extern/fmt
echo "[fetch_praat] fmt $FMT_COMMIT -> $DEST/extern/fmt"
fetch_commit "$FMT_REPO" "$FMT_COMMIT" "$DEST/extern/fmt"

test -f "$DEST/praat/CMakeLists.txt"
test -f "$DEST/extern/fmt/CMakeLists.txt"
echo "[fetch_praat] Listo."
