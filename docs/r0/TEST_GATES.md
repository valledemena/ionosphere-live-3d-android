# TEST_GATES â€” R0 (GLM) Â· v1.1

**Estado**: BASELINE GLM R0 (v1.1: Â§5 matriz actualizada con el dispositivo declarado, 2026-11-02).
**AutorÃ­a**: GLM, canal GLMâ†’David, textual, 2026-11-02. Publicado por David en nombre de GLM.
**Responde a**: Issue #1 (deliverable 4).

## 1. Niveles de test

1. **Unit JVM (JUnit)** para `app/domain` (Kotlin puro): **tests espejo** de `lib/iono/*.test.ts` de la web â€” la web ya fija el comportamiento esperado (replay, `sampleStationAt`, colormaps, fallback/precedencia de fuente, sgp4, volumeMarchâ€¦). Portar el test relevante junto con cada funciÃ³n.
2. **Contract / golden fixtures**: mismo raw upstream grabado â†’ parseo Kotlin == payload web esperado (comparaciÃ³n de JSON canÃ³nico). Corren en CI en cada PR.
3. **Instrumented (mÃ­nimo)**: smoke de renderer (contexto EGL creado, primer frame sin errores GL) + modo aviÃ³n (UI muestra degradaciÃ³n declarada). `connectedAndroidTest` en el dispositivo de David; **sin emulator en v1**.
4. **Dispositivo real**: gate de David por slice (ver IMPLEMENTATION_SLICES).

## 2. Disciplina de mutantes (heredada de OK-2)

Todo guard de comportamiento (precedencia de fuente, TTL, `stale`, wrap de replay, "nunca vacÃ­o como bueno") tiene un test que **muere si el guard se muta**. VerificaciÃ³n local durante el desarrollo (Beam la ejecuta y reporta en el PR); los mutantes no se commitean.

## 3. CI (GitHub Actions, ubuntu)

- Cada PR: `assembleDebug` + `unitTest` + `lint`. Obligatorio y bloqueante.
- Emulator nightly: opcional **despuÃ©s** de R3; no sustituye nunca al dispositivo.
- Honradez declarada: el CI no puede validar render, baterÃ­a ni red real â€” eso lo valida David en el dispositivo, y asÃ­ se registra.

## 4. Gates de fusiÃ³n (todos obligatorios)

1. CI verde.
2. RevisiÃ³n hunk-a-hunk de GLM (canal GLMâ†’David; comentarios en el PR).
3. ValidaciÃ³n en dispositivo de David con el DoD del slice.
4. Merge: solo David.

## 5. Matriz de dispositivos y presupuestos (v1.1)

- Dispositivo de referencia **declarado** (2026-11-02, vÃ­a Beam): **Xiaomi Mi 10 Lite â€” Android 11 / API 30, Adreno 620 (â†’ GLES 3.2, cubre la baseline ES 3.1 con margen), 6 GB RAM**. *Pendiente: confirmaciÃ³n directa de David en el PR de publicaciÃ³n de esta v1.1.*
- Presupuestos (cerrados con ese dispositivo como referencia de gama media-alta): **30 FPS sostenidos** en escena viva Â· **20 FPS** piso aceptable en tier `atmoQuality=low` Â· texturas **â‰¤ 4K ASTC** en tier high Â· RAM de texturas **< 400 MB** en high Â· 60 FPS solo opcional en tier high con lÃ­mite configurable.
- Cada slice R3+ reporta FPS y consumo medidos en el dispositivo de la matriz.

## 6. RegresiÃ³n contra orÃ¡culos

- Web: fixtures dorados + comparaciÃ³n visual lado a lado (screenshots de `docs/screenshots/` de la web como referencia de lenguaje visual).
- Desktop: semÃ¡ntica de degradaciÃ³n y defaults (`App.cpp`) citada en los comentarios de porteo, como ya hace `types.ts`.

