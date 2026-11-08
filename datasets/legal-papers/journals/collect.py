"""Rebuild the 2024–2025 English legal journal snapshot (DOAJ, Crossref, publishers)."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import gzip
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import quote, urlsplit
from xml.etree import ElementTree

import requests
from bs4 import BeautifulSoup

HERE = Path(__file__).resolve().parent
JOURNALS = {
    "2071-8322": ("German Law Journal", "www.cambridge.org"),
    "1871-515X": ("Utrecht Law Review", "utrechtlawreview.org"),
    "2053-5341": ("Utrecht Journal of International and European Law", "utrechtjournal.org"),
    "2211-2545": ("Tilburg Law Review", "tilburglawreview.com"),
}
SOURCE = "DOAJ-Crossref-legal-journals"
CC = "creativecommons.org/licenses/by/4.0"
NOTICE = re.compile(
    r"(^editorial(?:$|:| of volume)|erratum|corrigendum|retraction|acknowledg(?:e)?ment to reviewers|"
    r"(?:front|back) ?matter|cover and|^(?:correction|contents|book reviews?)\b)", re.I
)


def clean(text):
    return " ".join(text.split())


def strip_markup(text):
    soup = BeautifulSoup(text or "", "html.parser")
    for heading in soup.find_all(["jats:title", "title"]):
        heading.decompose()
    for p in soup.find_all(["jats:p", "p", "br"]):
        p.append("\n")
    return "\n".join(clean(line) for line in soup.get_text().splitlines() if clean(line))


class Fetcher:
    def __init__(self, cache):
        self.cache = cache
        self.cache.mkdir(exist_ok=True, parents=True)

    def __call__(self, url):
        path = self.cache / hashlib.sha256(url.encode()).hexdigest()
        if not path.exists():
            response = requests.get(url, headers={"User-Agent": "LexAid scholarly catalogue/1.0"},
                                    timeout=(8, 18))
            response.raise_for_status()
            path.write_bytes(response.content)
        return path.read_bytes()


def catalog(fetch, issn):
    crossref_issn = "2211-0046" if issn == "2211-2545" else issn
    cr = json.loads(fetch(f"https://api.crossref.org/journals/{crossref_issn}/works?"
                          "filter=type:journal-article,from-pub-date:2024-01-01,until-pub-date:2025-12-31"
                          "&rows=1000"))["message"]
    cr_rows = {r["DOI"].lower(): r for r in cr["items"]}
    if len(cr_rows) != cr["total-results"]:
        raise ValueError("Partial Crossref response for " + issn)
    doaj = {}
    raw_ids = set()
    query = quote(f"issn:{issn} AND (bibjson.year:2024 OR bibjson.year:2025)", safe="")
    page = 1
    while True:
        result = json.loads(fetch(f"https://doaj.org/api/search/articles/{query}?pageSize=50&page={page}"))
        for entry in result["results"]:
            raw_ids.add(entry["id"])
            bib = entry["bibjson"]
            if issn not in bib["journal"]["issns"] or int(bib["year"]) not in {2024, 2025}:
                raise ValueError("DOAJ query returned an out-of-scope paper")
            dois = [x["id"].strip().lower() for x in bib["identifier"] if x["type"] == "doi"]
            if len(dois) != 1:
                raise ValueError(f"Expected one DOI for DOAJ article {entry['id']}")
            doaj[dois[0]] = entry
        if page * 50 >= result["total"]:
            break
        page += 1
    if len(raw_ids) != result["total"]:
        raise ValueError("Incomplete DOAJ pagination for " + issn)
    return cr_rows, doaj


def cc_by_4(url):
    return url.rstrip("/").lower() in ("https://" + CC, "http://" + CC)


def body_text_xml(body):
    blocks = []
    def visit(node):
        if node.tag in {"fig", "fig-group", "table-wrap", "table-wrap-group", "fn-group", "fn",
                        "disp-formula", "supplementary-material", "graphic"}:
            return
        if node.tag in {"title", "p"}:
            value = clean("".join(node.itertext()))
            if value:
                blocks.append(value)
        else:
            for child in node:
                visit(child)
    visit(body)
    return "\n\n".join(blocks) or None


def ojs_archive(fetch):
    base = "https://utrechtjournal.org"
    archive = BeautifulSoup(fetch(base + "/issue/archive"), "lxml")
    pages = {}
    for issue in archive.select(".obj_issue_summary"):
        if not re.search(r"\(202[45]\)", issue.get_text()):
            continue
        index = BeautifulSoup(fetch(issue.select_one("a[href]")["href"]), "lxml")
        for a in index.select("h3 a[href]"):
            url = a["href"]
            html = BeautifulSoup(fetch(url), "lxml")
            doi = html.select_one("meta[name=citation_doi]")
            if doi:
                pages[doi["content"].strip().lower()] = url
    return pages


def publication(fetch, doi, hostname, landing):
    """Return article-level CC BY evidence and available publisher text."""
    html = BeautifulSoup(fetch(landing), "lxml")
    ident = html.select('meta[name=citation_doi],meta[name="DC.Identifier"]')
    if not any(el.get("content", "").strip().lower() == doi for el in ident):
        return None
    if hostname == "www.cambridge.org":
        license_links = html.select(".creative-commons a[href]")
        body = html.select_one("#article-tab div.article div.body")
        if not body or not any(cc_by_4(a["href"]) for a in license_links):
            return None
        for item in body.select("figure, table, .fig, .table-wrap, .fn, .fn-group, .ref-list, "
                                ".disp-formula, .modal, .popover, script, style, .supplementary-material"):
            item.decompose()
        blocks = [clean(el.get_text()) for el in body.select("h2,h3,h4,h5,h6,p")
                  if not el.find_parent(["p","h2","h3","h4","h5","h6"])]
        content = "\n\n".join(x for x in blocks if x)
        return {"content": content or None, "evidence": landing, "abstract": None}
    if hostname == "utrechtjournal.org":
        if not any(cc_by_4(a["href"]) for a in html.select(".item.copyright a[href]")):
            return None
        abstract = html.select_one("section.abstract")
        if abstract:
            for title in abstract.select("h1,h2,h3"):
                title.decompose()
        return {"content": None, "evidence": landing,
                "abstract": clean(abstract.get_text()) if abstract else None}
    xml_link = html.select_one("meta[name=citation_xml_url]")
    if not xml_link:
        return None
    target = xml_link.get("content", "")
    if urlsplit(target).scheme != "https" or urlsplit(target).hostname != hostname:
        return None
    xml = ElementTree.fromstring(fetch(target))
    actual = (xml.findtext("./front/article-meta/article-id[@pub-id-type='doi']") or "").strip().lower()
    perms = xml.findall("./front/article-meta/permissions/license")
    if actual != doi or not any(cc_by_4(value) for node in perms for el in node.iter()
                                for value in el.attrib.values()):
        return None
    body = xml.find("./body")
    content = body_text_xml(body) if body is not None else None
    abstract = xml.find("./front/article-meta/abstract")
    return {"content": content, "evidence": target,
            "abstract": body_text_xml(abstract) if abstract is not None else None}


def row_for(fetch, issn, doi, deposit, entry, landings):
    bib = entry["bibjson"] if entry else {}
    if deposit is None:
        try:
            deposit = json.loads(fetch("https://api.crossref.org/works/" + quote(doi, safe="/")))["message"]
            if deposit["DOI"].lower() != doi:
                raise ValueError("DOI mismatch " + doi)
        except requests.HTTPError as exc:
            if exc.response.status_code != 404:
                raise
            deposit = {}
    journal, hostname = JOURNALS[issn]
    title = strip_markup(bib.get("title") or deposit["title"][0])
    authors = [a.get("name") for a in bib.get("author", [])]
    if not authors or not all(authors):
        authors = [clean(" ".join(filter(None, (x.get("given"), x.get("family"))))) or x.get("name")
                   for x in deposit.get("author", [])]
    if not authors or not all(authors) or deposit.get("update-to") or NOTICE.search(title):
        return None, {"doi": doi, "title": title, "reason": "notice or no complete author credit"}
    year = int(bib["year"]) if bib else deposit["published"]["date-parts"][0][0]
    if year not in {2024, 2025}:
        return None, {"doi": doi, "title": title, "reason": "outside selected years"}
    landing = landings.get(doi, "https://doi.org/" + doi)
    if not deposit and entry and hostname == "tilburglawreview.com":
        # This work is present in the publisher and DOAJ records although its
        # DOI currently lacks a Crossref deposit/working DOI resolver.
        landing = f"https://{hostname}/articles/" + doi
    try:
        printed = publication(fetch, doi, hostname, landing)
    except (requests.RequestException, ElementTree.ParseError) as exc:
        printed = None
        print(f"Publisher body skipped: {doi}: {exc}", flush=True)
    cc = any(cc_by_4(l.get("URL", "")) and l.get("content-version") in {"vor", "unspecified"}
             for l in deposit.get("license", ()))
    abstract = strip_markup(bib.get("abstract") or deposit.get("abstract")) if cc or printed else ""
    if not abstract and printed:
        abstract = printed["abstract"] or ""
    lang = deposit.get("language") or (bib.get("journal", {}).get("language") or ["en"])[0]
    record = dict(scholar_name="; ".join(authors), title=title, summary=abstract,
                  content=printed["content"] if printed else None, source=journal, publish_year=year,
                  language_code=lang.lower(), source_dataset=SOURCE, source_item_id=doi,
                  source_url=landing, doi=doi, license_name="CC-BY-4.0" if cc or printed else None,
                  summary_kind="author_abstract" if abstract else "metadata_only")
    return record, dict(doi=doi, issn=issn, doajId=entry.get("id") if entry else None,
                        crossrefRegistered=bool(deposit), crossrefCcBy4=cc,
                        publisherEvidence=printed["evidence"] if printed else None)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=HERE)
    args = parser.parse_args()
    fetch = Fetcher(args.cache_dir)
    rows, evidence, excluded, stats = [], [], [], {}
    for issn, (journal, _) in JOURNALS.items():
        cr, dj = catalog(fetch, issn)
        landings = ojs_archive(fetch) if issn == "2053-5341" else {}
        dois = sorted(set(dj) | set(cr))
        counts = Counter(crossrefSearch=len(cr), doajSearch=len(dj), candidates=len(dois))
        print(f"{journal}: {len(dois)} DOI candidates", flush=True)
        def build(doi):
            return row_for(fetch, issn, doi, cr.get(doi), dj.get(doi), landings)
        with ThreadPoolExecutor(max_workers=4) as pool:
            for row, source in pool.map(build, dois):
                if row:
                    rows.append(row)
                    evidence.append(source)
                    counts["records"] += 1
                    counts["authorAbstracts"] += bool(row["summary"])
                    counts["bodyTexts"] += bool(row["content"])
                else:
                    excluded.append(source)
        stats[journal] = dict(counts)
        print(dict(counts), flush=True)
    rows.sort(key=lambda r: r["doi"])
    if len({r["doi"] for r in rows}) != len(rows):
        raise ValueError("Overlapping DOI across selected journals")
    args.output_dir.mkdir(exist_ok=True, parents=True)
    path = args.output_dir / "records.json.gz"
    with path.open("wb") as f, gzip.GzipFile(filename="", fileobj=f, mode="wb", mtime=0) as gz:
        gz.write(json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode())
    report = {"journals": stats, "excluded": excluded, "records": len(rows),
              "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "sources": evidence}
    (args.output_dir / "provenance.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(rows)} papers; sha256 {report['sha256']}")


if __name__ == "__main__":
    main()
