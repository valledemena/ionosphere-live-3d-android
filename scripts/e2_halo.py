#!/usr/bin/env python3
"""Ensambla `halo-<head7>.json` desde la salida de `w11-metric.py`.

No calcula píxeles: el medidor auditado (sha256 b3794c49…) es la única
autoridad. Este script sólo selecciona la captura Android, conserva THIN y
DEEP y añade la metadata de captura que exige el gate E2.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

BANDS = {"THIN": [258, 278], "DEEP": [262, 287]}
LIMB = [75, 225]
SECTOR_DEG = 15
N_ANGLES = 720


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("metric", type=Path, help="salida JSON de w11-metric.py")
    ap.add_argument("--head", required=True)
    ap.add_argument("--capture-method", default="freeform exterior 610x766; framebuffer real 610x620; wm size/density bloqueados por MIUI")
    ap.add_argument("--on", type=Path, default=None)
    ap.add_argument("--off", type=Path, default=None)
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args(argv)

    data = json.loads(args.metric.read_text(encoding="utf-8"))
    android = data.get("captures", {}).get("android", data)
    thin = android.get("delta_B_by_sector")
    deep = android.get("delta_B_deep_by_sector")
    if not isinstance(thin, list) or len(thin) != 10:
        print("error: delta_B_by_sector Android ausente o incompleto", file=sys.stderr)
        return 2
    if not isinstance(deep, list) or len(deep) != 10:
        print("error: delta_B_deep_by_sector Android ausente o incompleto", file=sys.stderr)
        return 2

    sectors = []
    sectors_deep = []
    for i, (t, d) in enumerate(zip(thin, deep)):
        deg = LIMB[0] + SECTOR_DEG * i
        sectors.append({"deg": deg, "deg_end": deg + SECTOR_DEG, "delta_B": t, "band": "THIN"})
        sectors_deep.append({"deg": deg, "deg_end": deg + SECTOR_DEG, "delta_B": d, "band": "DEEP"})

    payload = {
        "head_sha": args.head,
        "capture": {"w": 610, "h": 620, "dpr": 1},
        "capture_method": args.capture_method,
        "n_angles": N_ANGLES,
        "sector_deg": SECTOR_DEG,
        "bands": BANDS,
        "illuminated_limb": LIMB,
        "delta_B_min_illuminated": min(thin),
        "sectors": sectors,
        "sectors_deep": sectors_deep,
        "source_metric": {
            "file": str(args.metric),
            "sha256": sha256(args.metric),
        },
    }
    if args.on:
        payload["capture_on"] = {"file": str(args.on), "sha256": sha256(args.on)}
    if args.off:
        payload["capture_off"] = {"file": str(args.off), "sha256": sha256(args.off)}

    out = args.out or Path("work") / f"halo-{args.head[:7]}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(out)
    print("delta_B_min_illuminated", min(thin))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
