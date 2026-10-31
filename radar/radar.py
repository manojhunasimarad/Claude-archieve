#!/usr/bin/env python3
"""Daily radar: a short digest of what is new and worth your time, from open sources only.

Sources (all free, no accounts): arXiv (new papers on your topics), Hugging Face Daily Papers (what AI
researchers upvote today), Hacker News (stories gaining points), GitHub (repos rising in the last 14 days),
plus a countdown for your deadlines. Anything shown once is never shown again, so the digest only holds
new things and stays short.

    python radar/radar.py                 # writes radar/latest.md and radar/archive/<date>.md
Edit radar/config.json (topics, keywords, limits) and radar/deadlines.json. Python standard library only.
"""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
IST = timezone(timedelta(hours=5, minutes=30))
UA = "manoj-radar/1.0 (personal daily digest)"
ATOM = {"a": "http://www.w3.org/2005/Atom"}


# ---------- fetching ----------

def http_get(url: str, headers: dict | None = None, timeout: int = 30) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def keyword_hits(text: str, keywords: list[str]) -> list[str]:
    """Keywords found at the start of a word ("quantiz" matches "quantization"); short ones like "ros" or
    "rag" must be whole words, so "across" or "storage" don't count."""
    t = text.lower()
    hits = []
    for k in keywords:
        kl = k.lower()
        end = r"(?![a-z0-9])" if len(kl) <= 4 else ""
        if re.search(r"(?<![a-z0-9])" + re.escape(kl) + end, t) and k not in hits:
            hits.append(k)
    return hits[:3]


def first_sentence(text: str, limit: int = 200) -> str:
    text = " ".join(text.split())
    cut = text.find(". ")
    s = text[: cut + 1] if 0 < cut < limit else text[:limit]
    return s if len(s) < len(text) or s.endswith(".") else s + "..."


# ---------- parsers (pure functions, tested offline) ----------

def parse_arxiv(xml_bytes: bytes, topic: str) -> list[dict]:
    root = ET.fromstring(xml_bytes)
    items = []
    for e in root.findall("a:entry", ATOM):
        raw_id = e.findtext("a:id", "", ATOM).rsplit("/abs/", 1)[-1]
        aid = raw_id.rsplit("v", 1)[0] if "v" in raw_id.split("/")[-1] else raw_id
        title = " ".join(e.findtext("a:title", "", ATOM).split())
        summary = e.findtext("a:summary", "", ATOM)
        if not aid or not title:
            continue
        items.append({
            "id": f"arxiv:{aid}", "arxiv_id": aid, "title": title, "url": f"https://arxiv.org/abs/{aid}",
            "summary": first_sentence(summary), "text": f"{title} {summary}",
            "published": e.findtext("a:published", "", ATOM), "topic": topic,
        })
    return items


def parse_hf(json_bytes: bytes) -> list[dict]:
    items = []
    for d in json.loads(json_bytes):
        p = d.get("paper", d)
        pid = p.get("id")
        if not pid:
            continue
        title = " ".join((p.get("title") or d.get("title") or "").split())
        summary = p.get("summary") or d.get("summary") or ""
        items.append({
            "id": f"arxiv:{pid}", "arxiv_id": pid, "title": title, "url": f"https://huggingface.co/papers/{pid}",
            "summary": first_sentence(summary), "text": f"{title} {summary}", "upvotes": int(p.get("upvotes", 0) or 0),
        })
    return items


def parse_hn(json_bytes: bytes) -> list[dict]:
    items = []
    for h in json.loads(json_bytes).get("hits", []):
        oid = h.get("objectID")
        title = h.get("title") or ""
        if not oid or not title:
            continue
        discussion = f"https://news.ycombinator.com/item?id={oid}"
        items.append({
            "id": f"hn:{oid}", "title": title, "url": h.get("url") or discussion, "discussion": discussion,
            "points": int(h.get("points") or 0), "comments": int(h.get("num_comments") or 0), "text": title,
        })
    return items


def parse_github(json_bytes: bytes) -> list[dict]:
    items = []
    for r in json.loads(json_bytes).get("items", []):
        name = r.get("full_name")
        if not name:
            continue
        desc = (r.get("description") or "").strip()
        items.append({
            "id": f"gh:{name.lower()}", "title": name, "url": r.get("html_url"), "summary": desc[:160],
            "stars": int(r.get("stargazers_count") or 0), "language": r.get("language") or "", "text": f"{name} {desc}",
        })
    return items


