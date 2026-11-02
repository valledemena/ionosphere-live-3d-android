# IMPLEMENTATION_SLICES — R0 → R6 (GLM)

**Estado**: BASELINE GLM R0. **Autoría**: GLM, canal GLM→David, 2026-11-02.
**Responde a**: Issue #1 (deliverable 3, pregunta 5). Sustituye el tablero tentativo de `docs/TASKS.md` (Beam lo actualiza en el PR de R0).

Regla transversal: **cada slice = PR pequeño**, revisado hunk-a-hunk por GLM, CI verde, y **gate de dispositivo de David antes de merge**. Verificado R0: el repo contiene hoy solo docs — Beam ha cumplido "sin código antes de la baseline GLM".

## R0 — Baseline (este PR)
Los 5 documentos R0 + LICENSE GPL-3.0 + TASKS.md alineado + `whiteboard/` creado (WORKFLOW.md lo menciona; hoy no existe) con espejo del resumen.
**DoD**: merge a main + acuse de recibo de Beam en el PR.

## R1 — Esqueleto + CI
Gradle KTS, app Compose vacía, `GLSurfaceView` con globo texturizado (tier medium), GitHub Actions (`assembleDebug` + `unitTest` + `lint` en cada PR), NOTICE de terceros. Sin código de red.
**DoD**: APK en el dispositivo de David con globo girando a FPS estables; árbol del repo = estructura aprobada en este doc; GLM revisa el diff completo.

## R2 — Núcleo de datos (contrato v1)
Modelos Kotlin espejo + `docs/contracts/*.schema.json` + fixtures dorados; adaptadores directos de **space-weather, ionosondes, aurora, dst** (conjunto mínimo usable); caché memoria+disco con TTLs de DATA_CONTRACT; precedencia de fuente + degradación honesta visible en UI.
**DoD**: tests unit + contract verdes; modo avión muestra `cache +N min` / `model` con edad; GLM revisa la normalización diff-a-diff contra las rutas web; David valida con datos reales por WiFi y por datos móviles.

## R3 — Escena v1 (lenguaje visual)
Tierra + luces nocturnas + shell atmosférico + puntos/capa de ionosondas + HUD (Kp/Dst/foF2). Defaults = `DEFAULT_SETTINGS` de la web, exactos. Colormaps portados con tests espejo. Slider pointSize 3–15 (clamp 2–30, como el oráculo).
**DoD**: validación lado a lado con la web en el dispositivo de David; presupuesto FPS cumplido (TEST_GATES §5); revisión GLM.

## R4 — Tiempo y profundidad
Replay 168 h + TimeBar: las puertas puras de la web (`replayAdvance`, `replaySliderOffset`, `timeBarEffectiveMinutes`, `timeBarClockLabel`) se portan con tests espejo de `replay.test.ts`. Muestreo de historia (`sampleStationAt`). Cortina Ne(h), iso-bands, raymarch (density/chapman) tras `atmoQuality`. Decisión NDK-MSIS aquí si hace falta el fondo de modelo (con su licencia).
**DoD**: paridad de replay vs web; informe de batería de David (sesión de 30 min).

## R5 — Cielo vivo
Satélites (sgp4 Kotlin + refresco TLE con caché 6 h), CME/ENLIL, flares/fadeout, WSPR, lightning, electrojets/AE, cinturones Van Allen, anillo West Ford 1963 (**DECLARADO reconstrucción, sin datos vivos**, como la web), magnetosfera.
**DoD**: paridad + FPS/batería en dispositivo + etiqueta de fuente declarada en cada capa.

## R6 — Release
Endurecimiento perf/energía, auto-tier de texturas por dispositivo, AAB/APK firmados, GitHub Releases (el release enlaza al tag = oferta de fuente GPL-3), NOTICE completo, `BUILDING.md`, tag `v0.1.0-android`.
**DoD**: firma y publica David; revisión final GLM.

## Prioridad "minimal usable" (pregunta 5 del Issue #1)

**Primero**: R1 + R2 — globo + foF2/Kp/Dst reales con degradación honesta. Ya es una app útil y verificable.
**Esperan**: profundidad de R4 (raymarch, cortina), todo R5 (satélites, WSPR, lightning, magnetosfera…) y R6.
