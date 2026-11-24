#!/usr/bin/env python3
"""
Build remote static vocabulary packs (manifest.json + packs/*.json)
directly from docs/储能行业英文词汇表.xlsx using only Python stdlib.

Usage:
    python vocab-repo/scripts/build_vocab.py
    python vocab-repo/scripts/build_vocab.py --input docs/储能行业英文词汇表.xlsx --version 2026.11.23.1
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
CJK_RE = re.compile(r"[\u4e00-\u9fff]")
PAREN_RE = re.compile(r"\(([^()]+)\)")
HEADER_COUNT_RE = re.compile(r"[（(]\s*\d+\s*条?\s*[）)]\s*$")
ABBR_TOKEN_RE = re.compile(r"^[A-Z0-9][A-Za-z0-9&\-/]{1,10}$")


def read_xlsx_rows(xlsx_path: Path) -> list[dict[str, str]]:
    with zipfile.ZipFile(xlsx_path, "r") as zf:
        shared: list[str] = []
        if "xl/sharedStrings.xml" in zf.namelist():
            ss_root = ET.fromstring(zf.read("xl/sharedStrings.xml"))
            for si in ss_root.findall("s:si", NS):
                shared.append(
                    "".join(
                        t.text or ""
                        for t in si.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")
                    )
                )

        wb_root = ET.fromstring(zf.read("xl/workbook.xml"))
        rels_root = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
        rel_map = {r.attrib["Id"]: r.attrib["Target"] for r in rels_root}
        first_sheet = wb_root.find(".//s:sheet", NS)
        if first_sheet is None:
            raise ValueError("No worksheet found in xlsx")
        rid = first_sheet.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]
        target = "xl/" + rel_map[rid].lstrip("/")
        sheet_root = ET.fromstring(zf.read(target))

        parsed_rows: list[dict[str, str]] = []
        for row in sheet_root.findall(".//s:sheetData/s:row", NS):
            cells: dict[str, str] = {}
            for cell in row.findall("s:c", NS):
                col = "".join(ch for ch in cell.attrib.get("r", "") if ch.isalpha())
                cell_type = cell.attrib.get("t")
                v_el = cell.find("s:v", NS)
                if v_el is None:
                    is_el = cell.find("s:is", NS)
                    val = (
                        "".join(
                            t.text or ""
                            for t in is_el.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")
                        )
                        if is_el is not None
                        else ""
                    )
                elif cell_type == "s":
                    val = shared[int(v_el.text or "0")]
                else:
                    val = v_el.text or ""
                cells[col] = val.strip()
            parsed_rows.append(cells)
        return parsed_rows


def extract_abbreviations(en: str) -> list[str]:
    abbrs: list[str] = []
    # 1. Parenthetical abbreviations such as (BESS), (C&I), (Li-ion), (DoD), (Na-S)
    for match in PAREN_RE.findall(en):
        candidate = match.strip()
        if CJK_RE.search(candidate):
            continue
        if len(candidate) <= 12 and " " not in candidate:
            if candidate not in abbrs:
                abbrs.append(candidate)

    # 2. Leading uppercase abbreviations followed by parenthetical expansion, e.g. SCADA (...), NMC/NCA (...)
    prefix = en.split("(", 1)[0].strip()
    for token in re.split(r"[\s/]+", prefix):
        token_clean = token.strip()
        if (
            token_clean
            and any(ch.isupper() for ch in token_clean)
            and ABBR_TOKEN_RE.match(token_clean)
            and (token_clean.isupper() or "-" in token_clean or "&" in token_clean)
            and len(token_clean) >= 2
            and token_clean not in {"IEC", "DC", "AC"}
        ):
            if token_clean not in abbrs:
                abbrs.append(token_clean)
    return abbrs


def build_tts_text(en: str) -> str:
    # Remove Chinese parenthetical annotations like "(工业通信协议)" and short abbreviations inside parens
    def replace_paren(match: re.Match[str]) -> str:
        inner = match.group(1).strip()
        if CJK_RE.search(inner):
            return ""
        # Keep full-word expansions like "(Supervisory Control and Data Acquisition)" only when prefix is an acronym
        if len(inner) <= 12 and " " not in inner:
            return ""
        return f", {inner}"

    cleaned = PAREN_RE.sub(replace_paren, en)
    # Replace spaced slash synonyms "A / B" with natural pause ", "
    cleaned = re.sub(r"\s+/\s+", ", ", cleaned)
    cleaned = re.sub(r"\s+,", ",", cleaned)
    cleaned = re.sub(r"\s{2,}", " ", cleaned).strip(" ,")
    return cleaned or en


def stable_word_id(en: str) -> str:
    # Normalize term to keep ID stable across Excel re-numbering
    base = PAREN_RE.sub("", en).lower()
    base = re.sub(r"[^a-z0-9]+", "_", base).strip("_")
    digest = hashlib.sha1(en.strip().lower().encode("utf-8")).hexdigest()[:8]
    slug = base[:24].strip("_") or "term"
    return f"ess_{slug}_{digest}"


def slugify_category(name: str, index: int) -> str:
    digest = hashlib.sha1(name.encode("utf-8")).hexdigest()[:6]
    return f"category_{index:02d}_{digest}"


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_previous_manifest(manifest_path: Path) -> dict:
    if not manifest_path.exists():
        return {}
    try:
        return json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception:
        return {}


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Convert ESS Excel vocabulary to static JSON packs.")
    parser.add_argument(
        "--input",
        type=Path,
        default=repo_root / "docs" / "储能行业英文词汇表.xlsx",
        help="Path to source Excel file.",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=repo_root / "vocab-repo" / "config" / "categories.json",
        help="Category mapping config JSON.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=repo_root / "vocab-repo" / "dist",
        help="Output directory for manifest.json and packs/*.json.",
    )
    parser.add_argument(
        "--version",
        type=str,
        default="",
        help="Optional datasetVersion override (e.g. 2026.11.23.1).",
    )
    args = parser.parse_args()

    if not args.input.exists():
        print(f"ERROR: Excel file not found: {args.input}", file=sys.stderr)
        return 1

    cat_cfg = {}
    if args.config.exists():
        cat_cfg = json.loads(args.config.read_text(encoding="utf-8")).get("categories", {})

    rows = read_xlsx_rows(args.input)
    packs: list[dict] = []
    current_pack: dict | None = None
    seen_ids: set[str] = set()

    for row in rows:
        col_a = row.get("A", "")
        col_b = row.get("B", "")
        col_c = row.get("C", "")
        col_d = row.get("D", "")

        # Skip title row & table header row
        if not col_a or col_a == "序号":
            continue

        # Category header row: starts with "▌" or has no Chinese/English columns
        if col_a.startswith("▌"):
            raw_name = col_a.lstrip("▌").strip()
            clean_name = HEADER_COUNT_RE.sub("", raw_name).strip()
            meta = cat_cfg.get(clean_name, {})
            pack_id = meta.get("id") or slugify_category(clean_name, len(packs) + 1)
            description = meta.get("description", f"{clean_name}相关储能专业英文术语")
            current_pack = {
                "id": pack_id,
                "name": clean_name,
                "description": description,
                "order": len(packs) + 1,
                "words": [],
            }
            packs.append(current_pack)
            continue

        # Vocabulary data row
        if col_a.isdigit() and col_b and col_c:
            if current_pack is None:
                current_pack = {
                    "id": "general",
                    "name": "通用储能词汇",
                    "description": "储能行业通用专业术语",
                    "order": 1,
                    "words": [],
                }
                packs.append(current_pack)

            word_id = stable_word_id(col_c)
            if word_id in seen_ids:
                word_id = f"{word_id}_{col_a}"
            seen_ids.add(word_id)

            # Optional future columns E=例句英文, F=例句中文, G=备注, H=音频URL
            example_en = row.get("E", "") or None
            example_zh = row.get("F", "") or None
            note = row.get("G", "") or None
            audio_url = row.get("H", "") or None

            word_item = {
                "id": word_id,
                "seq": int(col_a),
                "zh": col_b,
                "en": col_c,
                "ttsText": build_tts_text(col_c),
                "abbreviations": extract_abbreviations(col_c),
                "ipa": col_d,
                "exampleEn": example_en,
                "exampleZh": example_zh,
                "note": note,
                "audioUrl": audio_url,
            }
            current_pack["words"].append(word_item)

    if not packs:
        print("ERROR: No vocabulary packs parsed from Excel.", file=sys.stderr)
        return 1

    dist_dir: Path = args.output
    packs_dir = dist_dir / "packs"
    packs_dir.mkdir(parents=True, exist_ok=True)

    prev_manifest = load_previous_manifest(dist_dir / "manifest.json")
    prev_packs = {p["id"]: p for p in prev_manifest.get("packs", [])}

    now_iso = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(timespec="seconds")
    today_tag = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).strftime("%Y.%m.%d")

    manifest_packs: list[dict] = []
    total_words = 0

    for pack in packs:
        pack_id = pack["id"]
        words = pack["words"]
        total_words += len(words)

        prev_info = prev_packs.get(pack_id)
        prev_version = int(prev_info.get("version", 1)) if prev_info else 1

        # Build canonical content without volatile version first to detect changes cleanly
        content_for_diff = json.dumps(
            {
                "packId": pack_id,
                "packName": pack["name"],
                "description": pack["description"],
                "words": words,
            },
            ensure_ascii=False,
            indent=2,
        ).encode("utf-8")
        content_hash = sha256_bytes(content_for_diff)

        if prev_info and prev_info.get("contentSha256") == content_hash:
            pack_version = prev_version
        elif prev_info:
            pack_version = prev_version + 1
        else:
            pack_version = 1

        pack_payload = {
            "packId": pack_id,
            "packName": pack["name"],
            "description": pack["description"],
            "version": pack_version,
            "words": words,
        }
        pack_bytes = (json.dumps(pack_payload, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        pack_file = packs_dir / f"{pack_id}.json"
        pack_file.write_bytes(pack_bytes)

        manifest_packs.append(
            {
                "id": pack_id,
                "name": pack["name"],
                "description": pack["description"],
                "order": pack["order"],
                "wordCount": len(words),
                "version": pack_version,
                "sha256": sha256_bytes(pack_bytes),
                "contentSha256": content_hash,
                "path": f"packs/{pack_id}.json",
            }
        )

    # Remove stale pack json files that no longer exist in Excel
    valid_files = {f"{p['id']}.json" for p in manifest_packs}
    for old_file in packs_dir.glob("*.json"):
        if old_file.name not in valid_files:
            old_file.unlink()

    if args.version:
        dataset_version = args.version
    else:
        prev_ver = str(prev_manifest.get("datasetVersion", ""))
        if prev_ver.startswith(today_tag + "."):
            try:
                rev = int(prev_ver.rsplit(".", 1)[1])
            except ValueError:
                rev = 1
            # Bump rev only if any pack hash changed
            prev_hashes = {p["id"]: p.get("sha256") for p in prev_manifest.get("packs", [])}
            curr_hashes = {p["id"]: p["sha256"] for p in manifest_packs}
            dataset_version = f"{today_tag}.{rev}" if prev_hashes == curr_hashes else f"{today_tag}.{rev + 1}"
        else:
            dataset_version = f"{today_tag}.1"

    manifest = {
        "schemaVersion": 1,
        "datasetVersion": dataset_version,
        "updatedAt": now_iso,
        "minAppVersionCode": 1,
        "totalWords": total_words,
        "packs": manifest_packs,
    }
    manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    (dist_dir / "manifest.json").write_bytes(manifest_bytes)

    print(f"SUCCESS: Generated {len(manifest_packs)} packs, {total_words} words (datasetVersion={dataset_version})")
    for p in manifest_packs:
        print(f"  - [{p['order']}] {p['name']} ({p['id']}): {p['wordCount']} words, v{p['version']}, sha256={p['sha256'][:12]}...")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
