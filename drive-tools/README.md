# Drive tools

Scripts from the October 2026 Google Drive cleanup: a 173 GB Takeout export turned into a sorted, verified `D:\Library` (393,805 files moved, each one checked byte-for-byte, with an undo log). Plus the private local code assistant (Ollama + Continue) set up in the same chat.

The final versions live on my laptop in `C:\Users\manoj\Documents\buffer\`. To add them here, run this once on the laptop:

```powershell
powershell -ExecutionPolicy Bypass -File <path-to-Claude-archieve>\drive-tools\add-drive-tools.ps1
```

It copies only `*.py`, `*.ps1` and the `code-assistant\` config, then commits and pushes. The catalog database, plans and logs stay on the laptop because they list every personal file path.

## The pipeline

Run in this order. Steps 1 and 2 only read and write reports; nothing moves until step 3.

| Step | Script | What it does |
| --- | --- | --- |
| 0 | `unpack_nested.py`, `unpack_7z.py` | Unpack archives found inside the backup; delete each archive only after its contents check out |
| 1 | `catalog.py` | Read-only scan into `catalog.db`: SHA-256 of every file, EXIF/Takeout dates, GPS, camera, screenshot guess, licensed-code flag. Resumable |
| 2 | `organize_plan.py` | Dry run: writes `plan.csv`, `events.csv` (photo events by date and place) and a summary for review |
| 3 | `apply_plan.py` | Moves files per the reviewed plan. Same drive: rename. Across drives: copy, check SHA-256, then remove the source. Logs every move to `moves_log.csv` |
| 4 | `verify_library.py` | Re-hashes every file at its new place and compares with the step-1 fingerprint. Writes `verify_report.csv` |

Rules the scripts follow: nothing is deleted by default (duplicates go to `_Review`), and licensed LTTS/AUTOSAR code is moved whole and never read by any AI step.

Dependencies: `pip install pillow pillow-heif reverse_geocoder zipfile-deflate64`, and 7-Zip (`winget install 7zip.7zip`) for step 0.

## Private code assistant (`code-assistant/`)

Ollama in Docker on the local NVIDIA GPU, listening on `127.0.0.1` only, with the Continue extension in VS Code pointed at it. `setup.ps1` picks the model by GPU memory (Qwen3-Coder 30B at 22 GB or more, down to Qwen2.5-Coder 3B). The same `docker-compose.yml` runs on the V100 server; reach it with `ssh -L 11434:127.0.0.1:11434 user@server`.

Source chat: [Google Drive cleanup download](https://claude.ai/chat/6cb549b3-d96f-4da7-bf8b-b65ee9eae358)
