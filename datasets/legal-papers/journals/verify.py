"""Offline integrity check for the international journal snapshot."""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path


def verify(directory):
    path = directory / "records.json.gz"
    evidence = json.loads((directory / "provenance.json").read_text())
    assert hashlib.sha256(path.read_bytes()).hexdigest() == evidence["sha256"]
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        rows = json.load(stream)
    by_doi = {r["doi"]: r for r in rows}
    assert len(by_doi) == len(rows) == evidence["records"]
    proofs = {r["doi"]: r for r in evidence["sources"]}
    assert by_doi.keys() == proofs.keys()
    for doi, row in by_doi.items():
        proof = proofs[doi]
        assert row["source_item_id"] == doi == doi.lower()
        assert row["publish_year"] in (2024, 2025)
        assert row["language_code"] == "en"
        assert row["scholar_name"].strip() and row["title"].strip()
        assert row["source_url"].startswith(("https://doi.org/", "https://utrechtjournal.org/article/view/",
                                             "https://tilburglawreview.com/articles/"))
        assert proof["crossrefRegistered"] or proof["doajId"]
        assert bool(row["summary"]) == (row["summary_kind"] == "author_abstract")
        if row["summary"] or row["content"]:
            assert row["license_name"] == "CC-BY-4.0"
            assert proof["crossrefCcBy4"] or proof["publisherEvidence"]
        if row["content"]:
            assert proof["publisherEvidence"] and len(row["content"].strip()) > 100
    counts = Counter(row["source"] for row in rows)
    for journal, stats in evidence["journals"].items():
        assert counts[journal] == stats["records"]
        assert stats["bodyTexts"] == sum(bool(row["content"]) for row in rows if row["source"] == journal)
    assert len(rows) + len(evidence["excluded"]) == sum(x["candidates"] for x in evidence["journals"].values())
    print(f"Verified {len(rows)} distinct papers across {len(counts)} legal journals.")


if __name__ == "__main__":
    verify(Path(__file__).resolve().parent)
