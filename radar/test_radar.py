"""Offline tests for radar.py: parsers on sample responses, then a full run with the network faked.
    python -m pytest -q radar/test_radar.py
"""
import json
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import radar  # noqa: E402

NOW = datetime.now(timezone.utc)
ARXIV = f"""<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry>
    <id>http://arxiv.org/abs/2611.01234v1</id>
    <published>{NOW.strftime('%Y-%m-%dT%H:%M:%SZ')}</published>
    <title>Communication-Efficient Federated Learning
      for Vehicular Edge Networks</title>
    <summary>We study federated learning on vehicles with slow uplinks. Our method cuts uploads by 10x.</summary>
  </entry>
  <entry>
    <id>http://arxiv.org/abs/2401.00001v3</id>
    <published>2024-01-01T00:00:00Z</published>
    <title>An old paper</title>
    <summary>Too old to show.</summary>
  </entry>
</feed>""".encode()
HF = json.dumps([
    {"paper": {"id": "2611.09999", "title": "A Tiny LLM Trained From Scratch", "upvotes": 210,
               "summary": "We pretrain a small language model on one GPU. It matches larger models."}},
    {"paper": {"id": "2611.01234", "title": "Communication-Efficient Federated Learning", "upvotes": 35,
               "summary": "Vehicles."}},
]).encode()
HN = json.dumps({"hits": [
    {"objectID": "1", "title": "Show HN: Run an LLM on a Raspberry Pi 5", "url": "https://example.com/pi",
     "points": 320, "num_comments": 80},
    {"objectID": "2", "title": "A big unrelated launch", "url": "https://example.com/big", "points": 900, "num_comments": 500},
    {"objectID": "3", "title": "Something small and unrelated", "url": None, "points": 70, "num_comments": 3},
]}).encode()
GH = json.dumps({"items": [
    {"full_name": "someone/fl-on-cars", "html_url": "https://github.com/someone/fl-on-cars",
     "description": "Federated learning for connected cars", "stargazers_count": 140, "language": "Python"},
]}).encode()


def fake_get(url, headers=None, timeout=30):
    if "arxiv.org" in url:
        return ARXIV
    if "huggingface.co" in url:
        return HF
    if "algolia" in url:
        return HN
    if "api.github.com" in url:
        return GH
    raise OSError("unexpected url " + url)


def test_parsers():
    a = radar.parse_arxiv(ARXIV, "FL")
    assert a[0]["arxiv_id"] == "2611.01234" and "Vehicular Edge" in a[0]["title"] and "\n" not in a[0]["title"]
    assert radar.parse_hf(HF)[0]["upvotes"] == 210
    hn = radar.parse_hn(HN)
    assert hn[2]["url"].startswith("https://news.ycombinator.com/item?id=")
    assert radar.parse_github(GH)[0]["stars"] == 140


def test_deadline_countdown():
    lines = radar.upcoming([{"name": "X", "date": (date.today() + timedelta(days=3)).isoformat(), "url": "u"},
                            {"name": "Past", "date": "2000-01-01", "url": "u"}], date.today())
    assert len(lines) == 1 and "in 3 days" in lines[0]


def test_full_run_and_no_repeats(tmp_path, monkeypatch):
    for f in ("config.json", "deadlines.json"):
        (tmp_path / f).write_text((radar.HERE / f).read_text())
    monkeypatch.setattr(radar, "HERE", tmp_path)
    monkeypatch.setattr(radar, "http_get", fake_get)
    monkeypatch.setattr(radar.time, "sleep", lambda s: None)
    assert radar.main() == 0
    first = (tmp_path / "latest.md").read_text()
    assert "Communication-Efficient Federated Learning" in first
    assert "35 upvotes on Hugging Face" in first          # arXiv paper boosted by its HF upvotes
    assert "A Tiny LLM Trained From Scratch" in first     # trending section
    assert "someone/fl-on-cars" in first
    assert "Raspberry Pi" in first and "A big unrelated launch" in first
    assert "Something small and unrelated" not in first  # low points, no keyword: filtered out
    assert "An old paper" not in first
    radar.main()
    second = (tmp_path / "latest.md").read_text()
    assert "Nothing new worth your time today" in second  # everything was already shown


def test_keyword_boundaries():
    kw = ["ros", "rag", "quantiz", "edge"]
    assert radar.keyword_hits("works across storage and fragments", kw) == []
    assert radar.keyword_hits("A ROS node with RAG and quantization at the edge", kw) == ["ros", "rag", "quantiz", "edge"][:3]
