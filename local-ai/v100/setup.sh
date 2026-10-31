#!/usr/bin/env bash
# One-time setup of a private Ollama model server on the V100 machine, in your home folder (no sudo).
#   bash ~/ollama-v100/setup.sh            # picks a coding model that fits the GPU
#   bash ~/ollama-v100/setup.sh qwen2.5-coder:14b
# The server listens on 127.0.0.1 only. Your laptop reaches it through your own SSH tunnel
# (windows\v100.ps1 connect). Nothing is installed system-wide and nothing runs as root.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PREFIX="${OLLAMA_PREFIX:-$HOME/.local/ollama}"
MODELS_DIR="${OLLAMA_MODELS:-$HOME/ollama-models}"
ENV_FILE="$HOME/.config/ollama-v100.env"
CHOSEN_MODEL="${1:-}"

say() { printf '\n== %s\n' "$*"; }

say "1. GPU check"
command -v nvidia-smi >/dev/null || { echo "nvidia-smi not found: this machine has no NVIDIA driver in PATH."; exit 1; }
nvidia-smi --query-gpu=index,name,memory.total,memory.used,driver_version --format=csv
DRIVER_MAJOR=$(nvidia-smi --query-gpu=driver_version --format=csv,noheader | head -1 | cut -d. -f1)
if [ "${DRIVER_MAJOR:-0}" -lt 531 ]; then
  echo "WARNING: driver $DRIVER_MAJOR is older than Ollama's minimum (531). Ask the admin for a newer driver."
fi
# Use the GPU with the most free memory (on a shared server someone may be busy on GPU 0).
read -r GPU FREE_MIB TOTAL_MIB < <(nvidia-smi --query-gpu=index,memory.free,memory.total --format=csv,noheader,nounits \
  | tr -d ' ' | tr ',' ' ' | sort -k2 -n -r | head -1)
echo "Using GPU $GPU: ${FREE_MIB} MiB free of ${TOTAL_MIB} MiB"

if [ -z "$CHOSEN_MODEL" ]; then
  if [ "$TOTAL_MIB" -ge 30000 ]; then CHOSEN_MODEL="qwen3-coder:30b";   CTX=32768   # V100 32 GB, about 19 GB
  elif [ "$TOTAL_MIB" -ge 15000 ]; then CHOSEN_MODEL="qwen2.5-coder:14b"; CTX=16384 # V100 16 GB, about 9 GB
  else CHOSEN_MODEL="qwen2.5-coder:7b"; CTX=16384
  fi
else
  CTX=$([ "$TOTAL_MIB" -ge 30000 ] && echo 32768 || echo 16384)
fi
echo "Model: $CHOSEN_MODEL, context $CTX tokens"

say "2. Disk check for models ($MODELS_DIR)"
mkdir -p "$MODELS_DIR"
df -h "$MODELS_DIR" | tail -1
AVAIL_GB=$(df -BG --output=avail "$MODELS_DIR" | tail -1 | tr -dc '0-9')
if [ "${AVAIL_GB:-0}" -lt 25 ]; then
  echo "WARNING: under 25 GB free here. If your home folder has a quota, rerun with a scratch disk, e.g."
  echo "  OLLAMA_MODELS=/scratch/$USER/ollama-models bash $0"
fi

say "3. Install Ollama into $PREFIX"
mkdir -p "$PREFIX"
if [ ! -x "$PREFIX/bin/ollama" ] || [ "${UPDATE:-0}" = 1 ]; then
  TMP=$(mktemp -d)
  if curl -fL --retry 3 -o "$TMP/ollama.tar.zst" https://ollama.com/download/ollama-linux-amd64.tar.zst; then
    if command -v zstd >/dev/null; then
      tar --use-compress-program=unzstd -xf "$TMP/ollama.tar.zst" -C "$PREFIX"
    else
      echo "zstd is not installed; unpacking with Python instead"
      python3 -m pip install --user --quiet zstandard
      python3 - "$TMP/ollama.tar.zst" "$PREFIX" <<'PY'
import sys, tarfile, zstandard
with open(sys.argv[1], "rb") as fh, zstandard.ZstdDecompressor().stream_reader(fh) as stream:
    with tarfile.open(fileobj=stream, mode="r|") as tar:
        try:
            tar.extractall(sys.argv[2], filter="fully_trusted")
        except TypeError:
            tar.extractall(sys.argv[2])
PY
    fi
  elif curl -fL --retry 3 -o "$TMP/ollama.tgz" https://ollama.com/download/ollama-linux-amd64.tgz; then
    tar -xzf "$TMP/ollama.tgz" -C "$PREFIX"
  else
    echo "Could not download Ollama. If this server has no internet, see 'Offline install' in local-ai/README.md."
    exit 1
  fi
  rm -rf "$TMP"
fi
"$PREFIX/bin/ollama" --version 2>/dev/null || true

say "4. Save settings to $ENV_FILE"
# A port of your own (from your user id), so you don't collide with anyone else's Ollama on 11434.
PORT=$((20000 + $(id -u) % 10000))
mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
PREFIX=$PREFIX
OLLAMA_HOST=127.0.0.1:$PORT
OLLAMA_MODELS=$MODELS_DIR
OLLAMA_KEEP_ALIVE=15m
OLLAMA_NUM_PARALLEL=1
OLLAMA_CONTEXT_LENGTH=$CTX
CUDA_VISIBLE_DEVICES=$GPU
MODEL=$CHOSEN_MODEL
EOF
chmod 600 "$ENV_FILE"
cat "$ENV_FILE"

say "5. Start the server"
bash "$HERE/ollama-ctl.sh" restart

say "6. Download $CHOSEN_MODEL (once; several GB)"
bash "$HERE/ollama-ctl.sh" pull "$CHOSEN_MODEL"

say "7. Test that it runs on the GPU"
bash "$HERE/ollama-ctl.sh" test

echo
echo "Done. On your Windows laptop run:  powershell -ExecutionPolicy Bypass -File v100.ps1 connect"
