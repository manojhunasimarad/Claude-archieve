#!/usr/bin/env bash
# Controls your private Ollama on the V100.
#   ollama-ctl.sh start | stop | restart | status | logs | pull [model] | test
set -euo pipefail
ENV_FILE="$HOME/.config/ollama-v100.env"
[ -f "$ENV_FILE" ] || { echo "No settings yet. Run setup.sh first."; exit 1; }
set -a; . "$ENV_FILE"; set +a
export PATH="$PREFIX/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib/ollama${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
STATE="$HOME/.local/state"; mkdir -p "$STATE"
LOG="$STATE/ollama-v100.log"
PIDF="$STATE/ollama-v100.pid"
URL="http://$OLLAMA_HOST"

running() { [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; }
up() { curl -fs --noproxy '*' "$URL/api/version" >/dev/null 2>&1; }

start() {
  if running && up; then echo "Already running on $OLLAMA_HOST (pid $(cat "$PIDF"))"; return; fi
  nohup ollama serve >>"$LOG" 2>&1 &
  echo $! > "$PIDF"
  for _ in $(seq 1 40); do up && break; sleep 0.5; done
  if up; then
    echo "Running on $OLLAMA_HOST (pid $(cat "$PIDF")), GPU $CUDA_VISIBLE_DEVICES, model $MODEL"
  else
    echo "Ollama did not start. Last log lines:"; tail -n 30 "$LOG"; exit 1
  fi
}

stop() {
  if running; then kill "$(cat "$PIDF")" && rm -f "$PIDF" && echo "Stopped"; else echo "Not running"; fi
}

case "${1:-status}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop || true; sleep 1; start ;;
  status)
    if running && up; then echo "Running on $OLLAMA_HOST (pid $(cat "$PIDF"))"; ollama ps; else echo "Not running"; fi ;;
  logs)    tail -n "${2:-60}" "$LOG" ;;
  pull)    up || start; ollama pull "${2:-$MODEL}" ;;
  test)
    up || start
    echo "Asking $MODEL a small coding question..."
    curl -s --noproxy '*' "$URL/api/generate" -d "{\"model\":\"$MODEL\",\"stream\":false,\"options\":{\"num_predict\":160},\"prompt\":\"In C++, write a function that reverses a std::string in place. Code only.\"}" \
      | python3 -c 'import sys, json
r = json.load(sys.stdin)
if "error" in r: sys.exit("Error: " + r["error"])
print(r["response"].strip()[:600])
secs = r["eval_duration"] / 1e9
print("\nSpeed: %.1f tokens/s" % (r["eval_count"] / secs))'
    echo
    ollama ps
    echo "The PROCESSOR column should say 100% GPU. GPU lines from the log:"
    grep -iE "inference compute|library=cuda|offloaded" "$LOG" | tail -n 3 || true
    ;;
  *) echo "usage: $0 start|stop|restart|status|logs|pull [model]|test"; exit 1 ;;
esac
