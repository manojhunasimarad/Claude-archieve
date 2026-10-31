# Daily radar

Every morning at 06:47 IST, GitHub builds a short digest of what is new on your topics and opens an issue with it, so the GitHub app on your phone notifies you. Read it, close the app. Nothing is ever shown twice, so it only gets longer when something new actually happened. It runs on GitHub's free Actions and needs no Claude, no API keys and no accounts.

| Section | Source | What "worth it" means |
| --- | --- | --- |
| New papers on your topics | arXiv API | Submitted in the last 4 days, matching `config.json` topics; boosted if researchers upvote it on Hugging Face |
| Trending in AI research | Hugging Face Daily Papers | Most upvoted papers today |
| Rising repos | GitHub search | Created in the last 14 days, sorted by stars |
| Worth reading | Hacker News | Your keywords, or 400+ points |
| Deadlines | `deadlines.json` | Countdown for anything in the next 75 days; bold within 10 days |

**Read it:** the GitHub app notification, the open issue labelled `radar`, or [`latest.md`](latest.md). Past days are in `archive/`.

**Change it:** edit `config.json` (topics, keywords, limits) or `deadlines.json` in the GitHub app or website. A change to `config.json` runs the radar right away; otherwise Actions → Daily radar → Run workflow.

**Notifications:** in the GitHub app, Profile → Settings → Notifications → turn on push for "Issues" or "Mentions" (the digest mentions you).

## X, Reddit and LinkedIn: a routine instead of scrolling

None of the three can be read automatically for free any more: X charges per post read, Reddit is closing logged-out access and new API requests, and LinkedIn has no feed API. So use each one in a narrow, chronological, time-boxed way:

| App | Set up once | Then |
| --- | --- | --- |
| X | Make a List (for example "FL + edge AI") of 15 to 25 accounts: researchers you read, @flwrlabs, @huggingface, labs you'd apply to. Pin it | Open only the List, never "For you". 10 minutes, twice a week |
| Reddit | Make a Custom Feed of 3 to 4 subs: r/MachineLearning, r/LocalLLaMA, r/developersIndia | Open the Custom Feed, sort by Top → Today. 5 minutes a day |
| LinkedIn | Jobs → create alerts ("Machine Learning Engineer Bengaluru", "Research Intern federated learning"); ring the bell on 5 to 10 people and target companies | Read alerts and notifications only; skip the feed |

Set a 15-minute daily limit for all three together in Android Settings → Digital Wellbeing → Dashboard → App timers.
