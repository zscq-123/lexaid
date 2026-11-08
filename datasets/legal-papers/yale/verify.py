"""Check the locally bundled Yale records, without any network access."""
import gzip
import hashlib
import json
from pathlib import Path


here = Path(__file__).resolve().parent
archive = here / "records.json.gz"
expected = "d281a29eb87a795c1c5b915a7a207d358d706e1d4f260ffc94f9a3bc4f0b9a19"
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected
with gzip.open(archive, "rt", encoding="utf-8") as stream:
    rows = json.load(stream)
assert len(rows) == len({r["doi"] for r in rows}) == len({r["source_item_id"] for r in rows}) == 1855
assert all(r["scholar_name"] and r["title"] and r["doi"] for r in rows)
assert all(r["source_url"].rstrip("/") == "https://osf.io/preprints/lawarchive/" + r["source_item_id"]
           for r in rows)
assert sum(bool(r["summary"]) for r in rows) == 1098
assert all(r["license_name"] in {"CC-BY-4.0", "CC0-1.0"} for r in rows if r["summary"] or r["content"])
sources = json.loads((here / "text-sources.json").read_text(encoding="utf-8"))
assert {r["source_item_id"] for r in rows if r["content"]} == set(sources)
assert len(sources) == 33
print("Yale: verified 1,855 preprints, 1,098 abstracts, 33 PDF texts")
