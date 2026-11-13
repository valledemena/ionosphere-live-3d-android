# TASKS

## R0 — cerrado (2026-11-02)

Gate SHA256 **8/8**: los cinco documentos de baseline más `GLM_R0_RESPONSE`, el
comentario del Issue #1 y el `relay-primitive` están publicados y verificados
byte a byte. Tabla completa y método de verificación: `docs/r0/README.md`.

Ningún artefacto de este repo declara autoría de GLM sin coincidir con su hash.
Ese es el criterio, y no hay otro.

## Fases (fuente: `docs/r0/IMPLEMENTATION_SLICES.md`, GLM)

Este fichero no resume el contenido de ese documento: lo referencia. El detalle
de alcance y DoD de cada fase vive allí, y cualquier discrepancia la gana el
documento canónico.

| Fase | Título canónico | Estado |
|---|---|---|
| R0 | Baseline | **cerrado** |
| R1 | Esqueleto + CI | **cerrado** |
| R2 | Nucleo de datos (contrato v1) | **cerrado** (issue #4 closed 2026-11-06, SHA ac31886) |
| R3 | Escena v1 (lenguaje visual) | en progreso: R3.0-R3.3 cerrados 2026-11-07; **R3.4 en curso** (plan en `main`; implementacion A-E pendiente de gate y revision) |
| R4 | Tiempo y profundidad | pendiente |
| R5 | Cielo vivo | pendiente |
| R6 | Release | pendiente |

Aviso de nomenclatura: el track de **paridad visual** cerrado el 2026-11-13 usa
los nombres "R4" y "R5" (de ahí "paridad-R5"), que **no** son las fases R4
(Tiempo y profundidad) ni R5 (Cielo vivo) de este roadmap. Cuando un documento
diga "paridad-R5" habla del track; las fases R4, R5 y R6 siguen sin empezar.

Mínimo usable declarado por GLM: **R1 + R2** (globo + foF2/Kp/Dst reales con
degradación honesta). Replay, raymarch, satélites, WSPR y magnetosfera esperan a
R3–R5.

## Regla transversal (GLM, `IMPLEMENTATION_SLICES.md`)

Cada slice = un PR pequeño, revisado hunk-a-hunk por GLM, con CI verde y gate de
dispositivo de David antes del merge. Merge: solo David.

## Gate de dispositivo

Xiaomi Mi 10 Lite 5G — Android 11 (API 30), Snapdragon 765G,
Adreno 620 (GLES 3.2), 6 GB RAM. Detalle en `docs/r0/TEST_GATES.md` v1.1.
