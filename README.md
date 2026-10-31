# Claude archive

Everything I built with Claude up to Nov 1, 2026, kept in one private repo so it doesn't depend on a subscription.

## What's here

| Folder | Contents |
| --- | --- |
| [`artifacts/`](artifacts) | The six apps and guides Claude built, each a single `index.html` that runs in any browser, offline |
| [`docs/`](docs) | The three Claude Docs, exported to PDF |
| [`chats/INDEX.md`](chats/INDEX.md) | Every chat from Aug 1 to Oct 31 with links, plus which chats hold files to download |
| [`drive-tools/`](drive-tools) | The Google Drive cleanup pipeline and the private local code assistant (run the helper script once on the laptop to add the files) |
| [`context/about-me.md`](context/about-me.md) | A background brief to paste into any AI assistant |

## Apps (`artifacts/`)

Open any `index.html` in a browser. Progress is saved in that browser's local storage.

| App | Folder | Also live at |
| --- | --- | --- |
| Kitchen From Zero | `kitchen-from-zero/` | [manojhunasimarad.github.io/Kitchen](https://manojhunasimarad.github.io/Kitchen/) (the repo version is newer) |
| LLM From Zero | `llm-from-zero/` | [manojhunasimarad.github.io/llm-from-zero](https://manojhunasimarad.github.io/llm-from-zero/) (v4 there is much fuller) |
| Dalal Dojo: trading desk | `dalal-dojo-v2-kite-desk/` | [manojhunasimarad.github.io/dalal-dojo](https://manojhunasimarad.github.io/dalal-dojo/) |
| Dalal Dojo: lessons and lab | `dalal-dojo-v1-lessons/` | [manojhunasimarad.github.io/dalal-dojo-lessons](https://manojhunasimarad.github.io/dalal-dojo-lessons/) |
| RRSI paper: first pass | `rrsi-first-pass/` | Private |
| Reading papers for the literature review | `reading-papers-lit-review/` | Private |

## Docs (`docs/`)

| Doc | File |
| --- | --- |
| Speaking script: LLM pruning talk, CVMI 2026, paper 709 | `speaking-script-llm-pruning-cvmi-2026.pdf` |
| LaTeX with Overleaf + AI: prerequisites | `latex-overleaf-ai-prerequisites.pdf` |
| LaTeX with Overleaf + AI: after the session | `latex-overleaf-ai-after-the-session.pdf` |

The editable originals stay in Claude under Artifacts, and each can be exported again to Word or Markdown from there.

## Other repos with Claude-built work

- [Kitchen](https://github.com/manojhunasimarad/Kitchen): Kitchen From Zero, the full app
- [manojhunasimarad.github.io](https://github.com/manojhunasimarad/manojhunasimarad.github.io): LLM From Zero v4, Dalal Dojo, home page
- [auto-rag-assistant](https://github.com/manojhunasimarad/auto-rag-assistant): RAG + agent project for the Citi interview
- [automotive-fl](https://github.com/manojhunasimarad/automotive-fl): federated learning testbed (NITK Minor Project)

## Still to do by hand

- [ ] Claude data export: claude.ai on a laptop → Settings → Privacy → Export data. Download the zip from the email within 24 hours.
- [ ] Download the chat-only files listed in [`chats/INDEX.md`](chats/INDEX.md), especially the resumes and the Dalal Dojo Android project.
- [ ] Run `drive-tools/add-drive-tools.ps1` on the laptop.
