"""Capture the 2025 Crossref journal-article catalog and publisher-supplied Chinese labels."""
import argparse
import gzip
import json
import re
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlsplit

import requests
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


ROOT = Path(__file__).resolve().parents[1] / "datasets/legal-papers"
JOURNALS = {"hans": "2329-7360", "laws": "2075-471X"}
ADMIN_NOTICE = re.compile(
    r"^(?:editorial\b|correction\b|retraction\b|erratum\b|corrigendum\b|"
    r"acknowledg(?:e)?ment to (?:the )?reviewers|撤稿|更正声明|勘误|致审稿人|编者按)", re.I)


def fetch(url, path, params=None):
    if not path.exists():
        session = requests.Session()
        retry = Retry(total=3, backoff_factor=1, status_forcelist=(429, 500, 502, 503, 504))
        session.mount("https://", HTTPAdapter(max_retries=retry))
        with session:
            response = session.get(url, params=params, timeout=25,
                                   headers={"User-Agent": "LexAid-paper-catalog/1.0"})
            response.raise_for_status()
            path.write_bytes(response.content)
        time.sleep(0.3)
    return path.read_bytes()


def journal_items(cache, name):
    url = f"https://api.crossref.org/journals/{JOURNALS[name]}/works"
    params = {"filter": "from-pub-date:2025-01-01,until-pub-date:2025-12-31,type:journal-article",
              "rows": 1000}
    data = json.loads(fetch(url, cache / (name + ".json"), params))["message"]
    if len(data["items"]) != data["total-results"]:
        raise ValueError("Incomplete Crossref result set")
    return sorted(data["items"], key=lambda item: item["DOI"].lower())


def text_fragment(html):
    # Crossref uses inline emphasis, superscripts and paragraph elements in JATS.
    soup = BeautifulSoup(html, "html.parser")
    for element in soup.find_all(["jats:p", "p", "br"]):
        element.append("\n")
    return "\n".join(" ".join(line.split()) for line in soup.get_text().splitlines()
                     if line.strip())


def base_row(item, source, language):
    doi = item["DOI"].lower().strip()
    return dict(doi=doi, source_item_id=doi, source_dataset="Crossref-law-journals",
                source_url="https://doi.org/" + doi, source=source, language_code=language,
                publish_year=item["published"]["date-parts"][0][0], summary="", content=None,
                license_name=None, summary_kind="metadata_only")


def laws_row(item):
    row = base_row(item, "Laws", "en")
    authors = [" ".join(filter(None, (a.get("given"), a.get("family")))) or a.get("name")
               for a in item["author"]]
    if not all(authors):
        raise ValueError("Incomplete author credit " + row["doi"])
    row.update(title=text_fragment(item["title"][0]), scholar_name="; ".join(authors))
    allowed = any(x.get("URL", "").rstrip("/") in {
        "http://creativecommons.org/licenses/by/4.0", "https://creativecommons.org/licenses/by/4.0"}
                  and x.get("content-version") in ("vor", "unspecified")
                  for x in item.get("license", []))
    if allowed:
        row["license_name"] = "CC-BY-4.0"
        if item.get("abstract"):
            row.update(summary=text_fragment(item["abstract"]), summary_kind="author_abstract")
    return row


def hans_row(item, cache, evidence):
    row = base_row(item, "法学（汉斯出版社）", "zh-CN")
    url = item["resource"]["primary"]["URL"]
    if urlsplit(url).hostname != "www.hanspub.org":
        raise ValueError("Unexpected publisher URL " + url)
    html = fetch(url, cache / (row["doi"].split("/")[1] + ".html"))
    soup = BeautifulSoup(html.decode("utf-8-sig"), "html.parser")
    meta = {m["name"].lower(): m["content"].strip()
            for m in soup.select("meta[name][content]")}
    if (meta.get("citation_doi", "").strip().lower() != row["doi"]
            or not re.search(r"[\u3400-\u9fff]", meta.get("citation_title", ""))):
        authors = []
        for author in item["author"]:
            family, given = author.get("family", ""), author.get("given", "")
            if re.search(r"[\u3400-\u9fff]", family + given):
                name = family + given
            else:
                name = " ".join(filter(None, (given, family))) or author.get("name")
            if not name:
                raise ValueError("Incomplete bibliographic credit " + row["doi"])
            authors.append(name)
        row.update(title=text_fragment(item["title"][0]), scholar_name="; ".join(authors),
                   language_code=item.get("language") or "und")
        return row
    row.update(title=meta["citation_title"], scholar_name=meta["citation_authors"])
    proof = evidence.get(row["doi"])
    if proof:
        if proof != meta.get("citation_pdf_url"):
            raise ValueError("Publisher changed the PDF for " + row["doi"])
        element = soup.select_one("div[id$='_div_abs_zw']")
        if element is None:
            raise ValueError("Chinese abstract element missing " + row["doi"])
        row.update(summary=re.sub(r"^\s*(?:摘\s*要)\s*[:：]\s*", "",
                                  element.get_text(" ", strip=True)), license_name="CC-BY-4.0",
                   summary_kind="author_abstract")
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, required=True)
    args = parser.parse_args()
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    evidence = json.loads((ROOT / "hans-cc-by.json").read_text())
    evidence.update(json.loads((ROOT / "hans/hans-verified.json").read_text()))
    catalog = {"records": [], "candidateCounts": {}, "excluded": []}
    for journal in JOURNALS:
        items = journal_items(args.cache_dir, journal)
        catalog["candidateCounts"][journal] = len(items)
        eligible = []
        for item in items:
            title = text_fragment(item["title"][0])
            if item.get("update-to") or ADMIN_NOTICE.search(title):
                catalog["excluded"].append({"doi": item["DOI"], "title": title,
                                             "reason": "editorial notice or update"})
            else:
                eligible.append(item)
        if journal == "hans":
            with ThreadPoolExecutor(max_workers=3) as pool:
                rows = list(pool.map(lambda i: hans_row(i, args.cache_dir, evidence), eligible))
        else:
            rows = [laws_row(i) for i in eligible]
        catalog["records"].extend(rows)
    raw = json.dumps(catalog, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with (ROOT / "crossref-catalog.json.gz").open("wb") as dest:
        with gzip.GzipFile(fileobj=dest, filename="", mode="wb", mtime=0) as gz:
            gz.write(raw)
    print(catalog["candidateCounts"], "excluded", catalog["excluded"])


if __name__ == "__main__":
    main()
