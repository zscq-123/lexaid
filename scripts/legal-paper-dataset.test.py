import gzip
import importlib.util
import hashlib
import json
import re
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("build-legal-paper-datasets.py")
SPEC = importlib.util.spec_from_file_location("paper_builder", SCRIPT)
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)
ROOT = BUILDER.ROOT


class PaperDatasetTest(unittest.TestCase):
    def test_rebuild_matches_shipped_manifest_and_sql(self):
        with tempfile.TemporaryDirectory() as temp:
            result = BUILDER.build(ROOT / "crossref-catalog.json.gz", [ROOT / name for name in BUILDER.EXTRA_SNAPSHOTS],
                                   ROOT / "laws-texts.json.gz", Path(temp))
            stored = BUILDER.read_json(ROOT / "manifest.json")
            artifact = (ROOT / stored["output"]).read_bytes()
            self.assertEqual(hashlib.sha256(artifact).hexdigest(), stored["sha256"])
            self.assertEqual(len(artifact), stored["compressedBytes"])
            # zlib implementations may compress the same bytes differently across hosts.
            for field in ("sha256", "compressedBytes"):
                result.pop(field)
                stored.pop(field)
            self.assertEqual(result, stored)
            with gzip.open(ROOT / "lexaid-papers.sql.gz") as expected:
                with gzip.open(Path(temp) / "lexaid-papers.sql.gz") as rebuilt:
                    self.assertEqual(expected.read(), rebuilt.read())
            with gzip.open(ROOT / "lexaid-papers.sql.gz") as records:
                inserts = [line for line in records if line.startswith(b"INSERT")]
            self.assertEqual(len(inserts), result["totalRecords"])
            self.assertEqual(len(inserts), len(set(inserts)))

    def test_no_abstract_invented_for_metadata_only(self):
        rows = BUILDER.read_json(ROOT / "crossref-catalog.json.gz")["records"]
        for r in rows:
            if r["summary_kind"] == "metadata_only":
                self.assertEqual(r["summary"], "")
            if r["language_code"] == "zh-CN":
                self.assertRegex(r["title"], r"[\u3400-\u9fff]")
        self.assertTrue(any(r["summary_kind"] == "metadata_only" for r in rows))

    def test_duplicate_doi_across_providers_rejected(self):
        row = BUILDER.read_json(ROOT / "crossref-catalog.json.gz")["records"][0]
        with self.assertRaisesRegex(ValueError, "Duplicate paper"):
            BUILDER.validate([row, {**row, "source_dataset": "other-source"}])

    def test_same_doi_from_an_index_preserves_primary_record(self):
        row = BUILDER.read_json(ROOT / "crossref-catalog.json.gz")["records"][0]
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            catalog_file, index_file = path / "catalog.json", path / "index.json"
            catalog_file.write_text(json.dumps({"records": [row], "candidateCounts": {}, "excluded": []}))
            index_file.write_text(json.dumps([{**row, "title": "Alternate title", "source_dataset": "index"}]))
            result = BUILDER.build(catalog_file, [index_file], None, path)
            self.assertEqual(result["totalRecords"], 1)
            self.assertEqual(result["extraSnapshots"][str(index_file)]["duplicateDois"], 1)
            self.assertEqual(result["sources"], {row["source_dataset"]: 1})
            with gzip.open(path / result["output"]) as f:
                sql = f.read().decode("ascii")
            self.assertIn(BUILDER.sql_value(row["title"]), sql)
            self.assertNotIn(BUILDER.sql_value("Alternate title"), sql)

    def test_sql_roundtrip_utf8_and_null(self):
        value = "O'Brien\\张\n\x00 & <"
        sql = BUILDER.sql_value(value)
        self.assertTrue(re.fullmatch(r"X'[a-f0-9]+'", sql))
        self.assertEqual(bytes.fromhex(sql[2:-1]).decode("utf8"), value)
        self.assertEqual(BUILDER.sql_value(None), "NULL")


if __name__ == "__main__":
    unittest.main()