# ---------- sources ----------

def fetch_arxiv(cfg: dict, now: datetime) -> list[dict]:
    out, cutoff = [], (now - timedelta(days=4)).strftime("%Y-%m-%d")
    for i, topic in enumerate(cfg["topics"]):
        if i:
            time.sleep(3)  # arXiv asks for one request every 3 seconds
        q = urllib.parse.urlencode({"search_query": topic["arxiv"], "sortBy": "submittedDate",
                                    "sortOrder": "descending", "max_results": 40})
        for item in parse_arxiv(http_get(f"https://export.arxiv.org/api/query?{q}"), topic["name"]):
            if item["published"][:10] >= cutoff:
                out.append(item)
    return out


def fetch_hf() -> list[dict]:
    return parse_hf(http_get("https://huggingface.co/api/daily_papers?limit=100"))


def fetch_hn(cfg: dict, now: datetime) -> list[dict]:
    since = int((now - timedelta(hours=26)).timestamp())
    q = urllib.parse.urlencode({"tags": "story", "hitsPerPage": 200,
                                "numericFilters": f"created_at_i>{since},points>{cfg['limits']['hn_min_points']}"})
    return parse_hn(http_get(f"https://hn.algolia.com/api/v1/search?{q}"))


def fetch_github(cfg: dict, now: datetime) -> list[dict]:
    since = (now - timedelta(days=14)).strftime("%Y-%m-%d")
    headers = {"Accept": "application/vnd.github+json"}
    if os.getenv("GITHUB_TOKEN"):
        headers["Authorization"] = f"Bearer {os.environ['GITHUB_TOKEN']}"
    out = []
    for query in cfg["github_queries"]:
        q = urllib.parse.urlencode({"q": f"{query} created:>{since} stars:>15", "sort": "stars",
                                    "order": "desc", "per_page": 6})
        out += parse_github(http_get(f"https://api.github.com/search/repositories?{q}", headers))
    return out


# ---------- selection and output ----------

def select(cfg, seen, arxiv, hf, hn, gh):
    kw, lim = cfg["keywords"], cfg["limits"]
    fresh = lambda items: [x for x in items if x["id"] not in seen]  # noqa: E731
    hf_votes = {x["arxiv_id"]: x["upvotes"] for x in hf}

    papers, ids = [], set()
    for x in fresh(arxiv):
        if x["id"] in ids:
            continue
        ids.add(x["id"])
        x["hits"] = keyword_hits(x["text"], kw)
        x["upvotes"] = hf_votes.get(x["arxiv_id"], 0)
        papers.append(x)
    papers.sort(key=lambda x: (x["upvotes"] > 0, len(x["hits"]), x["upvotes"], x["published"]), reverse=True)
    papers = papers[: lim["papers"]]

    shown = {p["id"] for p in papers}
    trending = sorted([x for x in fresh(hf) if x["id"] not in shown], key=lambda x: x["upvotes"], reverse=True)
    for x in trending:
        x["hits"] = keyword_hits(x["text"], kw)
    trending = trending[: lim["trending"]]

    repos, names = [], set()
    for x in fresh(gh):
        x["hits"] = keyword_hits(x["text"], kw)
    for x in sorted(fresh(gh), key=lambda x: (min(len(x["hits"]), 2), x["stars"]), reverse=True):
        if x["id"] not in names:
            names.add(x["id"])
            repos.append(x)
    repos = repos[: lim["repos"]]

    on_topic, big_news = [], []
    for x in fresh(hn):
        x["hits"] = keyword_hits(x["text"], kw)
        if x["hits"]:
            on_topic.append(x)
        elif x["points"] >= lim["hn_big_news_points"]:
            big_news.append(x)
    on_topic.sort(key=lambda x: x["points"], reverse=True)
    big_news.sort(key=lambda x: x["points"], reverse=True)
    talks = (on_topic + big_news[:2])[: lim["discussions"]]  # at most 2 off-topic headlines
    return papers, trending, repos, talks


