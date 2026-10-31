# Private AI for learning a codebase: laptop + V100

Goal: use AI to learn large C++ codebases such as the AUTOSAR Adaptive (APD) sources, with no cloud AI service involved. Two models, one editor:

| | Laptop model | V100 model |
| --- | --- | --- |
| Runs on | Laptop GPU (Docker, `127.0.0.1:11434`) | V100 (your home folder, your own port, `127.0.0.1` only) |
| Model | Picked by laptop VRAM (Qwen2.5-Coder 3B to 14B) | Qwen3-Coder 30B on a 32 GB V100, Qwen2.5-Coder 14B on 16 GB |
| Use it for | Autocomplete, codebase indexing, anything licensed | Long explanations and bigger questions, faster and smarter |
| Where your code goes | Nowhere: it never leaves the laptop | The question and the snippets it needs travel through your encrypted SSH tunnel into the V100's GPU memory. Nothing is written to disk there; prompts are not logged |

The editor is VS Code with the **Continue** extension. The code and Continue's search index always stay on the laptop.

**Licensed code.** In V100 mode, snippets of whatever you ask about reach the college server's memory. Admins of that server can see everything on it, and other users logged into it could send their own questions to your model (they cannot read yours). If your APD licence means "this PC only", use the laptop model for APD and the V100 for everything else. That decision is yours.

## Setup

### 1. Laptop model (built in October, not yet run)

Files are on the laptop in `C:\Users\manoj\Documents\buffer\code-assistant\` (copy them into `drive-tools/code-assistant/` with `drive-tools/add-drive-tools.ps1`). Start Docker Desktop, then:

```powershell
powershell -ExecutionPolicy Bypass -File C:\Users\manoj\Documents\buffer\code-assistant\setup.ps1
```

It checks the GPU, starts Ollama, downloads a coder model and an embedding model, and writes `~\.continue\config.yaml`. Then in VS Code, search the settings for "continue telemetry" and turn it off.

### 2. V100 model (once, about 20 minutes, mostly the download)

Connect Sophos VPN, check that `ssh v100` works, then from this folder on the laptop:

```powershell
cd <path-to-Claude-archieve>\local-ai\windows
powershell -ExecutionPolicy Bypass -File v100.ps1 setup      # installs Ollama on the V100, no sudo needed
powershell -ExecutionPolicy Bypass -File v100.ps1 continue   # adds "V100 coder" to Continue
```

`setup` prints a short test answer, its speed in tokens/s, and the `PROCESSOR` column, which must say `100% GPU`.

## Each session

1. Sophos VPN on.
2. `powershell -ExecutionPolicy Bypass -File v100.ps1 connect`, then leave the window open.
3. VS Code → Continue panel → choose **V100 coder** (or the laptop model for licensed code).
4. Done for the day: `v100.ps1 stop`. The model also unloads by itself after 15 idle minutes, so the GPU is free for others.

## How to learn a codebase with it

1. **Make navigation work first.** Install the `clangd` extension and generate `compile_commands.json` (`cmake -S . -B build -DCMAKE_EXPORT_COMPILE_COMMANDS=ON`). Go-to-definition and find-references beat any AI for "where is this used".
2. **Let Continue index the folder.** The status bar shows progress; the index stays on the laptop.
3. **Map before you read.** Ask `@codebase` for the top-level components and how they talk to each other, then ask for a reading order. Write the answers in your own words in a `notes/` folder next to the code (keep it out of public repos).
4. **Trace one real path end to end.** For example: how does the Execution Manager start a process, from the manifest to `fork/exec`? Ask the model to name each function and file, then open each one with clangd and check it yourself.
5. **Quiz yourself.** Ask the model for 5 questions about a module you just read, answer them without looking, then check against the code.
6. **Change something small.** Add a log line, rebuild, run. Understanding sticks when you have broken and fixed it once.

Prompts that work well with local models:

- `@codebase List the main components under <folder>, one line each, and which ones call which.`
- `@codebase Trace what happens when <function> is called. Name each file and function in order. Say "not sure" where you are guessing.`
- `Explain this file to someone who knows C++ but not AUTOSAR. Keep it under 200 words, then list 3 things to look at next.` (with the file open)
- `What would break if I removed this line?` (with a selection)

Local models make confident mistakes. Treat every answer as a lead to check with clangd, not a fact.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `ssh v100` asks for passwords each time | Set up an SSH key for the jump host and the V100 (the same one VS Code Remote-SSH uses) |
| `PROCESSOR` shows CPU, or the log says "no compatible GPUs" | Run `nvidia-smi` on the V100. The driver must be 531 or newer. V100 needs Ollama's CUDA 12 runner, which the standard download includes |
| Out of memory on the V100 | Someone else is using it. `v100.ps1 status` shows usage. Wait, or rerun setup with a smaller model: `bash ~/ollama-v100/setup.sh qwen2.5-coder:7b` |
| Home folder quota is too small for models | On the V100: `OLLAMA_MODELS=/scratch/$USER/ollama-models bash ~/ollama-v100/setup.sh` |
| The V100 has no internet | On the laptop, download `ollama-linux-amd64.tar.zst` from ollama.com, `scp` it to the V100, and unpack it into `~/.local/ollama` with `tar --use-compress-program=unzstd -xf ...`. Then run `ollama pull` on the laptop and copy `~/.ollama/models` across |
| Continue says it cannot reach "V100 coder" | The tunnel is closed. Run `v100.ps1 connect` again, or switch to the laptop model |

## Files

| File | Runs on | Does |
| --- | --- | --- |
| `windows/v100.ps1` | Laptop | `setup`, `continue`, `connect`, `status`, `stop` |
| `v100/setup.sh` | V100 | Checks GPU, driver and disk; installs Ollama in `~/.local/ollama`; picks the model; downloads it; tests it |
| `v100/ollama-ctl.sh` | V100 | `start`, `stop`, `restart`, `status`, `logs`, `pull`, `test` |
