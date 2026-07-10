import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


class BuildUpdateTest(unittest.TestCase):
    def test_builds_unified_translation_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            output = root / "site"
            language = source / "resources" / "Example Mod[example]" / "lang" / "zh_CN.lang"
            language.parent.mkdir(parents=True)
            language.write_text("example.key=示例\n", encoding="utf-8")
            second_language = source / "resources" / "Example Addon[example]" / "lang" / "zh_CN.lang"
            second_language.parent.mkdir(parents=True)
            second_language.write_text("addon.key=扩展\n", encoding="utf-8")
            quest = source / "config" / "txloader" / "load" / "betterquesting" / "lang" / "zh_CN.lang"
            quest.parent.mkdir(parents=True)
            quest.write_text("quest.key=任务\n", encoding="utf-8")

            subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).parents[1] / "tools" / "build_update.py"),
                    "--source", str(source),
                    "--output", str(output),
                    "--release", "2.8.4-cn.1",
                    "--pack-version", "2.8.4",
                    "--base-url", "https://example.invalid/NHTranslationUpdate",
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(2, manifest["schemaVersion"])
            self.assertEqual(["2.8.4"], manifest["packVersions"])
            self.assertEqual(1, len(manifest["artifacts"]))
            self.assertEqual("translation", manifest["artifacts"][0]["kind"])

            translation_zip = output / "releases" / "2.8.4-cn.1" / "gtnh-zh-cn-translation.zip"
            with zipfile.ZipFile(translation_zip) as archive:
                names = archive.namelist()
                # Standard resource pack entries
                self.assertIn("assets/example/lang/zh_CN.lang", names)
                merged = archive.read("assets/example/lang/zh_CN.lang").decode("utf-8")
                self.assertIn("example.key=示例", merged)
                self.assertIn("addon.key=扩展", merged)
                # TX Loader entries
                self.assertIn("txloader/betterquesting/lang/zh_CN.lang", names)
                self.assertEqual(
                    "quest.key=任务\n",
                    archive.read("txloader/betterquesting/lang/zh_CN.lang").decode("utf-8"),
                )


if __name__ == "__main__":
    unittest.main()
