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
| R3 | Escena v1 (lenguaje visual) | **cerrado 2026-11-14**: R3.0-R3.3 cerrados 2026-11-07; R3.4 cerrado (PR #39 merge `e12e100`; gate T4/T6 umbral 29,5; T7) |
| R4 | Tiempo y profundidad | **cerrada 2026-11-22**: acta `0446`, merge `6cbb796`, CI `44971098915` 3/3; Q3 14/18 + 4/18; 19,6 %/h (REPLAY pausado, volumen ON, HIGH=64) |
| R5 | Cielo vivo | **mantenida en el plan, diferida** hasta la reanudación de Android (decisión de David, 2026-11-22: reforma previa de la app desktop C++ y de la app web) |
| R6 | Release | **release del estado verificado desde `6cbb796`** (decisión de David, 2026-11-22); artefacto (tag/APK) a crear según su instrucción |

Aviso de nomenclatura: el track de **paridad visual** cerrado el 2026-11-13 usa
los nombres "R4" y "R5" (de ahí "paridad-R5"), que **no** son las fases R4
(Tiempo y profundidad) ni R5 (Cielo vivo) de este roadmap. Cuando un documento
diga "paridad-R5" habla del track, no de esta fase; R4 está cerrada y R5 sigue
en el plan, diferida.

**R5 — Cielo vivo**: satélites, CME/ENLIL, WSPR, lightning, electrojets,
cinturones, anillo 1963 y magnetosfera. Se mantiene y se difiere hasta que se
reanude Android, tras la reforma previa de la app desktop C++ y de la app web.

## Estado del plan (2026-11-22, decisión de David, acta `0448`)

- **R4**: cerrada (acta `0446`) contra `main 6cbb796`, CI `44971098915` 3/3.
  Los cuatro ítems abiertos de esa acta quedan registrados en la línea Android,
  sin acción mientras dure el aparcamiento.
- **Android**: **aparcado**. No se abren slices de código; `main` queda
  congelado en `6cbb796`, salvo decisión explícita de David. La congelación
  admite PRs docs-only de estado como este (sin este carve-out, «documentación
  al día» y «main congelado» serían contradictorios). La reanudación de Android
  es decisión explícita de David, no automática al terminar ningún slice.
- **Track activo — reforma de la app desktop C++ y de la app web**: namespace
  **DW** (confirmado por David), con slices `DW.n/cpp` y `DW.n/web`. Cada slice
  lleva pre-registro con head exacto, criterio de aceptación binario e
  instrumentos; mismo gate que R4 (acta de GLM sobre el head exacto + GO de
  David antes del merge, repo por repo). El hub documental sigue en este repo
  mientras dure el aparcamiento.
- **Baselines**: antes del primer pre-registro DW, cada repo commitea su árbol
  con inventario de ficheros no commiteados y su clasificación. En web
  (`8cdc334`), `next-env.d.ts` entra como higiene y `IonosphereScene.tsx`
  entra tal cual con `DW.1` = revert de los toggles `?bloom=off`/`?atmo=off`
  (marcados «measurement only, revert»), salvo que David los reclame
  explícitamente como funcionalidad. En C++ (`d9fb809`), los untracked se
  clasifican por fichero como higiene / trabajo de la reforma / obsoleto.
- **R6**: release del estado verificado desde `6cbb796`, decidida por David.

Mínimo usable declarado por GLM: **R1 + R2** (globo + foF2/Kp/Dst reales con
degradación honesta). Replay, raymarch, satélites, WSPR y magnetosfera esperan a
R3–R5.

## Regla transversal (GLM, `IMPLEMENTATION_SLICES.md`)

Cada slice = un PR pequeño, revisado hunk-a-hunk por GLM, con CI verde y gate de
dispositivo de David antes del merge. Merge: solo David.

## Gate de dispositivo

Xiaomi Mi 10 Lite 5G — Android 11 (API 30), Snapdragon 765G,
Adreno 620 (GLES 3.2), 6 GB RAM. Detalle en `docs/r0/TEST_GATES.md` v1.1.
