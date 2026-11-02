# Ionosphere Live 3D — Android

Aplicación móvil autónoma del proyecto **Ionosphere Live 3D** (escritorio en
`ionosphere-live-3d`, web en `ionosphere-live-3d-web`). Se conecta **directamente
a las fuentes públicas**: no necesita ningún servidor desplegado.

Estado: **R1 — Esqueleto + CI** (rama `r1/skeleton`). Baseline acordada y
publicada, verificada por hash: `docs/r0/`.

## Qué hay ahora

- Una actividad (`MainActivity`) con Compose Material 3 y **una única vista**:
  el globo. Sin pantallas inventadas (el HUD es R3).
- Renderer propio sobre **OpenGL ES 3.x** (`#version 310 es`, respaldo
  `300 es`): malla esférica UV generada en código, textura equirectangular
  procedural, mipmaps, iluminación difusa.
- **Render-on-demand**: `RENDERMODE_WHEN_DIRTY` + `Choreographer` con techo de
  30 fps, y parada en `ON_STOP`. Nada se dibuja en segundo plano.
- **Sin red**: no hay permiso `INTERNET` ni código de red.

## Compilar

Requisitos: JDK 17+ y Android SDK con `compileSdk 36`.

```bash
./gradlew assembleDebug     # APK de depuración
./gradlew testDebugUnitTest # tests JVM
./gradlew lintDebug         # lint bloqueante en CI
```

Los tres comandos son los tres jobs de CI (`.github/workflows/ci.yml`), y los
tres son bloqueantes en cada PR.

## Estructura

```
app/src/main/java/com/valledemena/ionosphere/
├── MainActivity.kt          actividad única
├── ui/GlobeScreen.kt        AndroidView + ciclo de vida (ON_START / ON_STOP)
├── gl/GlobeSurfaceView.kt   EGL 3.1 con respaldo 3.0, render-on-demand
├── gl/GlobeRenderer.kt      pipeline GL: malla, textura, luz
├── gl/ShaderProgram.kt      compilación GLSL con cabecera adaptativa
└── core/                    lógica pura, testeada en JVM
    ├── Rotation.kt          reloj de rotación y techo de FPS
    ├── SphereMesh.kt        malla esférica UV
    └── PlaceholderGlobeTexture.kt  textura procedural 2048×1024
```

## Procedencia de assets

La textura del globo es **procedural**, generada en el dispositivo por el propio
código (GPL-3.0, este repo). No es una imagen de terceros, no es geografía real y
no requiere atribución externa. Cuando entre una textura real (Blue Marble u otra
con licencia declarada), sustituye a esta sin tocar el renderer. Detalle en
`NOTICE`.

## Coordinación

- **GLM**: arquitectura, especificaciones, revisión hunk a hunk.
- **David**: validación en dispositivo, merges, publicación.
- **Beam**: construcción, tooling, CI.

Regla de la cadena: **nada se anuncia como verificado sin el artefacto**, y una
entrega no cuenta hasta que el receptor confirma los hashes.

Licencia: GPL-3.0 (`LICENSE`).
