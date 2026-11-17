/**
 * entry de la medicion R4.6.0 (se escribe en el temporal del arnes).
 * Cadena de PRODUCCION del pin 8cdc3348, sin reformular fisica.
 */
import os from "node:os";
import { performance } from "node:perf_hooks";

import {
  buildLiveVolume,
  interpProfiles,
  stationToProfileSample,
  type StationLike,
} from "./src/lib/iono/profileGrid";
import { applyDayNight } from "./src/lib/iono/densityVolume";
import { buildDensityVolume } from "./src/lib/iono/layerProfile";

const RUNS = Number(process.argv[2] || 15);
const WARMUP = Number(process.argv[3] || 3);

const EPOCH_SEC = Date.UTC(2026, 10, 17, 0, 0, 0) / 1000;
const F107 = 110;
const SUN_X = 1;
const SUN_Y = 0;
const SUN_Z = 0;
const FLARE = 1;
const N_STATIONS = 40;

function lcg(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

const rnd = lcg(20261117);
const STATIONS: StationLike[] = Array.from({ length: N_STATIONS }, () => ({
  lat: -70 + 140 * rnd(),
  lon: -180 + 360 * rnd(),
  foE: 1.5 + 2.5 * rnd(),
  foF1: 2.5 + 3.0 * rnd(),
  foF2: 3.0 + 8.0 * rnd(),
  hmF2: 230 + 130 * rnd(),
  B0: 60 + 120 * rnd(),
  B1: 1.8 + 1.6 * rnd(),
}));

interface Cfg {
  name: string;
  w: number;
  h: number;
  altRes: number;
  altMin: number;
  altMax: number;
}

const CONFIGS: Cfg[] = [
  { name: "android-target-72x72x48-60-500", w: 72, h: 72, altRes: 48, altMin: 60, altMax: 500 },
  { name: "web-prod-72x36x60-60-700", w: 72, h: 36, altRes: 60, altMin: 60, altMax: 700 },
];

const median = (xs: number[]) => {
  const s = [...xs].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : 0.5 * (s[m - 1] + s[m]);
};
const p95 = (xs: number[]) => {
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.ceil(0.95 * s.length) - 1)];
};

const samples = STATIONS.map((s) => stationToProfileSample(s, EPOCH_SEC * 1000));

function measure(cfg: Cfg) {
  const tInterp: number[] = [];
  const tNight: number[] = [];
  const tBuild: number[] = [];
  const tTotal: number[] = [];
  let checksum = 0;
  let cells = 0;
  let valid = false;

  for (let r = 0; r < WARMUP + RUNS; r++) {
    const t0 = performance.now();
    const profiles = interpProfiles(samples, cfg.w, cfg.h, SUN_X, SUN_Y, SUN_Z, EPOCH_SEC, F107);
    const t1 = performance.now();
    applyDayNight(profiles, cfg.w, cfg.h, SUN_X, SUN_Y, SUN_Z);
    const t2 = performance.now();
    const vol = buildDensityVolume(
      profiles, cfg.w, cfg.h, cfg.altRes, cfg.altMin, cfg.altMax, SUN_X, SUN_Y, SUN_Z, FLARE
    );
    const t3 = performance.now();
    if (r >= WARMUP) {
      tInterp.push(t1 - t0);
      tNight.push(t2 - t1);
      tBuild.push(t3 - t2);
      tTotal.push(t3 - t0);
      checksum = 0;
      for (let i = 0; i < vol.data.length; i++) checksum += vol.data[i];
      for (let i = 0; i < vol.layer.length; i++) checksum += vol.layer[i];
      cells = vol.data.length;
      valid = vol.valid;
    }
  }

  let heap: unknown = "sin --expose-gc";
  const gc = (globalThis as { gc?: () => void }).gc;
  if (gc) {
    gc();
    const before = process.memoryUsage().heapUsed;
    const vol = buildLiveVolume(
      STATIONS, cfg.w, cfg.h, SUN_X, SUN_Y, SUN_Z, EPOCH_SEC, F107, cfg.altRes, cfg.altMin, cfg.altMax, FLARE
    );
    gc();
    const after = process.memoryUsage().heapUsed;
    const touch = vol.data.length + vol.layer.length; // mantener vivo el volumen
    heap = { retained_mib: +((after - before) / 1048576).toFixed(2), touch };
  }

  return {
    name: cfg.name,
    w: cfg.w,
    h: cfg.h,
    altRes: cfg.altRes,
    altMinKm: cfg.altMin,
    altMaxKm: cfg.altMax,
    cells,
    valid,
    ms_median: {
      interpProfiles: +median(tInterp).toFixed(2),
      applyDayNight: +median(tNight).toFixed(2),
      buildDensityVolume: +median(tBuild).toFixed(2),
      total: +median(tTotal).toFixed(2),
    },
    ms_p95: {
      interpProfiles: +p95(tInterp).toFixed(2),
      applyDayNight: +p95(tNight).toFixed(2),
      buildDensityVolume: +p95(tBuild).toFixed(2),
      total: +p95(tTotal).toFixed(2),
    },
    checksum: +checksum.toFixed(6),
    heap,
    texture_bytes: { f32_two_fields: cells * 8, f16_two_fields: cells * 4 },
  };
}

console.log(
  JSON.stringify(
    {
      env: {
        node: process.version,
        platform: `${process.platform} ${os.release()}`,
        arch: process.arch,
        cpu: (os.cpus()[0]?.model ?? "desconocido").trim(),
        cpus: os.cpus().length,
        total_mem_gib: +(os.totalmem() / 1073741824).toFixed(2),
      },
      input: {
        stations: N_STATIONS,
        seed: 20261117,
        epochSec: EPOCH_SEC,
        f107: F107,
        sun: [SUN_X, SUN_Y, SUN_Z],
        flare: FLARE,
        warmup: WARMUP,
        runs: RUNS,
      },
      configs: CONFIGS.map(measure),
    },
    null,
    2
  )
);
