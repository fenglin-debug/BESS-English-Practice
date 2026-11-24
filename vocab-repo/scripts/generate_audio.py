#!/usr/bin/env python3
"""
Generate MP3 pronunciations for all ESS terms in vocab-repo/dist/packs/*.json.

Supports:
  - edge  : Microsoft Edge Neural TTS (edge-tts), optional --no-proxy to bypass local proxy
  - winrt : 100% offline Windows OneCore SpeechSynthesizer (Microsoft Mark) + ffmpeg 48k MP3
  - auto  : Try edge first; if blocked/rejected, fall back to offline winrt + ffmpeg

Usage:
    python vocab-repo/scripts/generate_audio.py --engine edge --no-proxy --limit 1
    python vocab-repo/scripts/generate_audio.py --engine winrt
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import edge_tts

PROXY_ENV_VARS = (
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "http_proxy",
    "https_proxy",
    "ALL_PROXY",
    "all_proxy",
)

WINRT_PS_SCRIPT = r"""
param(
    [Parameter(Mandatory=$true)][string]$JobsJsonPath,
    [string]$PreferredVoice = "Microsoft Mark"
)
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Runtime.WindowsRuntime
[Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType=WindowsRuntime] | Out-Null
[Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await-WinRtOp($WinRtTask, $ResultType) {
    $netTask = $asTaskGeneric.MakeGenericMethod($ResultType).Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    return $netTask.Result
}

$synth = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
$voices = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices
$chosenVoice = $voices | Where-Object { $_.DisplayName -eq $PreferredVoice } | Select-Object -First 1
if (-not $chosenVoice) {
    $chosenVoice = $voices | Where-Object { $_.Language -eq "en-US" } | Select-Object -First 1
}
if (-not $chosenVoice) {
    $chosenVoice = $voices | Where-Object { $_.Language -like "en*" } | Select-Object -First 1
}
if ($chosenVoice) {
    $synth.Voice = $chosenVoice
    Write-Output ("VOICE=" + $chosenVoice.DisplayName)
} else {
    Write-Output "VOICE=Default"
}

$rawJson = [System.IO.File]::ReadAllText($JobsJsonPath, [System.Text.Encoding]::UTF8)
$jobs = $rawJson | ConvertFrom-Json
if ($jobs -isnot [System.Array]) {
    $jobs = @($jobs)
}

foreach ($job in $jobs) {
    $stream = $null
    $inputStream = $null
    $reader = $null
    try {
        $stream = Await-WinRtOp ($synth.SynthesizeTextToStreamAsync([string]$job.spokenText)) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
        $size = [uint32]$stream.Size
        $inputStream = $stream.GetInputStreamAt(0)
        $reader = New-Object Windows.Storage.Streams.DataReader($inputStream)
        $loaded = Await-WinRtOp ($reader.LoadAsync($size)) ([uint32])
        $bytes = New-Object byte[] $loaded
        $reader.ReadBytes($bytes)
        [System.IO.File]::WriteAllBytes([string]$job.wavPath, $bytes)
    } finally {
        if ($reader) { $reader.Dispose() }
        if ($inputStream) { $inputStream.Dispose() }
        if ($stream) { $stream.Dispose() }
    }
}
$synth.Dispose()
"""


def load_rules(rules_path: Path) -> dict:
    if not rules_path.exists():
        return {"voice": "en-US-AndrewNeural", "fallbackVoice": "en-US-GuyNeural", "rate": "+0%"}
    return json.loads(rules_path.read_text(encoding="utf-8"))


def build_spoken_text(word: dict, rules: dict) -> str:
    word_id = word["id"]
    overrides = rules.get("wordOverrides", {})
    if word_id in overrides and str(overrides[word_id]).strip():
        return str(overrides[word_id]).strip()

    text = (word.get("ttsText") or word.get("en") or "").strip()
    replacements = rules.get("replacements", {})
    for src in sorted(replacements.keys(), key=len, reverse=True):
        dst = replacements[src]
        pattern = rf"(?<![A-Za-z0-9]){re.escape(src)}(?![A-Za-z0-9])"
        text = re.sub(pattern, dst, text)
    text = re.sub(r"\s{2,}", " ", text).strip()
    return text


def compute_digest(engine: str, voice: str, rate: str, spoken_text: str) -> str:
    payload = f"{engine}|{voice}|{rate}|{spoken_text}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def is_cached_valid(audio_index: dict, word_id: str, expected_digest: str, out_path: Path) -> bool:
    prev = audio_index.get(word_id, {})
    return (
        prev.get("hash") == expected_digest
        and out_path.exists()
        and out_path.stat().st_size > 1024
    )


async def synthesize_edge_one(
    sem: asyncio.Semaphore,
    spoken_text: str,
    voice: str,
    fallback_voice: str,
    rate: str,
    out_path: Path,
    max_retries: int = 2,
) -> tuple[bool, str, str]:
    tmp_path = out_path.with_suffix(".mp3.tmp")
    voices_to_try = [voice] if not fallback_voice or fallback_voice == voice else [voice, fallback_voice]

    async with sem:
        last_err = ""
        for chosen_voice in voices_to_try:
            for attempt in range(max_retries):
                try:
                    communicate = edge_tts.Communicate(text=spoken_text, voice=chosen_voice, rate=rate)
                    await communicate.save(str(tmp_path))
                    if tmp_path.exists() and tmp_path.stat().st_size > 1024:
                        tmp_path.replace(out_path)
                        return True, chosen_voice, ""
                    last_err = "Generated MP3 was empty or too small"
                except Exception as exc:
                    last_err = f"{type(exc).__name__}: {exc}"
                finally:
                    if tmp_path.exists():
                        try:
                            tmp_path.unlink()
                        except OSError:
                            pass
                await asyncio.sleep(0.8 * (2**attempt))
        return False, voice, last_err


async def run_edge_engine(
    pending_items: list[tuple[dict, str, Path]],
    voice: str,
    fallback_voice: str,
    rate: str,
    concurrency: int,
    no_proxy: bool,
    audio_index: dict[str, dict],
) -> tuple[int, list[str]]:
    if no_proxy:
        for var in PROXY_ENV_VARS:
            os.environ.pop(var, None)

    sem = asyncio.Semaphore(max(1, concurrency))

    async def worker(item: tuple[dict, str, Path]):
        word, spoken_text, out_path = item
        ok, used_voice, err = await synthesize_edge_one(
            sem=sem,
            spoken_text=spoken_text,
            voice=voice,
            fallback_voice=fallback_voice,
            rate=rate,
            out_path=out_path,
        )
        return word, spoken_text, out_path, ok, used_voice, err

    results = await asyncio.gather(*(worker(item) for item in pending_items))
    succeeded = 0
    failures: list[str] = []
    for word, spoken_text, out_path, ok, used_voice, err in results:
        if ok:
            succeeded += 1
            size_bytes = out_path.stat().st_size
            audio_index[word["id"]] = {
                "engine": "edge",
                "hash": compute_digest("edge", voice, rate, spoken_text),
                "spokenText": spoken_text,
                "voice": used_voice,
                "rate": rate,
                "bytes": size_bytes,
            }
            print(f"  [OK:edge] {word['seq']:>3} {word['id']} ({size_bytes} B) <- \"{spoken_text}\"")
        else:
            failures.append(f"{word['id']} ({word['en']}): {err}")
            print(f"  [FAIL:edge] {word['seq']:>3} {word['id']}: {err}", file=sys.stderr)
    return succeeded, failures


def run_winrt_engine(
    pending_items: list[tuple[dict, str, Path]],
    preferred_voice: str,
    rate: str,
    audio_index: dict[str, dict],
) -> tuple[int, list[str]]:
    succeeded = 0
    failures: list[str] = []

    with tempfile.TemporaryDirectory(prefix="ess_winrt_") as tmp_dir_str:
        tmp_dir = Path(tmp_dir_str)
        ps_path = tmp_dir / "synth_batch.ps1"
        jobs_path = tmp_dir / "jobs.json"
        ps_path.write_text(WINRT_PS_SCRIPT, encoding="utf-8")

        jobs_payload = []
        for word, spoken_text, out_path in pending_items:
            wav_path = tmp_dir / f"{word['id']}.wav"
            jobs_payload.append(
                {
                    "id": word["id"],
                    "spokenText": spoken_text,
                    "wavPath": str(wav_path),
                    "mp3Path": str(out_path),
                }
            )
        jobs_path.write_text(json.dumps(jobs_payload, ensure_ascii=False, indent=2), encoding="utf-8")

        ps_cmd = [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(ps_path),
            "-JobsJsonPath",
            str(jobs_path),
            "-PreferredVoice",
            preferred_voice,
        ]
        ps_proc = subprocess.run(ps_cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if ps_proc.returncode != 0:
            err_msg = ps_proc.stderr.strip() or ps_proc.stdout.strip() or f"PowerShell exit {ps_proc.returncode}"
            return 0, [f"WinRT batch failed: {err_msg}"]

        used_voice = preferred_voice
        for line in ps_proc.stdout.splitlines():
            if line.startswith("VOICE="):
                used_voice = line.split("=", 1)[1].strip() or preferred_voice

        for word, spoken_text, out_path in pending_items:
            wav_path = tmp_dir / f"{word['id']}.wav"
            mp3_tmp = out_path.with_name(f".{out_path.stem}.tmp.mp3")
            if not wav_path.exists() or wav_path.stat().st_size <= 1024:
                failures.append(f"{word['id']}: WAV not generated")
                print(f"  [FAIL:winrt] {word['seq']:>3} {word['id']}: WAV missing", file=sys.stderr)
                continue

            ff_cmd = [
                "ffmpeg",
                "-y",
                "-v",
                "error",
                "-i",
                str(wav_path),
                "-codec:a",
                "libmp3lame",
                "-b:a",
                "48k",
                "-ac",
                "1",
                "-ar",
                "24000",
                "-f",
                "mp3",
                str(mp3_tmp),
            ]
            ff_proc = subprocess.run(ff_cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
            if ff_proc.returncode == 0 and mp3_tmp.exists() and mp3_tmp.stat().st_size > 1024:
                mp3_tmp.replace(out_path)
                size_bytes = out_path.stat().st_size
                audio_index[word["id"]] = {
                    "engine": "winrt",
                    "hash": compute_digest("winrt", preferred_voice, rate, spoken_text),
                    "spokenText": spoken_text,
                    "voice": used_voice,
                    "rate": rate,
                    "bytes": size_bytes,
                }
                succeeded += 1
                print(f"  [OK:winrt] {word['seq']:>3} {word['id']} ({size_bytes} B) <- \"{spoken_text}\"")
            else:
                if mp3_tmp.exists():
                    mp3_tmp.unlink()
                ff_err = ff_proc.stderr.strip() or f"ffmpeg exit {ff_proc.returncode}"
                failures.append(f"{word['id']}: {ff_err}")
                print(f"  [FAIL:winrt] {word['seq']:>3} {word['id']}: {ff_err}", file=sys.stderr)

    return succeeded, failures


async def run_generation(args: argparse.Namespace) -> int:
    rules = load_rules(args.rules)
    edge_voice = args.voice or rules.get("voice", "en-US-AndrewNeural")
    fallback_voice = rules.get("fallbackVoice", "en-US-GuyNeural")
    winrt_voice = args.winrt_voice or "Microsoft Mark"
    rate = args.rate or rules.get("rate", "+0%")

    pack_files = sorted(args.packs_dir.glob("*.json"))
    if not pack_files:
        print(f"ERROR: No pack JSON files found in {args.packs_dir}", file=sys.stderr)
        return 1

    words: list[dict] = []
    for pf in pack_files:
        data = json.loads(pf.read_text(encoding="utf-8"))
        words.extend(data.get("words", []))

    args.audio_dir.mkdir(parents=True, exist_ok=True)
    index_path = args.audio_dir / "audio_index.json"
    audio_index: dict[str, dict] = {}
    if index_path.exists():
        try:
            audio_index = json.loads(index_path.read_text(encoding="utf-8"))
        except Exception:
            audio_index = {}

    def collect_pending(target_engine: str, target_voice: str) -> tuple[int, list[tuple[dict, str, Path]]]:
        cached_count = 0
        items: list[tuple[dict, str, Path]] = []
        for word in words:
            spoken_text = build_spoken_text(word, rules)
            digest = compute_digest(target_engine, target_voice, rate, spoken_text)
            out_path = args.audio_dir / f"{word['id']}.mp3"
            if not args.force and is_cached_valid(audio_index, word["id"], digest, out_path):
                cached_count += 1
            else:
                items.append((word, spoken_text, out_path))
        if args.limit > 0:
            items = items[: args.limit]
        return cached_count, items

    try:
        if args.engine == "edge":
            cached, pending = collect_pending("edge", edge_voice)
            print(f"[edge] Total={len(words)}, cached={cached}, pending={len(pending)}")
            if not pending:
                return 0
            _, failures = await run_edge_engine(
                pending, edge_voice, fallback_voice, rate, args.concurrency, args.no_proxy, audio_index
            )
        elif args.engine == "winrt":
            cached, pending = collect_pending("winrt", winrt_voice)
            print(f"[winrt] Total={len(words)}, cached={cached}, pending={len(pending)}")
            if not pending:
                return 0
            _, failures = run_winrt_engine(pending, winrt_voice, rate, audio_index)
        else:  # auto
            cached, pending = collect_pending("edge", edge_voice)
            print(f"[auto->edge] Total={len(words)}, cached={cached}, pending={len(pending)}")
            if not pending:
                return 0
            ok_count, failures = await run_edge_engine(
                pending, edge_voice, fallback_voice, rate, args.concurrency, args.no_proxy, audio_index
            )
            if failures and ok_count == 0:
                print("Edge TTS unreachable/rejected; falling back to offline WinRT + ffmpeg...", file=sys.stderr)
                cached, pending = collect_pending("winrt", winrt_voice)
                print(f"[auto->winrt] Total={len(words)}, cached={cached}, pending={len(pending)}")
                if not pending:
                    return 0
                _, failures = run_winrt_engine(pending, winrt_voice, rate, audio_index)
            elif failures:
                # Retry only failed items on winrt
                failed_ids = {f.split(":", 1)[0].split(" ", 1)[0] for f in failures}
                rem = [(w, t, p) for (w, t, p) in pending if w["id"] in failed_ids]
                _, failures = run_winrt_engine(rem, winrt_voice, rate, audio_index)
    finally:
        index_path.write_text(
            json.dumps(audio_index, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    if failures:
        print(f"ERROR: {len(failures)} audio file(s) failed to generate.", file=sys.stderr)
        return 1

    print(f"SUCCESS: Audio pipeline finished in {args.audio_dir}")
    return 0


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Batch-generate MP3 pronunciations.")
    parser.add_argument("--packs-dir", type=Path, default=repo_root / "vocab-repo" / "dist" / "packs")
    parser.add_argument("--rules", type=Path, default=repo_root / "vocab-repo" / "config" / "pronunciation_rules.json")
    parser.add_argument("--audio-dir", type=Path, default=repo_root / "vocab-repo" / "dist" / "audio")
    parser.add_argument("--engine", choices=("auto", "edge", "winrt"), default="auto")
    parser.add_argument("--no-proxy", action="store_true", help="Clear proxy env vars before calling edge-tts.")
    parser.add_argument("--voice", type=str, default="")
    parser.add_argument("--winrt-voice", type=str, default="Microsoft Mark")
    parser.add_argument("--rate", type=str, default="")
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--limit", type=int, default=0, help="Max words to generate (0 = all pending).")
    parser.add_argument("--force", action="store_true", help="Regenerate even if cached.")
    args = parser.parse_args()
    return asyncio.run(run_generation(args))


if __name__ == "__main__":
    raise SystemExit(main())
