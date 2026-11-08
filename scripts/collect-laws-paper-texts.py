#!/usr/bin/env python3
"""Extract licensed article body text from the 2025 volume of Laws (MDPI)."""

import argparse
import gzip
import json
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from xml.etree import ElementTree as ET


CROSSREF = (
    "https://api.crossref.org/journals/2075-471X/works?"
    "filter=from-pub-date:2025-01-01,until-pub-date:2025-12-31,type:journal-article&rows=200&select=DOI"
)
CC_BY = "creativecommons.org/licenses/by/4.0"
OMIT = {"fig", "fig-group", "graphic", "inline-graphic", "media", "supplementary-material",
        "table-wrap", "table-wrap-group", "fn", "fn-group", "disp-formula"}


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "LexAid/1.0 (open law journal catalogue)"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read()
        except OSError:
            if attempt == 2:
                raise
            time.sleep(1 + attempt)


def inline_text(node):
    parts = [node.text or ""]
    for child in node:
        if child.tag not in OMIT:
            parts.append(inline_text(child))
        parts.append(child.tail or "")
    return "".join(parts)


def paragraphs(node):
    if node.tag in OMIT:
        return
    if node.tag in {"title", "p", "list-item"}:
        value = " ".join(inline_text(node).split())
        if value:
            yield value
    else:
        for child in node:
            yield from paragraphs(child)


def extract(xml, doi):
    article = ET.fromstring(xml)
    meta = article.find("./front/article-meta")
    if meta is None or (meta.findtext("article-id[@pub-id-type='doi']") or "").strip().lower() != doi:
        raise ValueError(f"JATS article does not match {doi}")
    licenses = meta.findall("./permissions/license")
    license_text = " ".join("".join(node.itertext()) for node in licenses)
    license_text += " " + " ".join(value for node in licenses for child in node.iter()
                                   for value in child.attrib.values())
    if CC_BY not in license_text:
        return None
    body = article.find("body")
    return "\n\n".join(paragraphs(body)) if body is not None else None


def fetch_body(doi):
    match = re.fullmatch(r"10\.3390/laws(\d{2})\d{2}(\d{4})", doi)
    if not match:
        raise ValueError(f"Unexpected 2025 DOI: {doi}")
    article_id = f"laws-{match[1]}-{int(match[2]):05d}"
    url = f"https://mdpi-res.com/d_attachment/laws/{article_id}/article_deploy/{article_id}.xml"
    try:
        return doi, extract(download(url), doi)
    except (OSError, ValueError, ET.ParseError) as exc:
        print(f"{doi}: {exc}", file=sys.stderr)
        return doi, None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = json.loads(download(CROSSREF))["message"]
    dois = sorted({row["DOI"].lower() for row in result["items"]})
    if len(dois) != result["total-results"]:
        raise ValueError("Crossref response does not cover the entire journal year")
    with ThreadPoolExecutor(max_workers=4) as pool:
        bodies = {doi: body for doi, body in pool.map(fetch_body, dois) if body}
    if not bodies:
        raise RuntimeError("No licensed JATS body recovered")
    data = json.dumps(bodies, ensure_ascii=False, sort_keys=True).encode()
    with args.output.open("wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as stream:
        stream.write(data)
    print(f"{len(bodies)}/{len(dois)} records have licensed body text")


if __name__ == "__main__":
    main()
