# GLM Round 0 response — official version

> **Procedencia**: publicado por David en nombre de GLM (canal GLM→David, textual, 2026-11-02).
> Sustituye íntegramente a la versión anterior de este fichero (commits 99c103e/b8d98fc), commiteada sin pasar por el canal GLM y con afirmaciones de verificación que GLM no había realizado. Registro del incidente: `docs/r0/OWNERSHIP_AND_RELEASING.md` §2.

**R0 — Respuesta de GLM (baseline)**

**Handle/acceso**: GLM no tiene ni puede tener cuenta GitHub (modelo en sandbox, sin credenciales). Verificación con lectura pública, canal GLM→David textual. Verificador sin escritura = separación de funciones. Sin bot. Detalle en `docs/r0/OWNERSHIP_AND_RELEASING.md`.

**Respuestas a las cinco preguntas abiertas**:

1. **¿Un lenguaje en web+nativo (RN) o Kotlin nativo?** Kotlin nativo + Compose. La app es canvas-céntrica (una escena 3D viva + HUD); el puente RN golpea justo el 3D volumétrico y la batería en gama media/baja, y el compartir UI casi no existe aquí. GPL-3 no discrimina: se comparte **contrato, no lenguaje**.
2. **¿Motor de render para bajo consumo y profundidad?** Renderer propio sobre **OpenGL ES 3.1** (`#version 310 es`), shaders portados del oráculo GL 4.0 (`#version 400 core`), web Three.js como referencia visual. SceneView/Filament fuera del núcleo (permitido puntual para glTF estable). Bajo consumo: render-on-demand, pausa en background, tiers `atmoQuality`, texturas ASTC (techo móvil 4K; el hires 5400×2700 queda fuera, declarado).
3. **¿Compartir tipos/esquemas entre Next.js y Android?** `types.ts` (web) es la fuente de verdad → JSON Schema commiteados (`docs/contracts/`) + modelos Kotlin espejo + **fixtures dorados** que validan ambos lados en CI. Sin runtime compartido.
4. **¿Proxy local o llamadas directas?** **Directas a las fuentes públicas** (aclaración de David, 2026-11-02: sin web desplegada, móvil autocontenido como el C++). Las rutas `/api` son la especificación de referencia, no dependencia. Verificado: ninguna ruta usa API keys. TTLs y precedencia espejo (tabla completa en `docs/r0/DATA_CONTRACT.md`: 90 s–6 h según endpoint; ionosondes con failover kc2g y anti-rebaño).
5. **¿Qué slices primero?** R1 (esqueleto + CI) y R2 (núcleo de datos: space-weather, ionosondes, aurora, dst) = mínimo usable con degradación honesta. Replay/raymarch/satélites/WSPR espera a R3–R5. Mapa completo R0–R6 en `docs/r0/IMPLEMENTATION_SLICES.md`.

**Registro de incidente**: la versión anterior de este fichero se commiteó (99c103e) con firma "Response-from-GLM" sin pasar por el canal GLM→David, afirmando verificaciones que GLM no había realizado. Esta versión la sustituye con procedencia. No bloquea R0; sí veda el patrón.

**Pendiente de David**: lista de dispositivos (modelo, Android, GPU, RAM) — único blocker para techo de texturas y presupuesto FPS (`docs/r0/TEST_GATES.md` §5).

— GLM (vía David, textual)
