#!/usr/bin/env bash
# Copies each web app into its Android flavor (app/src/<flavor>/assets/www) before a build.
# Usage: android/sync-web.sh <path to manojhunasimarad.github.io> <path to Kitchen repo>
set -euo pipefail
SITE="$(cd "${1:-..}" && pwd)"
KITCHEN="$(cd "${2:-../../Kitchen}" && pwd)"
SRC="$(cd "$(dirname "$0")" && pwd)/app/src"

copy() {
  local flavor="$1" from="$2"; shift 2
  local to="$SRC/$flavor/assets/www"
  rm -rf "$to"
  mkdir -p "$to"
  tar -C "$from" --exclude='.git' --exclude='.github' --exclude='.nojekyll' --exclude='./README.md' "$@" -cf - . | tar -C "$to" -xf -
  test -f "$to/index.html" || { echo "No index.html for $flavor in $from" >&2; exit 1; }
  echo "$flavor: $(find "$to" -type f | wc -l) files, $(du -sh "$to" | cut -f1)"
}

copy llm         "$SITE/llm-from-zero"
copy kitchen     "$KITCHEN" --exclude='./docs' --exclude='./tools' --exclude='./src'
copy dojo        "$SITE/dalal-dojo"
copy dojolessons "$SITE/dalal-dojo-lessons"
