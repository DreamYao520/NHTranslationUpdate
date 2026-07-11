import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "tools" / "build_update.py"


def run_build(source: Path, output: Path, release: str, version: str, *extra: str) -> None:
    subprocess.run(
        [
            sys.executable,
            str(SCRIPT),
            "--source",
            str(source),
            "--output",
            str(output),
            "--release",
            release,
            "--pack-version",
            version,
            "--base-url",
            output.as_uri(),
            *extra,
        ],
        check=True,
        capture_output=True,
        text=True,
    )


class BuildUpdateTest(unittest.TestCase):
    def test_flattens_resources_in_txloader_priority_order(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            output = root / "site"
            base = source / "resources" / "Example Mod[example]" / "lang" / "zh_CN.lang"
            load = source / "config" / "txloader" / "load" / "example" / "lang" / "zh_CN.lang"
            forced = source / "config" / "txloader" / "forceload" / "example" / "lang" / "zh_CN.lang"
            for path, content in (
                (base, "same.key=基础\nbase.key=保留\n"),
                (load, "same.key=加载层\nload.key=保留\n"),
                (forced, "same.key=强制层\nforce.key=保留\n"),
            ):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            (source / "GregTech.lang").write_text("languagefile {\n S:key=格雷科技\n}\n", encoding="utf-8")

            run_build(source, output, "2.8.4-cn.1", "2.8.4")
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(3, manifest["schemaVersion"])
            self.assertEqual({"2.8.4"}, set(manifest["packs"]))
            self.assertEqual("2.8.4-cn.1", manifest["packs"]["2.8.4"]["release"])

            archive_path = output / "releases" / "2.8.4-cn.1" / "gtnh-zh-cn-translation.zip"
            with zipfile.ZipFile(archive_path) as archive:
                names = archive.namelist()
                self.assertIn("assets/example/lang/zh_CN.lang", names)
                self.assertNotIn("txloader/example/lang/zh_CN.lang", names)
                merged = archive.read("assets/example/lang/zh_CN.lang").decode("utf-8")
                self.assertLess(merged.index("same.key=基础"), merged.index("same.key=加载层"))
                self.assertLess(merged.index("same.key=加载层"), merged.index("same.key=强制层"))
                self.assertIn("base.key=保留", merged)
                self.assertIn("load.key=保留", merged)
                self.assertIn("force.key=保留", merged)
                self.assertIn("install/config/GregTech_zh_CN.lang", names)

    def test_preserves_previous_versions_in_schema_v3_catalog(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            language = source / "resources" / "Example[example]" / "lang" / "zh_CN.lang"
            language.parent.mkdir(parents=True)
            language.write_text("key=第一版\n", encoding="utf-8")

            old_site = root / "old-site"
            run_build(source, old_site, "2.8.4-cn.1", "2.8.4")
            language.write_text("key=第二版\n", encoding="utf-8")

            new_site = root / "new-site"
            run_build(
                source,
                new_site,
                "2.9.0-beta-2-cn.1",
                "2.9.0-beta-2",
                "--existing-site-url",
                old_site.as_uri(),
            )
            manifest = json.loads((new_site / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual({"2.8.4", "2.9.0-beta-2"}, set(manifest["packs"]))
            self.assertTrue(
                (new_site / "releases" / "2.8.4-cn.1" / "gtnh-zh-cn-translation.zip").is_file()
            )


if __name__ == "__main__":
    unittest.main()
