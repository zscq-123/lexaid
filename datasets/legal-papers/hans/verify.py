"""Verify each article's DOI and explicit CC BY 4.0 grant in its publisher PDF.

Usage: python3 verify.py --catalog /path/to/hans.json --cache-dir /path/to/cache
Dependencies: requests, beautifulsoup4, pymupdf.
"""
import argparse
from concurrent.futures import ProcessPoolExecutor
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

from bs4 import BeautifulSoup
import pymupdf
import requests


HERE = Path(__file__).resolve().parent
CC_BY_GRANT = re.compile(
    r"this work is licensed under the creative commons attribution international license"
    r"\s*\(cc by 4\.0\)\.?", re.I)
HEADERS = {"User-Agent": "LexAid-paper-catalog/1.0"}


def read_resource(url, cache, host):
    if urlsplit(url).hostname != host:
        raise ValueError("unexpected resource hostname")
    if cache.exists():
        return cache.read_bytes()
    with requests.get(url, headers=HEADERS, stream=True, timeout=(5, 10)) as response:
        response.raise_for_status()
        if urlsplit(response.url).hostname != host:
            raise ValueError("unexpected redirect")
        body = bytearray()
        for chunk in response.iter_content(65536):
            body.extend(chunk)
            if len(body) > 12 * 1024 * 1024:
                raise ValueError("resource size limit")
    if cache.suffix == ".pdf" and not body.startswith(b"%PDF-"):
        raise ValueError("invalid PDF response")
    cache.write_bytes(body)
    return bytes(body)


def verify(task):
    doi, url, cache_dir = task
    stem = doi.split("/")[-1]
    try:
        html_path = cache_dir / (stem + ".html")
        if not html_path.exists():
            html_path = cache_dir / "hans-pdf-cache" / html_path.name
        body = read_resource(url, html_path, "www.hanspub.org")
        soup = BeautifulSoup(body.decode("utf-8-sig"), "html.parser")
        meta = {m["name"]: m["content"].strip() for m in soup.select("meta[name][content]")}
        if meta.get("citation_doi", "").lower() != doi:
            raise ValueError("publisher page DOI mismatch")
        pdf_url = meta["citation_pdf_url"]
        pdf = read_resource(pdf_url, cache_dir / "hans-pdf-cache" / (stem + ".pdf"),
                            "pdf.hanspub.org")
        with pymupdf.open(stream=pdf, filetype="pdf") as doc:
            pages = [" ".join(page.get_text(sort=True).lower().split())
                     for page in doc.pages(0, min(3, len(doc)))]
        if not pages or not re.search(re.escape(doi) + r"(?![\w.])", pages[0]):
            raise ValueError("PDF first page DOI mismatch")
        if not any(CC_BY_GRANT.search(p) and "creativecommons.org/licenses/by/4.0/" in p
                   for p in pages):
            raise ValueError("specific license grant absent")
        return doi, pdf_url, None
    except (requests.RequestException, ValueError, KeyError, RuntimeError) as exc:
        return doi, None, str(exc)


def write_map(path, data):
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(dict(sorted(data.items())), indent=2) + "\n")
    temp.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--cache-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=HERE / "hans-verified.json")
    parser.add_argument("--workers", type=int, choices=range(1, 7), default=4)
    args = parser.parse_args()
    (args.cache_dir / "hans-pdf-cache").mkdir(parents=True, exist_ok=True)
    items = json.loads(args.catalog.read_text())["message"]["items"]
    tasks = sorted((i["DOI"].lower(), i["resource"]["primary"]["URL"], args.cache_dir)
                   for i in items if not i.get("update-to"))
    verified = {}
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        for index, (doi, pdf_url, reason) in enumerate(pool.map(verify, tasks), 1):
            if reason:
                print(f"{doi}: {reason}", flush=True)
            else:
                verified[doi] = pdf_url
            if index % 20 == 0:
                print(f"{index}/{len(tasks)} checked, {len(verified)} verified.", flush=True)
                write_map(args.output, verified)
    write_map(args.output, verified)
    print(f"Finished. {len(verified)} verified out of {len(tasks)} eligible records.")


if __name__ == "__main__":
    main()
