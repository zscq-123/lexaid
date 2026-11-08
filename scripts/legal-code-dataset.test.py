import unittest
import json
from tempfile import TemporaryDirectory
from xml.etree import ElementTree as ET

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path


SCRIPT = Path(__file__).with_name("build-legal-code-datasets.py")
SPEC = spec_from_file_location("legal_dataset_builder", SCRIPT)
MODULE = module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class LegalDatasetBuilderTest(unittest.TestCase):
    def test_sql_string_escapes_mysql_control_characters(self):
        self.assertEqual(MODULE.sql_string("a'b\\c\n"), "'a''b\\\\c\\n'")

    def test_eu_category_uses_celex_document_type(self):
        self.assertEqual(MODULE.eu_category("32016R0679"), "欧盟条例")
        self.assertEqual(MODULE.eu_category("32024L1234"), "欧盟指令")

    def test_us_section_excludes_editorial_notes(self):
        xml = f"""
        <section xmlns="{MODULE.USLM_NAMESPACE}" identifier="/us/usc/t1/s1">
          <num value="1">§ 1.</num><heading>Words denoting number</heading>
          <content>Visible law text.</content><notes>Editorial material.</notes>
        </section>
        """
        content = MODULE.us_section_content(ET.fromstring(xml))
        self.assertIn("Visible law text", content)
        self.assertNotIn("Editorial material", content)

    def test_us_duplicate_official_identifiers_get_distinct_source_keys(self):
        xml = f"""
        <uscDoc xmlns="{MODULE.USLM_NAMESPACE}">
          <meta><docNumber>1</docNumber></meta>
          <title><heading>General Provisions</heading>
            <section identifier="/us/usc/t1/s1"><num value="1">§ 1.</num><content>First.</content></section>
            <section identifier="/us/usc/t1/s1"><num value="1">§ 1.</num><content>Second.</content></section>
          </title>
        </uscDoc>
        """
        with TemporaryDirectory() as directory:
            path = Path(directory) / "usc01.xml"
            path.write_text(xml, encoding="utf-8")
            rows = list(MODULE.us_rows(Path(directory), "test"))
        self.assertEqual(len(rows), 2)
        self.assertNotEqual(rows[0]["source_item_id"], rows[1]["source_item_id"])

    def test_committed_artifacts_match_manifest_checksums(self):
        repository = SCRIPT.parent.parent
        manifest = json.loads((repository / "datasets/legal-codes/manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(
            manifest["totalRecords"],
            sum(dataset["records"] for dataset in manifest["datasets"].values()),
        )
        for dataset in manifest["datasets"].values():
            artifact = repository / dataset["output"]
            self.assertTrue(artifact.is_file(), artifact)
            self.assertEqual(MODULE.file_sha256(artifact), dataset["sha256"])


if __name__ == "__main__":
    unittest.main()
