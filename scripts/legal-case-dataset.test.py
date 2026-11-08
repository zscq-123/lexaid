import json
import unittest
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path


SCRIPT = Path(__file__).with_name("build-legal-case-datasets.py")
SPEC = spec_from_file_location("legal_case_dataset_builder", SCRIPT)
MODULE = module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class LegalCaseDatasetBuilderTest(unittest.TestCase):
    def test_sql_string_escapes_mysql_controls(self):
        self.assertEqual(MODULE.sql_string("a'b\\c\n"), "'a''b\\\\c\\n'")

    def test_chinese_metadata_extractors(self):
        text = "北京市第一中级人民法院 刑事判决书 （2020）京01刑初9号"
        self.assertEqual(MODULE.chinese_court(text), "北京市第一中级人民法院")
        self.assertEqual(MODULE.chinese_case_number(text), "（2020）京01刑初9号")
        self.assertEqual(MODULE.chinese_procedure("张某二审刑事判决书"), "二审")

    def test_joined_sections_preserves_headings(self):
        text = MODULE.joined_sections([("裁判要点", ["第一点", "第二点"]), ("空白", [])])
        self.assertEqual(text, "【裁判要点】\n第一点\n第二点")

    def test_committed_artifacts_match_manifest_checksums(self):
        repository = SCRIPT.parent.parent
        manifest = json.loads((repository / "datasets/legal-cases/manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(
            manifest["totalRecords"],
            sum(dataset["records"] for dataset in manifest["datasets"].values()),
        )
        for dataset in manifest["datasets"].values():
            artifact = repository / dataset["output"]
            self.assertTrue(artifact.is_file(), artifact)
            self.assertLess(artifact.stat().st_size, 100_000_000, artifact)
            self.assertEqual(MODULE.file_sha256(artifact), dataset["sha256"])


if __name__ == "__main__":
    unittest.main()
