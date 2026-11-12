"""W11: metrica radial delta_B por sector (ON - OFF) para las dos plataformas.

Definicion (identica a la declarada en comp-<sha>.json):
  centro = (305, 310)
  theta = 0.5 * k, k en [0, 720)
  para cada radio de la banda se muestrea el vecino mas proximo (sin interpolar)
  delta_B = media(B_on) - media(B_off) por sector de 15 grados del limbo
  iluminado [75, 225) -> 10 sectores

Uso:
  python w11-metric.py <android-ON.png> <android-OFF.png> <web-ON.png> <web-OFF.png>
"""
import json
import math
import sys

import numpy as np
from PIL import Image

CX, CY = 305.0, 310.0
N_ANGLES = 720
SECTOR_DEG = 15
LIMB = [75, 225]
BANDS = {"THIN": (258, 278), "DEEP": (262, 287)}


def channel_b(path):
    im = Image.open(path).convert("RGB")
    return np.asarray(im).astype(np.float64)[:, :, 2]


def samples(b_on, b_off, band):
    """Lista (sector, dB) para todos los (theta, radio) de la banda."""
    r0, r1 = band
    out = []
    for k in range(N_ANGLES):
        th = 0.5 * k
        if th < LIMB[0] or th >= LIMB[1]:
            continue
        sector = int((th - LIMB[0]) // SECTOR_DEG)
        c = math.cos(math.radians(th))
        s = math.sin(math.radians(th))
        for r in range(r0, r1 + 1):
            x = int(round(CX + r * c))
            y = int(round(CY - r * s))
            if 0 <= y < b_on.shape[0] and 0 <= x < b_on.shape[1]:
                out.append((sector, b_on[y, x] - b_off[y, x]))
    return out


def by_sector(pairs):
    acc = [[] for _ in range((LIMB[1] - LIMB[0]) // SECTOR_DEG)]
    for sector, d in pairs:
        acc[sector].append(d)
    return [round(float(np.mean(v)), 2) if v else 0.0 for v in acc]


def platform(on_path, off_path):
    b_on = channel_b(on_path)
    b_off = channel_b(off_path)
    bands = {}
    for name, band in BANDS.items():
        bands[name] = by_sector(samples(b_on, b_off, band))
    bands["AVG"] = [round((a + b) / 2.0, 2) for a, b in zip(bands["THIN"], bands["DEEP"])]
    return {
        "n_angles": N_ANGLES,
        "sector_deg": SECTOR_DEG,
        "illuminated_limb": LIMB,
        "bands": {k: list(v) for k, v in BANDS.items()},
        "delta_B_by_sector": bands["THIN"],
        "delta_B_by_sector_band": "THIN",
        "delta_B_deep_by_sector": bands["DEEP"],
        "delta_B_by_sector_all_bands": bands,
        "sector_start_deg": [LIMB[0] + SECTOR_DEG * i for i in range(10)],
        "capture_on": on_path,
        "capture_off": off_path,
    }


def main():
    a_on, a_off, w_on, w_off = sys.argv[1:5]
    a = platform(a_on, a_off)
    w = platform(w_on, w_off)
    res = {
        "captures": {"android": a, "web": w},
    }
    diffs = [abs(x - y) for x, y in zip(a["delta_B_by_sector"], w["delta_B_by_sector"])]
    deep_diffs = [abs(x - y) for x, y in zip(a["delta_B_deep_by_sector"], w["delta_B_deep_by_sector"])]
    res["report"] = {
        "android_THIN": a["delta_B_by_sector"],
        "web_THIN": w["delta_B_by_sector"],
        "abs_diff": [round(d, 2) for d in diffs],
        "min_android": min(a["delta_B_by_sector"]),
        "min_web": min(w["delta_B_by_sector"]),
        "max_abs_diff": round(max(diffs), 2),
        "android_DEEP": a["delta_B_deep_by_sector"],
        "web_DEEP": w["delta_B_deep_by_sector"],
        "abs_diff_deep": [round(d, 2) for d in deep_diffs],
        "min_android_deep": min(a["delta_B_deep_by_sector"]),
        "min_web_deep": min(w["delta_B_deep_by_sector"]),
        "max_abs_diff_deep": round(max(deep_diffs), 2),
        "parity_tol": 2.0,
        "passes": min(a["delta_B_by_sector"]) >= 3
        and min(w["delta_B_by_sector"]) >= 3
        and max(diffs) <= 2.0,
        "passes_deep": min(a["delta_B_deep_by_sector"]) >= 3
        and min(w["delta_B_deep_by_sector"]) >= 3
        and max(deep_diffs) <= 2.0,
    }
    print(json.dumps(res, indent=2))


if __name__ == "__main__":
    main()
