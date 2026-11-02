# TASKS

## Estado de R0 (2026-11-02)

R0 cierra cuando el gate SHA256 de los 8 artefactos canónicos esté completo.
Estado actual: **5/8** (`docs/r0/README.md`).

Bloqueado en un único punto: los tres textos canónicos
`ARCHITECTURE_DECISION.md`, `DATA_CONTRACT.md` e `IMPLEMENTATION_SLICES.md`
no han llegado a este equipo como ficheros, así que no se pueden publicar
byte a byte. Hasta que lleguen, esas rutas quedan vacías en `docs/r0/`.

No queda ninguna acción pendiente de Beam en R0 aparte de publicar esos tres
ficheros en cuanto existan. El comentario del Issue #1 ya está publicado
(id `6577699347`) y el resto de canónicos están verificados por hash.

## Mapa canónico R0–R6

Fuente: `IMPLEMENTATION_SLICES.md` (GLM, pendiente de publicación). Los nombres
de las fases están citados literalmente por GLM en el Issue #1 y en el acta
`OK-3-R0-verificacion-5bdf689.md` §3; el detalle de alcance de cada fase vive en
ese documento, que aún no está en el repo. No se rellena aquí para no inventar
contenido bajo autoría ajena.

- **R0** — Baseline
- **R1** — Esqueleto + CI
- **R2** — Núcleo de datos
- **R3** — Escena v1
- **R4** — Tiempo y profundidad
- **R5** — Cielo vivo
- **R6** — Release

Mínimo usable declarado por GLM: **R1 + R2**.
Replay, raymarch, satélites y WSPR esperan a R3–R5.

## Alcance de R1 (citado por GLM, Issue #1)

Proyecto Gradle KTS, app Compose vacía, `GLSurfaceView` con globo texturizado
(tier medium), GitHub Actions con `assembleDebug` + `unitTest` + `lint`
bloqueante en cada PR, NOTICE de terceros. **Sin código de red en R1.**

DoD: CI verde; APK instalado en el dispositivo de referencia con el globo
girando a FPS estables; árbol del repo = estructura aprobada; revisión del diff
completo por GLM antes del merge; merge solo David.

## Gate de dispositivo

Xiaomi Mi 10 Lite 5G — Android 11 (API 30), Snapdragon 765G,
Adreno 620 (GLES 3.2), 6 GB RAM.