def upcoming(deadlines: list[dict], today: date, horizon: int = 75) -> list[str]:
    out = []
    for d in sorted(deadlines, key=lambda d: d["date"]):
        days = (date.fromisoformat(d["date"]) - today).days
        if 0 <= days <= horizon:
            when = "today" if days == 0 else ("tomorrow" if days == 1 else f"in {days} days")
            strong = days <= 10
            label = f"**{when}**" if strong else when
            out.append(f"[{d['name']}]({d['url']}) {label} ({date.fromisoformat(d['date']).strftime('%d %b')})")
    return out


def esc(text: str) -> str:
    return text.replace("[", "(").replace("]", ")")


def build_markdown(today: date, papers, trending, repos, talks, deadlines, status: dict) -> str:
    n = len(papers) + len(trending) + len(repos) + len(talks)
    lines = [f"# Radar · {today.strftime('%a %d %b %Y')}", ""]
    lines.append(f"_{n} new items, about {max(2, round(n * 0.5))} minutes. Nothing here has been shown before._"
                 if n else "_Nothing new worth your time today._")
    lines.append("")
    if deadlines:
        lines += ["**Deadlines:** " + " · ".join(deadlines), ""]
    if papers:
        lines += ["## New papers on your topics", ""]
        for i, p in enumerate(papers, 1):
            extra = [p["topic"]]
            if p["upvotes"]:
                extra.append(f"{p['upvotes']} upvotes on Hugging Face")
            lines.append(f"{i}. **[{esc(p['title'])}]({p['url']})**: {p['summary']} _({'; '.join(extra)})_")
        lines.append("")
    if trending:
        lines += ["## Trending in AI research today", ""]
        for p in trending:
            tag = f" _(matches: {', '.join(p['hits'])})_" if p["hits"] else ""
            lines.append(f"- **[{esc(p['title'])}]({p['url']})** · {p['upvotes']} upvotes: {p['summary']}{tag}")
        lines.append("")
    if repos:
        lines += ["## Rising repos (created in the last 14 days)", ""]
        for r in repos:
            lang = f" · {r['language']}" if r["language"] else ""
            lines.append(f"- **[{r['title']}]({r['url']})** · {r['stars']:,} stars{lang}: {esc(r['summary']) or 'no description'}")
        lines.append("")
    if talks:
        lines += ["## Worth reading on Hacker News", ""]
        for t in talks:
            lines.append(f"- **[{esc(t['title'])}]({t['url']})** · {t['points']} points, "
                         f"[{t['comments']} comments]({t['discussion']})")
        lines.append("")
    ok = " · ".join(f"{k} {'ok' if v is True else 'failed (' + str(v)[:60] + ')'}" for k, v in status.items())
    lines += ["---", f"Sources: {ok}. Edit topics in `radar/config.json`."]
    return "\n".join(lines) + "\n"


def main() -> int:
    now = datetime.now(timezone.utc)
    today = now.astimezone(IST).date()
    cfg = json.loads((HERE / "config.json").read_text())
    deadlines = json.loads((HERE / "deadlines.json").read_text())
    seen_path = HERE / "seen.json"
    seen = json.loads(seen_path.read_text()) if seen_path.exists() else {}

    status, data = {}, {}
    for name, fn in (("arXiv", lambda: fetch_arxiv(cfg, now)), ("Hugging Face", fetch_hf),
                     ("Hacker News", lambda: fetch_hn(cfg, now)), ("GitHub", lambda: fetch_github(cfg, now))):
        try:
            data[name] = fn()
            status[name] = True
        except Exception as e:  # noqa: BLE001 - one broken source must not stop the digest
            data[name] = []
            status[name] = f"{type(e).__name__}: {e}"
            print(f"{name} failed: {e}", file=sys.stderr)

    papers, trending, repos, talks = select(cfg, seen, data["arXiv"], data["Hugging Face"],
                                            data["Hacker News"], data["GitHub"])
    md = build_markdown(today, papers, trending, repos, talks, upcoming(deadlines, today), status)

    (HERE / "latest.md").write_text(md)
    (HERE / "archive").mkdir(exist_ok=True)
    (HERE / "archive" / f"{today.isoformat()}.md").write_text(md)
    for item in papers + trending + repos + talks:
        seen[item["id"]] = today.isoformat()
    cutoff = (today - timedelta(days=60)).isoformat()
    seen = {k: v for k, v in seen.items() if v >= cutoff}
    seen_path.write_text(json.dumps(seen, indent=0, sort_keys=True))
    print(md)
    return 0 if any(v is True for v in status.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
