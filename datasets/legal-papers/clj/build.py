"""Export the three 2024 issues of China Law Journal from the publisher's archive."""

import argparse
import hashlib
import json
import re
from io import BytesIO
from pathlib import Path

import pymupdf
import requests
from bs4 import BeautifulSoup


ARCHIVE = "https://clj.ac/archives/"
ISSUES = {
    1: ("2-CLJ-24S-2.1.pdf", "2a1a1616ca2f3e9ed10fe7cbb942f827e982a3ca3cd4dd862941c4faa321af54", 7),
    2: ("3-CLJ-24SS-2.2.pdf", "383f70c2aa550c5a107c8ddaf3e696ec2c65caa6c21c683405bb5481efd6b220", 10),
    3: ("4-CLJ-24F-2.3.pdf", "7bae9aa65c601603dc9135486a3326b1235c12225d7ea9f0d45a4c46c5891b74", 7),
}
NO_ABSTRACT = {(2, 5), (2, 6), (2, 7)}


def get(url):
    response = requests.get(url, timeout=45)
    response.raise_for_status()
    return response.content


def issue_pdf(number, cache):
    filename, expected, _ = ISSUES[number]
    raw = (cache / filename).read_bytes() if cache else get(
        f"https://clj.ac/wp-content/uploads/2025/06/{filename}"
    )
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError(f"Issue {number} has changed. Verify its physical page boundaries before import.")
    return pymupdf.open(stream=BytesIO(raw), filetype="pdf")


def join_lines(text):
    # Remove line-wrap whitespace between Han characters or Chinese punctuation.
    normalized = re.sub(r"\s+", " ", text).strip()
    return re.sub(r"(?<=[\u3000-\u9fff\uff00-\uffef]) +(?=[\u3000-\u9fff\uff00-\uffef])", "", normalized)


def compare_print(text):
    return re.sub(r"[\s*\uf02a;；、]", "", text)


def extract_article(doc, start, end, title, authors, has_abstract):
    pages = []
    for number in range(start, end + 1):
        # All three issues start their printed page 1 on physical page 5.
        page = doc[number + 3]
        top = pymupdf.Rect(0, 0, page.rect.width, 62)
        body = pymupdf.Rect(0, 62, page.rect.width, page.rect.height)
        if not re.search(rf"(?<!\d){number}(?!\d)", page.get_text(clip=top)):
            raise ValueError(f"Printed page {number} missing for {title}")
        lines = page.get_text("text", clip=body, sort=True).splitlines()
        pages.append("\n".join(join_lines(line) for line in lines if line.strip()))
    front = pages[0]
    identity = compare_print(front.split("摘要", 1)[0][:400])
    if compare_print(title) not in identity or compare_print(authors) not in identity:
        raise ValueError(f"Title or byline mismatch at p.{start}: {title}")
    found = re.search(r"摘\s*要\s*[：:]\s*(.*?)\s*关\s*键\s*词\s*[：:]", front, re.S)
    if bool(found) != has_abstract:
        raise ValueError(f"Abstract boundary mismatch at p.{start}: {title}")
    if not has_abstract:
        return "", None
    content = "\n\n".join(pages)
    if "�" in content or any(not p for p in pages):
        raise ValueError(f"Unreadable PDF text within {title}")
    return join_lines(found[1]), content


def build(cache):
    archive = BeautifulSoup(get(ARCHIVE), "html.parser")
    docs = {issue: issue_pdf(issue, cache) for issue in ISSUES}
    articles = {}
    for row in archive.select("tr"):
        cells = row.find_all("td")
        if len(cells) != 6:
            continue
        link = cells[1].find("a", href=True)
        if not link:
            continue
        url = link["href"]
        path = re.fullmatch(r"https://clj\.ac/clj-2024-([1-3])-(\d+)/", url)
        if not path:
            continue
        issue, item = map(int, path.groups())
        page_from, page_to = map(int, cells[4].get_text(strip=True).split("-"))
        authors = cells[2].get_text(" ", strip=True)
        title = link.get_text(" ", strip=True)
        # The online index misattributes this article. The signed title page and
        # its contributor footnote independently name 王雷垠 (see README).
        if (issue, item) == (1, 2):
            authors = "王雷垠"
        summary, content = extract_article(
            docs[issue], page_from, page_to, title, authors, (issue, item) not in NO_ABSTRACT
        )
        doi_anchor = cells[1].select_one('a[href^="https://doi.org/"]')
        doi = doi_anchor["href"].split("doi.org/", 1)[1].lower() if doi_anchor else None
        if (issue, item) in articles:
            raise ValueError(f"Duplicate article {url}")
        articles[issue, item] = {
            "scholar_name": authors,
            "title": title,
            "source": f"华夏法学（2024年第{issue}期，第{page_from}–{page_to}页）",
            "publish_year": 2024,
            "language_code": "zh",
            "source_dataset": "China-Law-Journal-Archive",
            "source_item_id": doi or url,
            "source_url": url,
            "doi": doi,
            "license_name": "CC BY 4.0",
            "summary_kind": "author_abstract" if summary else "metadata_only",
            "summary": summary,
            "content": content,
        }
    expected = {(issue, i) for issue, (_, _, count) in ISSUES.items() for i in range(1, count + 1)}
    if set(articles) != expected:
        raise ValueError("Publisher's indexed articles differ from the 2024 snapshot.")
    return [articles[k] for k in sorted(articles)]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf-dir", type=Path, help="Optional local copies of the three pinned issues")
    parser.add_argument("--output", type=Path, default=Path(__file__).with_name("records.json"))
    args = parser.parse_args()
    rows = build(args.pdf_dir)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(rows)} papers; {sum(row['content'] is not None for row in rows)} with searchable text")
