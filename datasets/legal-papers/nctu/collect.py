"""Collect the DOAJ article index for NCTU Law Review (ISSN 2523-0298)."""

from collections import Counter
import gzip
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

import requests


API = "https://doaj.org/api/search/articles/issn%3A2523-0298"
OUT = Path(__file__).parent


def collect():
    items = {}
    with requests.Session() as session:
        for page in range(1, 101):
            response = session.get(API, params={"page": page, "pageSize": 50}, timeout=30)
            response.raise_for_status()
            data = response.json()
            for record in data["results"]:
                if record["id"] in items:
                    raise ValueError("DOAJ page repeated a record; retry on a stable index")
                items[record["id"]] = record["bibjson"]
            if len(items) == data["total"]:
                break
            if not data["results"] or len(items) > data["total"]:
                raise ValueError("DOAJ count changed during pagination")
        else:
            raise ValueError("Incomplete DOAJ page traversal")

    unique = {}
    for record_id, paper in items.items():
        if "2523-0298" not in paper["journal"]["issns"]:
            raise ValueError("Unexpected journal")
        title = " ".join(paper["title"].split())
        authors = "; ".join(" ".join(a["name"].split()) for a in paper["author"])
        year = int(paper["year"])
        doi = next((i["id"].lower().strip() for i in paper["identifier"] if i["type"] == "doi"), None)
        location = urlsplit(next(i["url"] for i in paper["link"] if i["type"] == "fulltext"))
        # Two papers were indexed twice when the publisher changed hosts.
        # Use the URL path, matching byline, title and publication year to
        # establish identity; retain the publisher's newer NYCU entry.
        fingerprint = (year, title, authors, location.path, location.query)
        preferred = (bool(doi), location.hostname == "lawreview.law.nycu.edu.tw", record_id)
        if fingerprint in unique and unique[fingerprint][0] >= preferred:
            continue
        row = dict(scholar_name=authors, title=title, source="交大法學評論",
                   publish_year=year, source_dataset="DOAJ-NCTU-Law-Review", source_item_id=record_id,
                   source_url=f"https://doi.org/{doi}" if doi else f"https://doaj.org/article/{record_id}",
                   doi=doi, language_code="zh" if re.search(r"[\u3400-\u9fff]", title) else "und",
                   license_name="CC0-1.0（DOAJ 题录）", summary_kind="metadata_only", summary="", content=None)
        unique[fingerprint] = (preferred, row)
    rows = sorted((r for _, r in unique.values()), key=lambda r: r["source_item_id"])
    if len({r["doi"] for r in rows if r["doi"]}) != sum(bool(r["doi"]) for r in rows):
        raise ValueError("Unresolved duplicate DOI")
    return rows, len(items)


if __name__ == "__main__":
    records, total = collect()
    with (OUT / "records.json.gz").open("wb") as raw:
        with gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0) as gz:
            gz.write(json.dumps(records, ensure_ascii=False, separators=(",", ":")).encode())
    print(total, "source records;", len(records), "distinct papers;", Counter(r["publish_year"] for r in records))
