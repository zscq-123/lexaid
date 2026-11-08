"""Build the LexAid scholarly literature import from the packaged source snapshots."""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1] / "datasets/legal-papers"
# Earlier inputs take priority for shared DOIs. Put journal-level records
# before bibliographic indexes so an index entry cannot displace a paper.
EXTRA_SNAPSHOTS = ("clj/records.json", "journals/records.json.gz",
                   "yale/records.json.gz", "nctu/records.json.gz", "openalex/records.json.gz")
COLUMNS = ("scholar_name", "title", "summary", "content", "source", "publish_year",
           "language_code", "source_dataset", "source_item_id", "source_url", "doi",
           "license_name", "summary_kind")


def read_json(path):
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def validate(rows):
    seen_ids, seen_dois = set(), set()
    for row in rows:
        for field in ("scholar_name", "title", "source", "language_code", "source_dataset",
                      "source_item_id", "source_url"):
            if not isinstance(row.get(field), str) or not row[field].strip():
                raise ValueError(f"Missing {field}: {row.get('source_item_id')}")
        key = row["source_dataset"], row["source_item_id"]
        doi = row.get("doi")
        if key in seen_ids or (doi and doi in seen_dois):
            raise ValueError(f"Duplicate paper: {key}, {doi}")
        if doi and (doi != doi.strip().lower() or not doi.startswith("10.") or "/" not in doi):
            raise ValueError(f"Invalid canonical DOI: {doi}")
        seen_ids.add(key)
        seen_dois.add(doi)
        for field, limit in {"source": 500, "source_dataset": 120, "source_item_id": 512,
                             "doi": 512, "license_name": 120, "language_code": 16}.items():
            if len(row.get(field) or "") > limit:
                raise ValueError(f"{key}: {field} exceeds VARCHAR({limit})")
        for field in ("title", "scholar_name", "source_url"):
            if len(row[field].encode("utf-8")) > 65535:
                raise ValueError(f"{key}: {field} exceeds MySQL TEXT")
        source_url = urlsplit(row["source_url"])
        if source_url.scheme != "https" or not source_url.hostname or source_url.username or source_url.password:
            raise ValueError(f"Invalid source URL for {key}")
        if not isinstance(row["publish_year"], int) or not 1500 <= row["publish_year"] <= 2100:
            raise ValueError(f"Missing publication year for {key}")
        expected_kind = "author_abstract" if row["summary"].strip() else "metadata_only"
        if row["summary_kind"] != expected_kind:
            raise ValueError(f"Abstract label or content mismatch for {key}")
        if (row["summary"] or row.get("content")) and not row.get("license_name"):
            raise ValueError(f"Missing license for {key}")
        for field in ("summary", "content"):
            if len((row.get(field) or "").encode("utf-8")) >= 2**24:
                raise ValueError(f"{key} {field} exceeds MySQL MEDIUMTEXT")


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    # Hex literals preserve quotes, control characters and Unicode in every MySQL sql_mode.
    return "X'" + value.encode("utf-8").hex() + "'"


def build(catalog_file, extra_json, texts_file, output_dir):
    catalog = read_json(catalog_file)
    rows = list(catalog["records"])
    validate(rows)
    seen_dois = {r["doi"] for r in rows if r.get("doi")}
    inputs = {}
    for path in extra_json:
        candidates = read_json(path)
        validate(candidates)
        unique = [r for r in candidates if not r.get("doi") or r["doi"] not in seen_dois]
        seen_dois.update(r["doi"] for r in unique if r.get("doi"))
        rows.extend(unique)
        inputs[snapshot_name(path)] = {"upstreamRows": len(candidates), "added": len(unique),
                                       "duplicateDois": len(candidates) - len(unique)}
    if texts_file:
        texts = read_json(texts_file)
        eligible = {r["doi"]: r for r in rows if r["source"] == "Laws" and r.get("license_name") == "CC-BY-4.0"}
        for doi, text in texts.items():
            if doi not in eligible or not isinstance(text, str) or not text.strip():
                raise ValueError("Unmatched or empty licensed text for " + doi)
            eligible[doi]["content"] = text
    validate(rows)
    rows.sort(key=lambda r: (r["source_dataset"], r["source_item_id"]))
    output_dir.mkdir(parents=True, exist_ok=True)
    sql_path = output_dir / "lexaid-papers.sql.gz"
    updates = ", ".join(f"{c} = incoming.{c}" for c in COLUMNS)
    counts = {}
    for row in rows:
        journal = "华夏法学" if row["source_dataset"] == "China-Law-Journal-Archive" else row["source"]
        group = counts.setdefault(journal, Counter(records=0, authorAbstracts=0, fullTexts=0))
        group["records"] += 1
        group["authorAbstracts"] += bool(row.get("summary"))
        group["fullTexts"] += bool(row.get("content"))
    with sql_path.open("wb") as dest:
        with gzip.GzipFile(fileobj=dest, filename="", mode="wb", mtime=0) as stream:
            stream.write(b"SET NAMES utf8mb4;\nSTART TRANSACTION;\n")
            for row in rows:
                values = ", ".join(sql_value(row.get(c)) for c in COLUMNS)
                query = (f"INSERT INTO scholar_viewpoint ({', '.join(COLUMNS)}) VALUES ({values}) "
                         f"AS incoming ON DUPLICATE KEY UPDATE {updates};\n")
                stream.write(query.encode("ascii"))
            stream.write(b"COMMIT;\n")
    manifest = dict(totalRecords=len(rows), authorAbstracts=sum(bool(r["summary"]) for r in rows),
                    fullTexts=sum(bool(r.get("content")) for r in rows), journals=counts,
                    languages=dict(Counter(r["language_code"] for r in rows)),
                    sources=dict(Counter(r["source_dataset"] for r in rows)), extraSnapshots=inputs,
                    crossrefCandidates=catalog["candidateCounts"], crossrefExcluded=catalog["excluded"],
                    inputs={snapshot_name(p): hashlib.sha256(p.read_bytes()).hexdigest()
                            for p in [catalog_file, *extra_json, *([texts_file] if texts_file else [])]},
                    output=sql_path.name, compressedBytes=sql_path.stat().st_size,
                    sha256=hashlib.sha256(sql_path.read_bytes()).hexdigest())
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2)
                                              + "\n", encoding="utf-8")
    return manifest


def snapshot_name(path):
    return str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else str(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=ROOT / "crossref-catalog.json.gz")
    parser.add_argument("--extra-json", type=Path, action="append")
    parser.add_argument("--laws-texts", type=Path)
    parser.add_argument("--output-dir", type=Path, default=ROOT)
    args = parser.parse_args()
    extra = args.extra_json
    if extra is None:
        extra = [ROOT / name for name in EXTRA_SNAPSHOTS]
    texts = args.laws_texts
    if texts is None and (ROOT / "laws-texts.json.gz").is_file():
        texts = ROOT / "laws-texts.json.gz"
    manifest = build(args.catalog, extra, texts, args.output_dir)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
