# TEST_GATES — R0 (GLM) · v1.1

**Estado**: BASELINE GLM R0 (v1.1: §5 matriz actualizada con el dispositivo declarado, 2026-11-02).
**Autoría**: GLM, canal GLM→David, textual, 2026-11-02. Publicado por David en nombre de GLM.
**Responde a**: Issue #1 (deliverable 4).

## 1. Niveles de test

1. **Unit JVM (JUnit)** para `app/domain` (Kotlin puro): **tests espejo** de `lib/iono/*.test.ts` de la web — la web ya fija el comportamiento esperado (replay, `sampleStationAt`, colormaps, fallback/precedencia de fuente, sgp4, volumeMarch…). Portar el test relevante junto con cada función.
2. **Contract / golden fixtures**: mismo raw upstream grabado → parseo Kotlin == payload web esperado (comparación de JSON canónico). Corren en CI en cada PR.
3. **Instrumented (mínimo)**: smoke de renderer (contexto EGL creado, primer frame sin errores GL) + modo avión (UI muestra degradación declarada). `connectedAndroidTest` en el dispositivo de David; **sin emulator en v1**.
4. **Dispositivo real**: gate de David por slice (ver IMPLEMENTATION_SLICES).

## 2. Disciplina de mutantes (heredada de OK-2)

Todo guard de comportamiento (precedencia de fuente, TTL, `stale`, wrap de replay, "nunca vacío como bueno") tiene un test que **muere si el guard se muta**. Verificación local durante el desarrollo (Beam la ejecuta y reporta en el PR); los mutantes no se commitean.

## 3. CI (GitHub Actions, ubuntu)

- Cada PR: `assembleDebug` + `unitTest` + `lint`. Obligatorio y bloqueante.
- Emulator nightly: opcional **después** de R3; no sustituye nunca al dispositivo.
- Honradez declarada: el CI no puede validar render, batería ni red real — eso lo valida David en el dispositivo, y así se registra.

## 4. Gates de fusión (todos obligatorios)

1. CI verde.
2. Revisión hunk-a-hunk de GLM (canal GLM→David; comentarios en el PR).
3. Validación en dispositivo de David con el DoD del slice.
4. Merge: solo David.

## 5. Matriz de dispositivos y presupuestos (v1.1)

- Dispositivo de referencia **declarado** (2026-11-02, vía Beam): **Xiaomi Mi 10 Lite — Android 11 / API 30, Adreno 620 (→ GLES 3.2, cubre la baseline ES 3.1 con margen), 6 GB RAM**. *Pendiente: confirmación directa de David en el PR de publicación de esta v1.1.*
- Presupuestos (cerrados con ese dispositivo como referencia de gama media-alta): **30 FPS sostenidos** en escena viva · **20 FPS** piso aceptable en tier `atmoQuality=low` · texturas **≤ 4K ASTC** en tier high · RAM de texturas **< 400 MB** en high · 60 FPS solo opcional en tier high con límite configurable.
- Cada slice R3+ reporta FPS y consumo medidos en el dispositivo de la matriz.

## 6. Regresión contra oráculos

- Web: fixtures dorados + comparación visual lado a lado (screenshots de `docs/screenshots/` de la web como referencia de lenguaje visual).
- Desktop: semántica de degradación y defaults (`App.cpp`) citada en los comentarios de porteo, como ya hace `types.ts`.
