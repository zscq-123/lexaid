"""Read primary PDF text for the recent, openly licensed OSF preprints in this snapshot."""
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import re
import unicodedata

import pymupdf
import requests

def normalized_title(text):
    return "".join(c for c in unicodedata.normalize("NFKC", text).casefold() if c.isalnum())


def primary_pdf(item, cache, get):
    file_id = item["primary_file"]
    if not file_id:
        return None, "missingPrimaryFile"
    metadata = get(f"https://api.osf.io/v2/files/{file_id}/")["data"]
    if metadata["id"] != file_id:
        raise ValueError("OSF file identity changed")
    info = metadata["attributes"]
    if not info["name"].lower().endswith(".pdf") or info["size"] > 12 * 1024 * 1024:
        return None, "nonPdfOrOversized"
    expected_hash = info["extra"]["hashes"]["sha256"]
    url = metadata["links"]["download"]
    if not re.fullmatch(r"https://osf\.io/download/[a-f0-9]+/", url):
        raise ValueError(f"Unexpected OSF download URL {url}")
    dest = cache / f"{expected_hash}.pdf"
    if dest.is_file():
        data = dest.read_bytes()
    else:
        with requests.get(url, stream=True, timeout=(10, 45)) as response:
            response.raise_for_status()
            data = bytearray()
            for part in response.iter_content(65536):
                data.extend(part)
                if len(data) > 12 * 1024 * 1024:
                    return None, "nonPdfOrOversized"
        if hashlib.sha256(data).hexdigest() != expected_hash:
            raise ValueError(f"OSF digest mismatch for {file_id}")
        dest.write_bytes(data)
    if hashlib.sha256(data).hexdigest() != expected_hash:
        raise ValueError(f"Corrupt local PDF for {file_id}")
    with pymupdf.open(stream=data, filetype="pdf") as doc:
        if doc.is_encrypted or not 1 <= len(doc) <= 150:
            return None, "encryptedOrLong"
        pages = [p.get_text(sort=True).strip() for p in doc]
    if normalized_title(item["title"]) not in normalized_title(" ".join(pages[:2])[:16000]):
        return None, "titleNotMatched"
    text = "\n\n".join(pages)
    if min(map(len, pages)) < 40 or len(text) < 1200 or "\ufffd" in text or "\0" in text:
        return None, "insufficientText"
    return {"id": item["id"], "sha256": expected_hash, "file": url, "content": text}, None


def collect(items, cache, output, get, license_name):
    entries = [x for x in items if x["public"] and x["latest"] and not x["withdrawn"]
               and x["date"] and "2026-10-20" <= x["date"][:10] < "2026-11-08"
               and x["authors"] and all(x["authors"]) and license_name(x["licenseId"])]
    texts, skips = {}, Counter()
    with ThreadPoolExecutor(max_workers=3) as pool:
        for record, reason in pool.map(lambda x: primary_pdf(x, cache, get), entries):
            if reason:
                skips[reason] += 1
            else:
                texts[record["id"]] = record
    evidence = {k: {f: v[f] for f in ("file", "sha256")} for k, v in sorted(texts.items())}
    (output / "text-sources.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print("Recent PDFs:", len(entries), "eligible;", len(texts), "with text;", dict(skips))
    return {k: v["content"] for k, v in texts.items()}
