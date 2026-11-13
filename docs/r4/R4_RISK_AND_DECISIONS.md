# R4 — Riesgos y decisiones

**Base del programa:** `00ec7cb` · **main de arranque E post-P:** `75366b2`
**Pin web:** `8cdc334` · **Instrumento:** v5 `ba433297…`
**Medidor W11/paridad-R5:** `b3794c49…`

Nota de nomenclatura: "R4"/"R5" en este documento son el **track de paridad
visual** ya cerrado (de ahí "paridad-R5"), no las fases R4 (Tiempo y
profundidad) ni R5 (Cielo vivo) de `docs/r0/IMPLEMENTATION_SLICES.md`.

Este documento recoge las decisiones ratificadas D1–D6 y los guardarraíles del
Slice E. No sustituye al plan ni al dictamen del instrumento.

## 1. Decisiones ratificadas

| ID | Decisión | Estado / consecuencia |
|---|---|---|
| **D1** | Paridad visual (ii), no naive | Ratificada; el gate se mide con el instrumento. |
| **D2** | `TARGET_FPS=30` (`FramePacer`) | Ratificada; E1 exige 6 ventanas >= 29 con preset 16/6. |
| **D3** | Damping `0.06` exacto, pan deshabilitado | Ratificada; K1–K6 verdes en main tras D. |
| **D4** | Export de video diferido | paridad-R5 backlog; no entra en E. Sin dueño tras cerrar el track: candidato a R6 o slice dedicado (decisión de David). |
| **D5** | Textura NASA `2048×1024` (pin `8cdc334`) | Ratificada; el golden de assets queda intocable. |
| **D6** | Cámara display-matching: fov 42, 3.3 R_E, min 1.12, max 30 | Ratificada y fusionada en D; E no toca `app/src/main`. |

## 2. Decisiones de E

| ID | Decisión | Motivo |
|---|---|---|
| **E0** | Cero rutas `app/src/main` en el slice | E mide lo construido; un cambio de producción invalida la comparación. |
| **E1** | 6 ventanas `avg_fps >= 29`, preset 16/6, `--latency` | Criterio del plan y del expediente R2/R3. |
| **E2** | THIN 258–278 es la única serie gateada; DEEP es reporte | Definición pre-registrada; `passes_deep` no es el gate. |
| **E3** | ≥2 documentos en `docs/r4/`; este slice aporta tres | Informe, decisiones y medidor paridad-R5 byte-exacto. |
| **Medidor** | `w11-metric.py` auditado, sha256 `b3794c49…` | Cualquier byte distinto exige re-auditoría paridad-R5. |
| **O1** | Ignorar `passes_deep`; usar THIN del instrumento | Evita doble criterio y falsos verdes. |
| **O2** | La captura declara `{w:610,h:620,dpr:1}` | La geometría del framebuffer es el contrato; la densidad no se usa. |
| **P** | `mediump → highp` en el pase de atmósfera | Fusionado en `75366b2`; elimina la cola fp16 que hacía fallar E2. |

## 3. Riesgos y guardarraíles

| Riesgo | Impacto | Mitigación |
|---|---|---|
| `mediump` = fp16 en Mali | Cola débil del halo cuantizada; E2 < 3 | Resuelto por P (`highp`); re-medir E2 sobre el SHA final. |
| MIUI bloquea inyección de input | No se puede automatizar la interacción | Medición humana controlada; documentar condiciones. |
| Doze / pantalla apagada | FPS falsamente bajo o captura incompleta | Mantener activo, primer plano, sin interacción. |
| APK distinto al SHA | Evidencia no atribuible | `head_sha` exacto, APK debug del commit, `pm path`/hash. |
| Halo con encuadre distinto | `delta_B` no comparable | 610×620, pin, 720/15°, limbo 75–225; declarar método. |
| `comp-<head7>` ausente | W11 FAIL | Reconstruir por igualdad de `app/src/main` con procedencia. |
| Main se mueve antes de E | S3/S4 y base cambian | Re-cortar sobre el main vigente y re-declarar. |
| Copiar el medidor con un byte distinto | Gate no auditado | Copia byte-idéntica y verificación sha256. |
| O1 (`42f` duplicados) | Deuda de mantenimiento | No tocar en E; registrar para un slice futuro. |

## 4. Frontera de E

- **Permitido:** `docs/r4/`, `scripts/`, tests bajo `app/src/test/.../render/`
  y `app/src/test/.../gl/`.
- **Prohibido:** `app/src/main/`, builds, workflows, manifest, golden 4,
  `FramePacer.kt`, `MeasureConfig.kt` y `data/`.
- **Evidencia:** `work/fps-<head7>.json` y `work/halo-<head7>.json`; el
  checker no acepta placeholders.

## 5. Falsación y re-corte

- `eec186a` (base `bfea5d8`) midió E1 PASS y E2 **FAIL 1.74** (1.79 con 16/6).
- La causa raíz fue `mediump` = fp16 en Mali; el emulador no la revelaba.
- P cambió el pase a `highp` y se fusionó en `75366b2`; el diagnóstico highp
  dio THIN 5.03 y W11 1.12.
- E se re-corta sobre `75366b2`; `eec186a` queda como registro de falsación,
  no como cabeza de trabajo.

## 6. Estado

- E0/E3: **PASS** en el checker v5 sobre el candidato `dfc8a4c`.
- E1: **PASS** 6/6 (mínimo 29.87; preset 16/6; `--latency`).
- E2: **PASS** THIN mínimo **5.03**; paridad W11 1.12; DEEP reportada.
- Checker v5: **49/50**, `NO FIRME`; solo S10 (CI) pendiente de push.
- Checker v3 histórico sobre `eec186a`: 47/50; el instrumento vigente es v5.
- Cualquier `--amend` cambia el SHA y exige re-medir las evidencias.
