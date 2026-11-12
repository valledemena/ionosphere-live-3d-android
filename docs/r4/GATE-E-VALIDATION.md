# R4 Slice E — Gate de validación (FPS + halo) — post-P

**Estado:** candidato post-P `dfc8a4c` medido. **E0/E1/E2/E3 PASS** en el
checker v5; solo S10 (CI) queda pendiente hasta el push. El SHA final se
re-mide tras el `--amend` que incorpora estas tablas.

**Base:** `origin/main = 75366b2` (merge de P) · rama de trabajo
`feat/r4-e-gates-postp` (se publicará como `feat/r4-e-gates`)
**Instrumento:** `R4_DISPLAY_CHECK_v5.py`, sha256
`ba4332979840d2893bd28e694ca5c8f03e39cc81ad8c149bcd1e6212daa89f00`
**Ancla:** `ANCLA-E-R4-SLICE-GATES-GLM-20261111.md`, actualizada por la
respuesta GLM del 2026-11-12 (re-corte y v5)
**Plan:** `docs/r4/PLAN-R4-DISPLAY-PARITY.md` (PR #30, `4c7bb4f`)

## 1. Estado de los checks E

| Check | Estado | Evidencia / criterio |
|---|---|---|
| **E0** sin código de producción | **PASS** | El diff del slice solo añade `docs/r4/` y `scripts/`; `app/src/main/` = 0 rutas. |
| **E1** gate FPS | **PASS** | 6/6 ventanas >= 29: 29.93, 29.93, 29.93, 29.94, 29.91, 29.87; preset `16/6`, BLAST `--latency`. |
| **E2** gate halo | **PASS** | 610×620, THIN mínimo **5.03** >= 3; paridad W11 1.12; DEEP reportada. |
| **E3** informe `docs/r4/` | **PASS** | Este informe, `R4_RISK_AND_DECISIONS.md` y `tools/w11-metric.py`: 3 ficheros. |

Checker v5 sobre el candidato `dfc8a4c`: **49/50 PASS**, `NO FIRME`; el único
FAIL es S10 (CI del SHA exacto), que se cierra tras el push.

La línea base `eec186a` (pre-P) midió E1 PASS y E2 **FAIL 1.74**; no es la
cabeza de E y no se reutiliza como evidencia. El candidato actual no toca
`app/src/main`: su APK debe ser funcionalmente el de `75366b2`.

## 2. E0 — frontera de producción

- El slice sólo puede tocar `docs/r4/`, `scripts/` y tests de `render/` o `gl/`.
- `app/src/main/`, builds, manifest, workflows, golden 4, `FramePacer`,
  `MeasureConfig` y `data/` quedan fuera.
- El fix de precisión `mediump → highp` vive en P (`a483f48`, ya en main);
  E no lo repite ni lo modifica.

Comprobación:

```bash
git diff --name-only origin/main..HEAD -- app/src/main
# salida esperada: vacía
```

## 3. E1 — gate FPS

**Criterio mecánico:** 6 ventanas de `avg_fps >= 29`, preset `16/6`,
`TARGET_FPS=30` como techo, APK debug del SHA exacto y `dumpsys
SurfaceFlinger --latency` sobre la capa BLAST del `SurfaceView`.

**Parser reproducible:** `scripts/e1_latency.py`. Agrupa los 8 dumps por
ventana y usa el timestamp de presentación (columna 2), deduplicado:
`frames = len(unicos)`, `span = ultimo - primero`, `avg_fps = (frames-1)/span`.
Validado contra `e1-raw-eec186a.txt`: reproduce 29.55, 29.85, 29.91, 29.88,
29.93, 29.91.

```bash
python scripts/e1_latency.py work/e1-raw-<sha7>.txt \
  --head <sha-completo> --preset 16/6 --layer "<capa BLAST>" \
  --out work/fps-<sha7>.json
```

**Evidencia obligatoria:** `work/fps-<head7>.json` con `head_sha`, `preset:
"16/6"`, `method` que contenga `latency` y exactamente 6 ventanas >= 29.

**Estado:** PASS en el candidato `dfc8a4c` con el APK de `75366b2` (idéntico
sha256 `185c336b…`). Valores: 29.93, 29.93, 29.93, 29.94, 29.91, 29.87; mínimo
**29.87**. La evidencia autoritativa se re-mide sobre el SHA final; no se
copian las ventanas de `eec186a` (pre-P).

## 4. E2 — gate halo

**Criterio mecánico:** captura **610×620, DPR 1**, 720 ángulos (sectores de
15°), banda **THIN 258–278** gateada, **DEEP 262–287** reporte, limbo
iluminado **[75,225)**, `delta_B >= 3` en unidades lineales 8-bit.

**Medidor:** `docs/r4/tools/w11-metric.py`, sha256
`b3794c490231e0e6ee5f2ce74292586a93aeb10d9f4456210c258e31db169747`
(byte-exacto; no se modifica). `passes_deep` se ignora: el gate autoritativo
es THIN.

```bash
python docs/r4/tools/w11-metric.py work/e2-<sha7>-on.png work/e2-<sha7>-off.png \
  work/web-nobloom-ON-1794186190.png work/web-OFF-1794186190.png > work/metric-e2-<sha7>.json
python scripts/e2_halo.py work/metric-e2-<sha7>.json --head <sha-completo> \
  --on work/e2-<sha7>-on.png --off work/e2-<sha7>-off.png \
  --out work/halo-<sha7>.json
```

**Captura freeform:** GLM acepta el frame exterior 610×766 con framebuffer
real 610×620, sin reescalado. `halo-<head7>.json` debe declarar
`capture:{w:610,h:620,dpr:1}` y el método real. La densidad 160 no es
requisito del gate.

**Evidencia obligatoria:** `work/halo-<head7>.json` con `head_sha`, captura
610×620, 720/15°, bandas exactas, limbo [75,225] y
`delta_B_min_illuminated >= 3`.

**Estado:** PASS en el candidato `dfc8a4c`. Capturas nuevas ON/OFF 610×620
(`8aaab706…` / `be2f231d…`), preset `14/5`, bloom OFF. THIN: `[52.06, 62.61,
69.21, 70.84, 68.49, 63.0, 49.83, 32.8, 16.39, 5.03]`; mínimo **5.03**.
DEEP: `[13.97, 18.05, 21.19, 22.19, 21.31, 18.88, 13.55, 7.57, 2.89, 0.26]`
(reporte). W11: `max|diff| = 1.12`. La medición pre-P había dado 1.74/1.79 con
`mediump`; el valor de E sale de una medición nueva, no de heredar la de P.

## 5. E3 — informe de gate

Ficheros del slice:

1. `docs/r4/GATE-E-VALIDATION.md` — este informe.
2. `docs/r4/R4_RISK_AND_DECISIONS.md` — decisiones y riesgos.
3. `docs/r4/tools/w11-metric.py` — medidor R5 byte-exacto.
4. `scripts/e1_latency.py` y `scripts/e2_halo.py` — tooling de evidencia.

E3 exige **≥2 ficheros** bajo `docs/r4/` en el árbol del head; el candidato
aporta tres.

## 6. Secuencia de cierre

1. Commit candidato: docs + tooling + medidor byte-exacto sobre `75366b2`.
2. Medir E1/E2 en el Mi 10 Lite sobre el APK del candidato.
3. Llenar las tablas del informe y `git commit --amend` (single-commit).
4. Re-medir el SHA final y generar `work/fps-<head7>.json` y
   `work/halo-<head7>.json` con el `head_sha` final.
5. Push + PR → CI 3/3 del SHA exacto → aviso §6 a GLM.
6. Si un gate falla, se reporta; no se ajusta el umbral.

## 7. Registro de falsación (eec186a, pre-P)

- `eec186a` (base `bfea5d8`): E1 PASS (6/6), E2 **FAIL** THIN mínimo **1.74**;
  1.79 con 16/6. Causa raíz documentada: `mediump` = fp16 en Mali, cola débil
  del halo cuantizada; el emulador no lo revelaba.
- P cambió el pase a `highp` (merge `75366b2`); el diagnóstico highp midió
  THIN 5.03 y W11 1.12.
- `eec186a` se conserva como registro de la falsación, no como cabeza de E.

## 8. Limitaciones y re-medición

- E1/E2 están medidos en el candidato `dfc8a4c` (APK idéntico al de
  `75366b2`). No se declaran autoritativos hasta re-medir el SHA final.
- El `--amend` que incorpora estas tablas cambia el SHA; las evidencias
  `fps-<head7>.json` y `halo-<head7>.json` se regeneran con el SHA final.
- E0/E3 están confirmados por el checker v5 sobre el candidato; se
  reconfirman sobre el SHA final antes del push.
