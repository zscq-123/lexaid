"""Export the public Yale Law Archive collection from the OSF provider API."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from functools import lru_cache
import gzip
import hashlib
import json
import math
from pathlib import Path
import re
import time
from urllib.parse import urlsplit

import requests


HERE = Path(__file__).resolve().parent
COLLECTION = "https://api.osf.io/v2/preprint_providers/lawarchive/preprints/"
LICENSES = {
    "CC-By Attribution 4.0 International": "CC-BY-4.0",
    "CC0 1.0 Universal": "CC0-1.0",
}


def get(url, params=None):
    for attempt in range(3):
        response = requests.get(url, params=params, timeout=(12, 75),
                                headers={"User-Agent": "LexAid-paper-catalog/1.0"})
        if response.status_code not in (502, 503, 504) or attempt == 2:
            response.raise_for_status()
            return response.json()
        time.sleep(2 ** attempt)


def collection_page(number, cache_dir):
    path = cache_dir / f"lawarchive-{number:03}.json.gz"
    if path.exists():
        with gzip.open(path, "rt", encoding="utf-8") as f:
            return json.load(f)
    payload = get(COLLECTION, [("page[size]", 100), ("embed", "bibliographic_contributors"),
                               ("page", number)])
    # Cache only what is used for this export, excluding OSF user profile fields.
    page = {"total": payload["links"]["meta"]["total"], "records": []}
    for node in payload["data"]:
        attrs = node["attributes"]
        provider = node["relationships"]["provider"]["data"]["id"]
        if provider != "lawarchive":
            raise ValueError(f"Unexpected OSF provider {provider}")
        contribs = node["embeds"]["bibliographic_contributors"]
        authors = [contributor_name(c) for c in contribs["data"]]
        next_page = contribs["links"].get("next")
        while next_page:
            additional = get(next_page, {"embed": "users"})
            authors.extend(contributor_name(c) for c in additional["data"])
            next_page = additional["links"].get("next")
        if len(authors) != contribs["links"]["meta"]["total"]:
            raise ValueError(f"Incomplete list of authors for {node['id']}")
        lic = node["relationships"].get("license", {}).get("data") or {}
        page["records"].append({
            "id": node["id"], "title": attrs["title"], "authors": authors,
            "public": attrs["public"] and attrs["is_published"],
            "withdrawn": attrs.get("date_withdrawn"), "latest": attrs["is_latest_version"],
            "version": attrs.get("version"), "licenseId": lic.get("id"),
            "date": attrs.get("date_published"), "abstract": attrs.get("description"),
            "doi_link": node["links"].get("preprint_doi"), "url": node["links"]["html"],
            "primary_file": (node["relationships"].get("primary_file", {}).get("data") or {}).get("id"),
        })
    with gzip.open(path.with_suffix(".tmp"), "wt", encoding="utf-8") as dest:
        json.dump(page, dest, ensure_ascii=False)
    path.with_suffix(".tmp").replace(path)
    print(f"OSF page {number}: {len(page['records'])}", flush=True)
    return page


def contributor_name(c):
    # The preprint-specific unregistered byline is authoritative when supplied.
    return (c["attributes"].get("unregistered_contributor") or
            c.get("embeds", {}).get("users", {}).get("data", {}).get("attributes", {}).get("full_name") or "").strip()


@lru_cache(maxsize=None)
def license_name(license_id):
    if not license_id:
        return None
    obj = get(f"https://api.osf.io/v2/licenses/{license_id}/")["data"]
    if obj["id"] != license_id:
        raise ValueError(f"License ID mismatch for {license_id}")
    return LICENSES.get(obj["attributes"]["name"])


def normalize(item):
    if not item["public"] or item["withdrawn"] or not item["latest"]:
        return None, "unpublishedWithdrawnOrEarlierVersion"
    authors = item["authors"]
    if not authors or not all(authors):
        citation = get(f"https://api.osf.io/v2/preprints/{item['id']}/citation/")["data"]
        if citation["id"] == item["id"] and citation["attributes"]["title"] == item["title"]:
            names = [" ".join(filter(None, (person.get("given"), person.get("family")))) or person.get("literal", "")
                     for person in citation["attributes"].get("author", [])]
            if len(names) == len(authors) and all(names):
                authors = names
    if not item["title"].strip() or not authors or not all(authors) or not item["date"]:
        return None, "incompleteBibliography"
    url = item["url"]
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "osf.io" or parsed.path.rstrip("/") != f"/preprints/lawarchive/{item['id']}":
        raise ValueError(f"Unrecognized source URL {url}")
    doi = None
    if item["doi_link"]:
        link = urlsplit(item["doi_link"])
        if link.hostname != "doi.org" or not re.fullmatch(r"/10\.\d{4,9}/.+", link.path):
            raise ValueError(f"Invalid DOI link {item['doi_link']}")
        doi = link.path[1:].strip().lower()
    license_ = license_name(item["licenseId"])
    abstract = (item["abstract"] or "").strip() if license_ else ""
    return dict(scholar_name="; ".join(authors), title=item["title"].strip(),
                source="Yale Law Archive (OSF)", publish_year=int(item["date"][:4]),
                source_dataset="Yale-Law-Archive-OSF", source_item_id=item["id"],
                source_url=url, doi=doi, language_code="und", license_name=license_,
                summary_kind="author_abstract" if abstract else "metadata_only",
                summary=abstract, content=None), None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=HERE)
    args = parser.parse_args()
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    first = collection_page(1, args.cache_dir)
    total = first["total"]
    pages = [first]
    with ThreadPoolExecutor(max_workers=2) as pool:
        pages.extend(pool.map(lambda p: collection_page(p, args.cache_dir),
                              range(2, math.ceil(total / 100) + 1)))
    items = [row for page in pages for row in page["records"]]
    if len(items) != total or len({row["id"] for row in items}) != total:
        raise ValueError("The provider index changed or pagination is incomplete; restart with a fresh cache")
    from texts import collect
    args.output_dir.mkdir(parents=True, exist_ok=True)
    bodies = collect(items, args.cache_dir, args.output_dir, get, license_name)
    rows = []
    skipped = Counter()
    for item in items:
        row, reason = normalize(item)
        if row:
            row["content"] = bodies.get(item["id"])
            rows.append(row)
        else:
            skipped[reason] += 1
    rows.sort(key=lambda r: r["source_item_id"])
    dois = [r["doi"] for r in rows if r["doi"]]
    if len(set(dois)) != len(dois):
        raise ValueError("Duplicate OSF preprint DOI in current versions")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    raw = json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode()
    target = args.output_dir / "records.json.gz"
    with target.open("wb") as dest, gzip.GzipFile(filename="", mode="wb", fileobj=dest, mtime=0) as gz:
        gz.write(raw)
    print(json.dumps({"providerCount": total, "records": len(rows),
                      "authorAbstracts": sum(bool(r["summary"]) for r in rows), "dois": len(dois),
                      "skipped": skipped, "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}))


if __name__ == "__main__":
    main()
