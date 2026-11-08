"""Capture and normalize two complete journal-article queries from OpenAlex."""

import argparse
import collections
import concurrent.futures
import gzip
import hashlib
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


HERE = Path(__file__).resolve().parent
COMMON = "type:article,primary_location.source.type:journal,is_retracted:false,is_paratext:false"
QUERIES = {
    "zh-law": COMMON + ",language:zh,primary_topic.subfield.id:3308,"
    "from_publication_date:2024-01-01,to_publication_date:2025-12-31",
    "en-private-international-law": COMMON + ",language:en,primary_topic.id:T12040|T12767,publication_year:2025",
}
SELECT = "id,doi,title,authorships,primary_location,language,publication_year,type,primary_topic,is_retracted,is_paratext"
NOTICE = re.compile(r"^(?:correction to\b|retraction\b|erratum\b|corrigendum\b|table of contents\b|"
                    r"front matter\b|back matter\b|更正声明|撤稿声明|勘误[：:]|編者按[：:]|编者按[：:])", re.I)
# Bare roundup and issue-level headings are distinct from titled case comments.
ISSUE_HEADINGS = {"判決快遞", "判决快递", "目次", "目錄", "目录", "編務公告", "编务公告"}


def json_gz(path):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def write_gz(path, obj):
    payload = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    temp = path.with_suffix(".tmp")
    with temp.open("wb") as dest:
        with gzip.GzipFile(fileobj=dest, filename="", mode="wb", mtime=0) as gz:
            gz.write(payload)
    temp.replace(path)


def fetch(params):
    url = "https://api.openalex.org/works?" + urllib.parse.urlencode(params)
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "LexAid/1.0 (public law journal catalogue)"})
            with urllib.request.urlopen(req, timeout=45) as resp:
                return json.load(resp)
        except urllib.error.HTTPError as exc:
            if exc.code not in {429, 500, 502, 503, 504} or attempt == 3:
                raise
        except OSError:
            if attempt == 3:
                raise
        time.sleep(2 ** attempt)


def capture(cohort):
    page_number = 1
    seen = set()
    records = []
    while True:
        page = fetch({"filter": QUERIES[cohort], "per_page": 100, "sort": "openalex:asc",
                      "select": SELECT, "page": page_number})
        if page_number == 1:
            count = page["meta"]["count"]
            if count > 10000:
                raise ValueError("Query exceeds basic paging limit; split the date window")
        for w in page["results"]:
            if w["id"] in seen:
                raise ValueError("Repeated OpenAlex work in paginated result: " + w["id"])
            seen.add(w["id"])
            topic = w.get("primary_topic") or {}
            src = (w.get("primary_location") or {}).get("source") or {}
            authors = [a.get("raw_author_name") or (a.get("author") or {}).get("display_name")
                       for a in w.get("authorships", [])]
            records.append({"id": w["id"], "doi": w.get("doi"), "title": w.get("title"),
                            "author_names": authors, "journal": src.get("display_name"),
                            "source_type": src.get("type"), "language": w.get("language"),
                            "year": w.get("publication_year"), "type": w.get("type"),
                            "topic": topic.get("id"), "subfield": (topic.get("subfield") or {}).get("id"),
                            "is_retracted": w.get("is_retracted"), "is_paratext": w.get("is_paratext")})
        if len(page["results"]) < 100 or len(records) >= count:
            break
        page_number += 1
    if len(records) != count:
        raise ValueError(f"Partial query for {cohort}: {len(records)} of {count}, retry capture")
    return {"filter": QUERIES[cohort], "count": count, "works": sorted(records, key=lambda w: w["id"])}


def normalize(snapshot):
    seen = set()
    rows = []
    stats = {}
    for cohort, group in sorted(snapshot.items()):
        counts = collections.Counter()
        if group["filter"] != QUERIES[cohort] or group["count"] != len(group["works"]):
            raise ValueError("Unexpected scope or incomplete source file")
        for w in group["works"]:
            if (w["source_type"] != "journal" or w["type"] != "article" or
                    w["is_retracted"] or w["is_paratext"] or
                    w["subfield"] != "https://openalex.org/subfields/3308" or
                    (cohort == "zh-law" and (w["language"] != "zh" or w["year"] not in {2024, 2025})) or
                    (cohort != "zh-law" and (w["language"] != "en" or w["year"] != 2025 or
                       w["topic"] not in {"https://openalex.org/T12040", "https://openalex.org/T12767"}))):
                raise ValueError(f"Invalid source row for {cohort}: {w['id']}")
            if not (w["title"] or "").strip() or not (w["journal"] or "").strip():
                counts["missing_title_or_journal"] += 1
                continue
            if (not w["author_names"] or len(w["author_names"]) >= 100 or
                    not all((name or "").strip() for name in w["author_names"])):
                counts["missing_or_incomplete_authors"] += 1
                continue
            if NOTICE.match(w["title"].strip()) or w["title"].strip() in ISSUE_HEADINGS:
                counts["publication_notice"] += 1
                continue
            doi = (w["doi"] or "").lower().strip().removeprefix("https://doi.org/")
            if doi and (not re.fullmatch(r"10\.\d{4,9}/[^\s]+", doi) or len(doi) > 512):
                counts["invalid_doi"] += 1
                continue
            if doi and doi in seen:
                counts["duplicate_doi"] += 1
                continue
            if doi:
                seen.add(doi)
            rows.append({"scholar_name": "; ".join(name.strip() for name in w["author_names"]),
                         "title": w["title"].strip(), "summary": "", "content": None,
                         "source": w["journal"].strip(), "publish_year": w["year"],
                         "language_code": w["language"], "source_dataset": "OpenAlex-Law",
                         "source_item_id": w["id"].rsplit("/", 1)[-1],
                         "source_url": ("https://doi.org/" + doi) if doi else w["id"], "doi": doi or None,
                         "license_name": None, "summary_kind": "metadata_only"})
            counts["kept"] += 1
        stats[cohort] = dict(counts)
    rows.sort(key=lambda r: r["source_item_id"])
    if len({r["source_item_id"] for r in rows}) != len(rows):
        raise ValueError("Duplicate OpenAlex ID")
    return rows, stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="capture a new source snapshot over the network")
    parser.add_argument("--verify", action="store_true", help="check the existing records against the snapshot")
    args = parser.parse_args()
    source = HERE / "source.json.gz"
    target = HERE / "records.json.gz"
    if args.refresh:
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            snapshot = dict(zip(QUERIES, pool.map(capture, QUERIES)))
        write_gz(source, snapshot)
    rows, stats = normalize(json_gz(source))
    if not rows:
        raise ValueError("Empty OpenAlex catalogue")
    if args.verify:
        if json_gz(target) != rows:
            raise ValueError("Records differ from frozen source")
    else:
        write_gz(target, rows)
    print(json.dumps({"counts": stats, "total": len(rows),
                      "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}, indent=2))


if __name__ == "__main__":
    main()
