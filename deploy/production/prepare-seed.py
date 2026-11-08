"""Prepare the production import directory from the same ordered datasets used for development."""
import hashlib
import re
import shutil
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
manifest = []
for src, name in re.findall(r"^\s*- \./([^:]+):/docker-entrypoint-initdb.d/([^:]+):ro$", (root / "docker-compose.yml").read_text(), re.M):
    source, dest = root / src, out / name
    if name == "04-data.sql":
        text, count = re.subn(r"-- ---------- 内置演示账号[^\n]*\nINSERT IGNORE INTO `user`[^;]+;\s*", "", source.read_text(), count=1)
        if count != 1:
            raise ValueError("Review the demonstration account seed before a production import")
        dest.write_text(text)
    elif not dest.exists() or not dest.samefile(source):
        shutil.copyfile(source, dest)
    with dest.open("rb") as data:
        digest = hashlib.sha256()
        while chunk := data.read(1024 * 1024):
            digest.update(chunk)
    manifest.append(f"{digest.hexdigest()}  {name}\n")
if not manifest:
    raise ValueError("No database initialization files found")
(out / "SHA256SUMS").write_text("".join(manifest))
