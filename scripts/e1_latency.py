#!/usr/bin/env python3
"""Convierte dumps de `dumpsys SurfaceFlinger --latency` en fps-<head7>.json.

Formato de entrada: secciones `### window N dump M` seguidas de filas
`desired_present actual_present frame_ready` en nanosegundos. Las filas de
probe anteriores a la primera ventana se ignoran.

El criterio del expediente es el timestamp de presentación real (columna 2):
se deduplica, `frames = len(unicos)`, `span = ultimo - primero` y
`avg_fps = (frames - 1) / span`. Reproduce las ventanas de `eec186a`.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import re
import sys
from pathlib import Path

WINDOW_RE = re.compile(r"^###\s+window\s+(\d+)(?:\s+dump\s+\d+)?\s*$")


def parse_windows(text: str) -> list[dict]:
    groups: dict[int, list[tuple[int, int, int]]] = {}
    current: int | None = None
    for raw in text.splitlines():
        line = raw.strip()
        m = WINDOW_RE.match(line)
        if m:
            current = int(m.group(1))
            groups.setdefault(current, [])
            continue
        if current is None:
            continue
        parts = line.split()
        if len(parts) == 3 and all(p.isdigit() for p in parts):
            groups[current].append(tuple(int(p) for p in parts))  # type: ignore[arg-type]

    windows: list[dict] = []
    for number in sorted(groups):
        presents = sorted(
            {row[1] for row in groups[number] if 0 < row[1] < 10**15}
        )
        if len(presents) < 2:
            continue
        span_s = (presents[-1] - presents[0]) / 1e9
        frames = len(presents)
        windows.append(
            {
                "window": number,
                "frames": frames,
                "span_s": round(span_s, 3),
                "avg_fps": round((frames - 1) / span_s, 2) if span_s else 0.0,
            }
        )
    return windows


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("raw", type=Path, help="salida cruda con secciones ### window")
    ap.add_argument("--head", required=True, help="SHA completo del head de E")
    ap.add_argument("--preset", default="16/6")
    ap.add_argument("--method", default="dumpsys SurfaceFlinger --latency (BLAST SurfaceView, columna 2, 8 dumps x ~4 s)")
    ap.add_argument("--layer", default="")
    ap.add_argument("--device", default="")
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args(argv)

    windows = parse_windows(args.raw.read_text(encoding="utf-8", errors="replace"))
    if len(windows) != 6:
        print(f"error: se esperaban 6 ventanas, hay {len(windows)}", file=sys.stderr)
        return 2

    out = args.out or Path("work") / f"fps-{args.head[:7]}.json"
    payload = {
        "head_sha": args.head,
        "preset": args.preset,
        "method": args.method,
        "layer": args.layer,
        "device": args.device,
        "measured_at": _dt.datetime.now(_dt.timezone.utc).isoformat(),
        "windows": windows,
    }
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(out)
    print("min_avg_fps", min(w["avg_fps"] for w in windows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
