# ARCHITECTURE_DECISION — Ionosphere Live 3D Android

**Estado**: BASELINE GLM R0 (vinculante para los slices R1+).
**Autoría**: GLM, canal GLM→David, 2026-11-02. Publicado por David en nombre de GLM.
**Responde a**: Issue #1 (deliverable 1, preguntas 1–2).

## 1. Decisión principal

App **100% nativa: Kotlin + Jetpack Compose (Material 3) + renderer propio sobre OpenGL ES 3.1** (`#version 310 es`).

- minSdk 26 · targetSdk 36 · Kotlin 2.x · Gradle KTS · distribución AAB + APK.
- **Sin React Native / react-three-fiber. Sin WebView híbrida.** SceneView/Filament fuera del núcleo (§3).

Justificación contra las alternativas planteadas en el Issue #1:

- La app es **canvas-céntrica**: una escena 3D viva + HUD ligero, no una app de muchas pantallas. El beneficio clásico de RN (compartir UI entre plataformas) apenas existe aquí; su coste clásico (bridge JS en el camino crítico de render, r3f sin paridad completa de `drei` en RN, doble runtime) golpea justo donde más duele: 3D volumétrico + presupuesto de batería en gama media/baja.
- La WebView híbrida duplica la memoria de texturas, gestiona peor la energía y dificulta mostrar la degradación honesta de forma nativa.
- GPL-3 no discrimina entre las opciones (sin fricción en ninguna); la decisión es técnica y de energía, no de licencia.

## 2. Capas

| Capa | Contenido | Regla |
|---|---|---|
| `app/ui` | Compose: HUD, paneles (espejo de `hud/*Panel.tsx`), ajustes | Degradación honesta SIEMPRE visible (fuente + edad por dato) |
| `app/render` | Kotlin + GLES 3.1: escena, capas, colormaps, raymarch | Shaders portados del oráculo C++ (`#version 400 core` → `#version 310 es`); cada adaptación se declara en comentario |
| `app/data` | Kotlin: llamadas directas a fuentes públicas + caché + precedencia declarada | Espejo de la normalización de las rutas `/api` de la web (ver DATA_CONTRACT.md) |
| `app/domain` | Kotlin puro: replay, muestreo de historia, colormaps, sgp4… | Tests espejo de `lib/iono/*.test.ts` |
| NDK | Solo si se adopta NRLMSIS 2.1 en C (reutilización del desktop, con su licencia) para el fondo de modelo | Decisión diferida a R4; todo lo demás es Kotlin puro |

## 3. Motor de render (pregunta 2 del Issue #1)

**Renderer propio GLES 3.1**, no un motor genérico:

- El pipeline del proyecto es específico (shells, raymarch Ne, godrays, iso-bands, cortina Ne(h)) y está afinado contra el oráculo C++ GL 4.0. Un motor genérico no porta ese pipeline sin pelearlo, y su peso compite con el presupuesto de memoria de texturas.
- **SceneView queda permitido puntualmente** para assets glTF estables si aparecen; no entra en v1.
- Bajo consumo: render-on-demand (sin animación activa no se pinta frame), pausa de render en background (lifecycle-aware), límite de FPS configurable, `atmoQuality low|medium|high` (ya existe en `SceneSettings` de la web) mapeado a tier de textura y resolución de render.

## 4. Texturas y memoria

- Tiers: **low 1024×512 · medium 2048×1024 · high 4096×2048**, compresión ASTC/ETC2. El hires 5400×2700 de la web queda **fuera de móvil** (adaptación declarada).
- Auto-selección por RAM/GPU + **lista de dispositivos de David** (pendiente: único bloqueador para cerrar el techo definitivo).

## 5. Licencias

- El repo android **no tiene LICENSE hoy** (verificado R0). Añadir **GPL-3.0 en el PR de R0** — un repo público declarado GPL-3 sin fichero de licencia es ambiguo; no se espera a R1.
- Dependencias: **solo permisivas** (Apache-2.0 / MIT / BSD / ISC). GPL-2-only prohibido. LGPL caso por caso.
- Notices de terceros: NRL MSIS 2.1 (si llega vía NDK, con su `nrlmsis2.1_license.txt`), y atribución de fuentes de datos (NOAA/SWPC, Kyoto WDC, NASA DONKI/SDO/CCMC, GIRO/LGDC, JPL Horizons, WSPRnet, kc2g) en pantalla "Fuentes".
- Verificado R0: **ninguna ruta de la web usa API keys ni secretos** → el móvil replica las llamadas directas sin embeber claves (compatible GPL-3 y con el principio del proyecto).

## 6. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| DONKI (CCMC) desde IPs de dispositivo: rate-limit distinto al de un servidor | Validar en R2 con el patrón de cortesía de la web; si exigiera clave, la capa pasa a degradación declarada — **no se embebe ninguna clave** |
| LGDC 429 (ya observado en vivo por la web) | TTL 10 min + anti-rebaño 5 min + cortesía 250 ms / 6 workers (idéntico a la web) |
| GLES 3.1 en gama baja | Fallback de CALIDAD (tiers), nunca de corrección |
| Deriva de contratos web↔móvil | Fixtures dorados compartidos + revisión GLM por slice (TEST_GATES.md) |
