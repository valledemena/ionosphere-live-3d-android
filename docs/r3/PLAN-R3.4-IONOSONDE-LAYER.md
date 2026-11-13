# PLAN R3.4 - CAPA DE IONOSONDAS (puntos + popup + clamp pointSize)

**Estado**: PROPUESTA docs-only, pendiente de dictamen de GLM. **Sin codigo.**
**Fecha**: 2026-11-13. **Autor**: OpenCode (DeepSeek V4.1 Flash).
**Issue**: #15 (tracker R3, contrato T1-T7). **Repo**: valledemena/ionosphere-live-3d-android.
**Base**: `origin/main` = `7f596462703774d8d3f114176c481021fdaa4513` (merge de PR #37).
**Oraculo web**: `8cdc3348ab087d10dd43807af217da4e246d996d`.

---

## 0. Que es este documento y que no es

Este PR es **solo documentacion**. No toca `app/`, no toca `data/**`, no toca
`.github/`, no toca el instrumento congelado
`docs/r4/tools/R4_DISPLAY_CHECK_v6.py` (sha256
`5a48cb189c33b7a4e81bed65263caf30d51272265a62c36e7eaf21afa4a6dea4`, inmutable
por pre-registro).

Hace tres cosas, y solo tres:

1. **Cierra los numeros T1-T7** de R3.4 antes de escribir una linea de codigo
   (condicion C de #15 y condicion explicita del dictamen de GLM).
2. Repara `docs/TASKS.md` (mojibake + filas desfasadas) y aplica la
   denominacion **`paridad-R5`** al track ya cerrado, para que nadie confunda el
   track de paridad con la fase R5 del roadmap (seccion 13).
3. Deja el contrato del popup definido, porque **el web no tiene popup**: si no
   se define aqui, se inventa durante la implementacion (seccion 8).

No aprueba nada. El GO es de David; el dictamen es de GLM.

---

## 1. Alcance del slice (verbatim, sin redefinir)

Texto canonico de #15, sin tocar una coma:

> 4. **R3.4 - Capa de ionosondas**: puntos + popup + clamp pointSize 2-30, tests
>    de frontera (T3).

> **T3 (R3.4)**: pointSize clamp 2,30 probado en 2,30,1,31,-1,NaN.

Tres entregables, ni uno mas:

| # | Entregable | Verificacion |
|---|---|---|
| E1 | Capa de puntos de ionosondas en el globo | captura en dispositivo + FPS (T4) |
| E2 | Popup de estacion | captura en dispositivo + contrato seccion 8 |
| E3 | `stationPointSizeClamp` [2,30] con tests de frontera | JUnit JVM + mutantes seccion 7 |

Invariante **C1** de #15 sigue vigente: este slice **no** produce diffs bajo
`data/**`. La capa de render consume la capa de datos existente tal cual.

---

## 2. Estado verificado (comandos y resultados, no memoria)

Medido sobre `origin/main` = `7f59646`, 2026-11-13:

| Afirmacion | Comando | Resultado |
|---|---|---|
| Capa de datos de ionosondas existe | `git ls-tree -r --name-only origin/main -- app/src/main/java/.../data` | `IonosondeData.kt`, `IonosondeParser.kt`, `IonosondeAdapter.kt` |
| Ya hay tests de esa capa | idem `app/src/test/.../ionosondes` | `IonosondeAdapterSmokeTest.kt`, `Kc2gFailoverTest.kt` |
| La capa de datos **ya se consume** en produccion | `git grep -n IonosondeAdapter origin/main -- app` | `MainActivity.kt:77` la instancia; `HudStore.kt:152` la inyecta como `fof2Source` |
| No hay capa de render de ionosondas | `git ls-tree -r --name-only origin/main -- app/src/main/java/.../gl app/src/main/java/.../render` | `gl/`: 7 ficheros (globo, atmo, post), ninguno de estaciones. `render/`: 7 ficheros (atmo, bloom, godrays, tonemap, colormaps), ninguno de estaciones |
| No hay camino de puntos en GL | `git grep -n "GL_POINTS\|gl_PointSize\|POINTS" origin/main -- app/src` | **vacio** (exit 1) |
| `pointSize` no existe en la app | `git grep -n pointSize origin/main -- app/src` | **vacio** (exit 1) |
| No hay grid de fo (rango de color del web) | `git grep -n "peakGrid\|foGrid\|foTex\|GRID_W\|fMin" origin/main -- app/src/main` | **vacio** (exit 1) |
| R3.4 es el unico slice abierto de R3 | `git show origin/main:docs/r3/README.md` | "R3.4 pendiente: capa ionosondas + pointSize clamp 2-30 tests" |
| Es el unico issue abierto | `gh issue list --state open` | solo #15 |

Consecuencia de diseno: **no hace falta ningun camino de red nuevo**. El render
lee el mismo `HudEntry<IonosondeData>` que ya produce `HudStore.fof2()`
(`HudStore.kt`: `fun fof2(): HudEntry<IonosondeData>? = fof2Slot.entry`), con su
TTL y su politica de edad. Esto respeta C1 y evita doble fetch.

---

## 3. Oraculo web: pin y numeros exactos

Blobs del oraculo en `8cdc334` (verificados con `git rev-parse 8cdc334:<ruta>`):

| Fichero | blob |
|---|---|
| `src/components/ionosphere/types.ts` | `455fe79a585a1dd1a26fcee35eadfdc2671c23f5` |
| `src/components/ionosphere/shaders.ts` | `aeecc91c9a83f22bc9e4e6474ac029724987cdfc` |
| `src/components/ionosphere/IonosphereScene.tsx` | `e390f66fe5566c5f870697218738f16826ebd9ca` |
| `src/components/ionosphere/hud/LayersPanel.tsx` | `d388012eb1198bede6df7b802b3fc36c205308af` |
| `src/components/ionosphere/settingsStorage.ts` | `ef60d50c526f73dfee13e48346a4e394a9d67328` |

`types.ts` blob es identico al pin de R2 (`docs/WEB_PIN_R2.txt`), es decir la
superficie de tipos no se ha movido entre R2 y R3.0.

### 3.1 Clamp (fuente literal)

`types.ts:393-399`:

```ts
/** Referencia del tamano de punto (default 11 == base 140 del shader). */
export const STATION_POINT_REF = 11;

/** Clamp [2,30] (setPointSize, IonosondeLayer.h:72). */
export function stationPointSizeClamp(v: number): number {
  if (!(v > 0)) return 2;
  if (v < 2) return 2;
  if (v > 30) return 30;
  return v;
}
```

Nota fina que importa para los tests: `!(v > 0)` es la guarda de NaN. Sin ella,
NaN cae por los tres `if` (todas las comparaciones con NaN son `false`) y
**devuelve NaN**. La guarda de `v < 2` no la sustituye.

### 3.2 Setting: dos capas, no una

| Capa | Fuente | Rango |
|---|---|---|
| Control de UI (slider) | `hud/LayersPanel.tsx:228-238` | `min=3`, `max=15`, `step=1` |
| Persistencia | `settingsStorage.ts:90` `readNumber(r, "pointSize", 3, 15, d.pointSize)` | acepta 3..15; no finito -> default |
| Default | `types.ts` `DEFAULT_SETTINGS.pointSize` | **11** (`STATION_POINT_REF`) |
| Uniform enviado al shader | `IonosphereScene.tsx:1393-1394` `stationPointSizeClamp(settings.pointSize)` | **[2,30]** |

El clamp [2,30] **nunca lo ejerce el slider** (3..15 lo deja pasar entero). Solo
muerde valores programaticos o persistidos anomalos. Las dos capas existen en el
web y las dos se portan; decir "slider 3..15" o decir "clamp 2..30" y omitir la
otra es un informe incompleto.

### 3.3 Shader de puntos (fuente literal)

`shaders.ts` `STATION_VERT` (L424-446):

```glsl
gl_PointSize = clamp(140.0 * uPointSize / 11.0 / dist, 3.0, min(26.0 * uPointSize / 11.0, 35.0)) * uPixelRatio;
vF = aFo;
vFade = 0.75 + 0.25 * sin(uTime * 2.2 + position.x * 40.0 + position.y * 33.0);
if (uHighlight > 0.5) vFade = 0.6 + 0.4 * sin(uTime * 6.0);
```

`shaders.ts` `STATION_FRAG` (L449-464):

```glsl
vec2 c = gl_PointCoord * 2.0 - 1.0;
float d = dot(c, c);
if (d > 1.0) discard;
float a = smoothstep(1.0, 0.25, d) * vFade;
vec3 col = uHasData > 0.5 ? texture2D(uCmap, vec2(vF, 0.5)).rgb : vec3(0.55);
float halo = smoothstep(1.0, 0.55, d) - smoothstep(0.55, 0.0, d);
gl_FragColor = vec4(col + vec3(0.9) * halo * 0.35, a * (uHasData > 0.5 ? 0.95 : 0.45));
```

`uPixelRatio = min(devicePixelRatio, 2)` (`builders.ts:320`).

### 3.4 Geometria y color (fuente literal)

`builders.ts:295-333` `buildStations`:

- posicion: `latLonToVec3(lat, lon, 1.012)` (radio 1.012, encima del globo).
- color: `aFo = (f > 0) ? clamp((f - fMin) / (fMax - fMin || 1), 0, 1) : 0`.
- rango: `fMin = sorted[floor(n*0.02)]`, `fMax = sorted[floor(n*0.98)]`,
  `span = max(1.5, fMax - fMin)`, con `sorted` = **grid de fo del shell**
  (`GRID_W = 72`, `GRID_H = 36`, 2592 muestras de `buildLivePeakGrids`;
  `builders.ts:26-27`, `IonosphereScene.tsx:1668-1695, 1742`), **no** las foF2 de
  las estaciones.

### 3.5 Tres hechos del web que el plan declara y no "arregla"

1. **`uHasData` nunca se actualiza**: su unica asignacion es el inicializador
   `{ value: 1 }` en `builders.ts:323`; las otras 3 apariciones son la
   declaracion y los dos usos del frag. `IonosphereScene.tsx` no lo escribe
   (grep vacio). Por tanto la rama gris `vec3(0.55)` y el alpha 0.45 **son
   codigo muerto en este commit**: una estacion sin `foF2` se pinta con el color
   del cmap en `t = 0`, alpha 0.95.
2. **`uHighlight` nunca se actualiza**: misma situacion (`{ value: 0 }` en
   `builders.ts:322`, sin escrituras); la rama de parpadeo de la estacion
   seleccionada es tambien muerta.
3. **`uMinF`/`uMaxF` se envian pero no se usan** en el vertex shader (aFo se
   calcula en CPU).

Decision de este plan: **se replica el comportamiento efectivo, no la
intencion**. En particular, una estacion sin dato se pinta en cmap(0) como en el
web; la honestidad se da en el popup (seccion 8), no cambiando el punto. Mejorar el
web (encender `uHasData`) es una divergencia que necesita su propio dictamen, no
un atajo dentro de R3.4.

### 3.6 Picking (fuente literal)

`IonosphereScene.tsx:719-748`:

- descarta si el arrastre supera **6 px** desde `pointerdown`;
- proyecta cada estacion; candidato si `dd < 60 px`;
- seleccion si `dd < bestD`, con **`bestD = 24 px`** inicial, tomando el minimo;
- descarta hemisferio oculto: `world.dot(camDir) >= 0`;
- emite `onStationPick(code | null)`; **no** abre ninguna UI (el web solo tiene
  un callback y un log de debug).

### 3.7 El web **no tiene popup**

Grep de `popup` en `.tsx`/`.ts` del web: **0 ficheros**. Grep de
`selectedStation|hoveredStation|onStationClick|stationInfo|tooltip`: solo
`route.ts` (tipos de API) y un comentario de `ideasData.ts`. Es decir: **el
popup de R3.4 es una pieza Android, no hay paridad que reclamar**; su contrato
se define en seccion 8 y su merito es la degradacion honesta, no la fidelidad visual.

---

## 4. T1-T7 cerrados

Condicion de GLM: cerrados **antes** de codificar. Los que no aplican al slice
se marcan como tales con el motivo; no se dejan "a definir en PR".

| T | Que exige | Aplica a R3.4 | Numero cerrado en este plan |
|---|---|---|---|
| **T1** | Textura max 4096x2048; memoria de texturas < 64 MB (>=4 GB), < 32 MB (<2 GB) | **No** (cerrado en R3.1; R3.4 no anade texturas) | Sin cambio: el slice no introduce ningun bitmap nuevo. Presupuesto de texturas del tier medium intacto. |
| **T2** | `DEFAULT_SETTINGS` paridad exacta extraida del web; HUD tol. <= 5% visual en device | **Si** (solo la clave `pointSize`) | `pointSize` default **11**; slider **3..15 step 1**; persistencia `readNumber(3,15,11)` con no-finito -> 11; uniform `= clamp(valor, 2, 30)`. Tol. visual: **<= 5%** del diametro medido para una estacion cerca del centro del disco con el mismo `pointSize`, en la captura lado a lado; si la camara no se puede igualar (el web no expone API de camara), se declara el delta medido y el juicio es de David (T6/T7). |
| **T3** | `pointSize` clamp [2,30] probado en `2,30,1,31,-1,NaN` | **Si** (nucleo del slice) | Tabla seccion 7.1, 6 casos obligatorios + 5 casos de la suite del web. Implementacion con el **mismo orden de guardas** que `types.ts:393`. |
| **T4** | Metodo FPS fijo: 5 min sostenidos, bateria 50%, avion ON, carga termica; medium >= 30 fps, low >= 20 fps worst-case 60 s | **Si** | Ver seccion 9. Ademas: **FPS(puntos ON) >= 0.95 x FPS(puntos OFF)** en la misma corrida; `gl_PointSize` efectivo <= **70 px** de diametro (`35 x min(dpr,2)`). |
| **T5** | Colormaps extraidos con script, unicos; entero exacto, float epsilon <= 1/255 por canal | **Si** (consumo, no extraccion) | Ya cerrado en R3.1: `ColormapsPinsTest` fija **16 pines** generados ejecutando `src/lib/iono/colormaps.ts` en `8cdc334`, con tolerancia medida **1e-4/canal** (mas estricta que el 1/255 del contrato). R3.4 consume `render/Colormaps.kt` sin tocarlo. Normalizacion `aFo`: **|diff| <= 1e-9** absoluto frente al oraculo (mismo orden de operaciones, IEEE-754 doble). |
| **T6** | Capturas por slice en dispositivo, anchor firmado por David | **Si** | **4 capturas** obligatorias (T6-1..T6-4 en seccion 9.3) + 1 opcional si la corrida tiene una estacion sin dato. Anchors: sha256 de cada PNG firmado por David en el PR. |
| **T7** | David firma el gate; Beam captura/extrae rastro; GLM revisa hunk-a-hunk | **Si** | David firma seccion 9.5; GLM revisa el PR de codigo hunk-a-hunk antes del merge; merge solo David. |

---

## 5. Diseno del slice

Cinco piezas, un PR de codigo, en este orden de commits. La pieza A no importa
`android.*` ni `GLES*`: es Kotlin puro y por eso se testea en JVM como
`Colormaps.kt`.

### A. Dominio puro (testeable en JVM)

`render/StationPoints.kt`:

```kotlin
const val STATION_POINT_REF = 11f
fun stationPointSizeClamp(v: Float): Float      // espejo literal de types.ts:393
fun stationPositions(stations: List<IonosondeStation>): FloatArray  // xyz, radio 1.012
fun stationFoValues(stations: List<IonosondeStation>, fMin: Float, fMax: Float): FloatArray
fun stationColorRange(foF2: List<Double>): Pair<Float, Float>       // percentiles 2/98 + span 1.5
fun stationPointPixels(uPointSize: Float, dist: Float, pixelRatio: Float): Float // formula del vert
```

`stationPointPixels` es la formula del shader extraida a Kotlin **para poder
testearla sin GPU**; el shader sigue calculandola (no se envia por uniform el
resultado) y un test comprueba que la version Kotlin y la constante GLSL
concuerdan sobre una rejilla de valores (ver seccion 7.3).

**Divergencia declarada y medida (rango de color).** El web ancla `fMin`/`fMax`
al grid de fo del shell (2592 muestras, 72x36). Android no tiene ese grid (seccion 2) y R3.4
no lo puede introducir sin romper "cada PR toca solo su slice". Por tanto:

- **formula**: identica (percentiles 2/98 sobre la muestra disponible,
  `span = max(1.5, fMax-fMin)`, clamp 0..1);
- **muestra**: las `foF2` de las estaciones recibidas, no el grid;
- **consecuencia**: **no se reclama paridad de color** en R3.4, solo paridad de
  formula. El color de una estacion puede diferir del web y eso es esperado,
  documentado y visible en la captura T6-1. Se convierte en paridad real cuando
  exista el grid de fo del shell (fase R4/R5), no antes.

### B. Capa GL de puntos

`gl/StationPointsRenderer.kt` (nuevo, aislado; **no** se refactoriza
`GlobeRenderer`):

- programa propio (`ShaderProgram`) con `STATION_VERT`/`STATION_FRAG` espejo;
- VAO + VBO propios (`aPosition` vec3 + `aFo` float), un solo `glDrawArrays(GL_POINTS, 0, n)`;
- `gl_PointSize` se escribe **en todas** las rutas del vertex shader. En GLES
  **no existe** `GL_PROGRAM_POINT_SIZE` (verificado: ausente de
  `android.opengl.GLES20/30/31/32` en `android.jar` de android-35); el valor del
  shader se aplica directamente a `GL_POINTS`, no hay `glEnable` que hacer;
- consulta en `onSurfaceCreated` de `GL_ALIASED_POINT_SIZE_RANGE` (constante de
  `GLES20`, heredada por `GLES30`) y **clampea el techo del shader al maximo del
  driver** (ver riesgo seccion 10.1); el valor medido se registra en el informe
  del gate;
- orden de dibujo: globo (opaco, escribe profundidad) -> **puntos** (depth test
  ON, **depth write OFF**, transparente) -> atmosfera. Coincide con el orden del
  web (`transparent: true, depthWrite: false`).

### C. Popup

`ui/hud/StationPopup.kt` (Compose) + `ui/hud/StationPopupModel.kt` (puro,
testeable). Contrato en seccion 8. Se abre/cierra por callback del renderer; **no**
consulta red.

### D. Cableado

- `GlobeRenderer`: campo `stations: StationPointsRenderer?`, `setStations(data)`
  y `setPointSize(raw)`; dibuja solo si `data != null && stations.isNotEmpty()`.
- `GlobeSurfaceView`: encola eventos de pick (tap con las mismas guardas del web:
  6 px de arrastre, 24 px de umbral, hemisferio visible) y los entrega en el hilo
  de UI con el `code` o `null`.
- `MainActivity`: mantiene `pointSize` (default 11) y `selectedCode`; alimenta
  `StationPopup` desde `HudStore.fof2()` ya existente.
- `HudStore`: **sin cambios de contrato**; `fof2()` ya es publica. Si hace falta
  un accesor de solo lectura del ultimo payload, se anade como `internal` y se
  justifica en el PR (no se toca `data/**`).

### E. Tests + docs

Ver seccion 7. Y `docs/r3/README.md`: la linea de R3.4 pasa de "pendiente" a "plan
depositado" con el enlace a este documento.

---

## 6. Ficheros

**Nuevos**

| Ruta | Tipo |
|---|---|
| `docs/r3/PLAN-R3.4-IONOSONDE-LAYER.md` | este plan |
| `app/src/main/java/com/valledemena/ionosphere/render/StationPoints.kt` | dominio puro |
| `app/src/main/java/com/valledemena/ionosphere/gl/StationPointsRenderer.kt` | GL |
| `app/src/main/java/com/valledemena/ionosphere/ui/hud/StationPopup.kt` | Compose |
| `app/src/main/java/com/valledemena/ionosphere/ui/hud/StationPopupModel.kt` | dominio puro |
| `app/src/test/java/com/valledemena/ionosphere/render/StationPointSizeClampTest.kt` | JUnit |
| `app/src/test/java/com/valledemena/ionosphere/render/StationPointsGeometryTest.kt` | JUnit |
| `app/src/test/java/com/valledemena/ionosphere/render/StationPickTest.kt` | JUnit |
| `app/src/test/java/com/valledemena/ionosphere/ui/hud/StationPopupModelTest.kt` | JUnit |

**Modificados**

| Ruta | Cambio |
|---|---|
| `app/src/main/java/com/valledemena/ionosphere/gl/GlobeRenderer.kt` | +1 campo, +1 llamada de dibujo, +2 setters (sin tocar el camino del globo) |
| `app/src/main/java/com/valledemena/ionosphere/gl/GlobeSurfaceView.kt` | cola de eventos de pick + entrega a UI |
| `app/src/main/java/com/valledemena/ionosphere/MainActivity.kt` | estado `pointSize`/`selectedCode`, hospeda el popup |
| `docs/r3/README.md` | R3.4 -> plan depositado |

**Prohibidos en este slice**

- `data/**` (C1), `docs/r4/tools/R4_DISPLAY_CHECK_v6.py` (congelado),
  `.github/**`, `app/src/main/AndroidManifest.xml`.

---

## 7. Tests y mutantes

### 7.1 Fronteras de T3 (obligatorias)

| Entrada | Esperado | Origen |
|---|---|---|
| `2` | `2` | T3 |
| `30` | `30` | T3 |
| `1` | `2` | T3 |
| `31` | `30` | T3 |
| `-1` | `2` | T3 |
| `NaN` | `2` | T3 |
| `0` | `2` | suite web (`replay.test.ts:103`) |
| `11` | `11` | suite web (default) |
| `99` | `30` | suite web |
| `+Inf` | `30` | derivado: `!(v>0)` falso, `v>30` cierto |
| `-Inf` | `2` | derivado: `v<2` cierto |
| `2.5` | `2.5` | derivado: pasa entero (no se trunca) |

Los seis primeros son **condicion de aceptacion**; el resto parasita la suite
del web y no se puede quitar.

### 7.2 Mutantes (disciplina de TEST_GATES seccion 2)

Se aplican **localmente**, se mide que el test muere, y **no se commitea ningun
mutante**. Tabla cerrada (mutante -> test que lo mata):

| Mutante | Cambio | Test que lo mata |
|---|---|---|
| MUT-1 | borrar `if (!(v > 0)) return 2` | solo `NaN -> 2` (los demas siguen cayendo en `v < 2`) |
| MUT-2 | `v < 2` -> `v < 3` | `1 -> 2` y `2 -> 2` |
| MUT-3 | `30` -> `31` en la guarda alta | `31 -> 30` y `99 -> 30` |
| MUT-4 | borrar la guarda alta entera | `31 -> 30`, `99 -> 30`, `+Inf -> 30` |
| MUT-5 | `span = max(1.5, ...)` -> `span = fMax - fMin` | fixture de span degenerado (seccion 7.4) |
| MUT-6 | `aFo` sin clamp 0..1 | fixture con `foF2` fuera de [fMin,fMax] |
| MUT-7 | `bestD = 24` -> `48` | pick a 30 px -> `null` |
| MUT-8 | quitar la guarda de arrastre `> 6 px` | tap-arrastre -> `null` |
| MUT-9 | quitar `world.dot(camDir) >= 0` | estacion de hemisferio oculto -> `null` |
| MUT-10 | uniform = valor crudo en vez de `clamp` | `pointSize = 99` -> uniform `30` |
| MUT-11 | radio `1.012` -> `1.0` | test de magnitud `|v|` de las posiciones |

**Mutante equivalente declarado**: `v < 2` -> `v <= 2` **no** se puede matar
(devuelve 2 en ambos casos) y **no** es un hueco de cobertura. Se declara aqui
para que no parezca un olvido.

### 7.3 Concordancia Kotlin/GLSL

Test que evalua `stationPointPixels` en una rejilla
`uPointSize in {2,3,11,15,30} x dist in {1.0,1.2,1.412,2.0,5.0} x pixelRatio in {1.0,2.0,2.75}`
y compara con los mimos valores calculados a partir de las **constantes GLSL**
(140.0, 11.0, 3.0, 26.0, 35.0) leidas del propio fuente del shader, con
tolerancia `1e-4` relativa. Rompe si alguien cambia una constante en un solo lado.

### 7.4 Fixtures

- `span` degenerado: estaciones con `foF2` todas iguales (p.ej. 5.0) -> `span =
  1.5` (no `0`), sin division por cero.
- Sin dato: `foF2 = null` -> `aFo = 0.0` exacto (replica del web, seccion 3.5).
- `foF2 = -1` (invalido) -> `aFo = 0.0` (el web usa `f > 0`, no `f != 0`).
- Picking: 3 estaciones sinteticas con la camara por defecto; una en el centro
  (debe ganar), una a 30 px (debe perder), una en el hemisferio oculto (nunca).

Todos los fixtures son **literales en el test** (Kotlin puro, sin red, sin JSON
externo): no hay script de extraccion que mantener para esta pieza.

---

## 8. Contrato del popup (degradacion honesta)

El web no tiene popup (seccion 3.7). Este contrato es, por tanto, **especificacion
nueva de Android**; se cierra aqui para que GLM la revise antes de que exista
codigo. Campos, todos tomados de `IonosondeStation` / `IonosondeData`, sin
inventar ninguno:

| Campo UI | Fuente | Si falta |
|---|---|---|
| Nombre | `IonosondeStation.name` | `"sin nombre"` |
| Codigo | `IonosondeStation.code` | no puede faltar (clave de pick) |
| Lat/Lon | `lat`, `lon` | formato `%.2f`, siempre presente |
| foF2 | `foF2` (MHz) | **`"sin dato foF2"`** |
| MUF(3000) | `muf3000` (MHz) | **`"sin dato MUF"`** |
| Fuente | `IonosondeData.source` | se muestra tal cual; nunca se reescribe |
| Edad | `IonosondeData.updated` + `HudEntry.ageLabel` | la etiqueta ya sellada (C4-C5); no se recalcula |
| Cobertura | `total` / `catalogSize` | `"N con dato / M en catalogo"` |

Reglas de estado:

1. `stale = true` -> badge **STALE** y los mismos numeros. No se oculta el dato
   ni se sustituye por otro.
2. `Outcome.Cached` -> se muestra la edad real y el badge de fuente; no se
   presenta como fresco.
3. `Outcome.Failure` -> **no hay puntos** (no hay datos) y el popup no puede
   abrirse. El HUD sigue mostrando fuente + edad, como hoy.
4. `pointSize` no afecta al popup.
5. Un tap fuera cierra; un tap en otra estacion sustituye; una estacion de
   hemisferio oculto no es seleccionable (seccion 3.6).
6. **Prohibido**: rellenar `foF2`/`muf3000` con 0, con la media, o con el valor
   de otra estacion. Un hueco es un hueco y se ve.

`StationPopupModel.kt` es puro: recibe `(IonosondeStation, IonosondeData,
ageLabel)` y devuelve la lista de lineas ya formateadas. Test: cada fila de la
tabla, y los casos 1-3. Es ahi donde vive la "degradacion honesta" que pide GLM,
no en el color del punto (seccion 3.5).

---

## 9. Gate de dispositivo (T4 + T6)

### 9.1 Entorno fijo

Xiaomi Mi 10 Lite 5G, Android 11 / API 30, Adreno 620. Bateria **50%**, modo
avion **ON** (los datos vienen de cache/Outcome.Cached; se declara), carga
termica declarada, 5 minutos sostenidos.

### 9.2 Medidas (T4)

| Medida | Metodo | Umbral |
|---|---|---|
| FPS medium, puntos ON | 5 min sostenidos en escena viva, `FramePacer` | **>= 30 fps** |
| FPS medium, puntos OFF | misma corrida, capa desactivada | **>= 30 fps** |
| Regresion por la capa | `FPS(ON) / FPS(OFF)` | **>= 0.95** |
| Piso low | tier `atmoQuality=low`, peor ventana de 60 s | **>= 20 fps** |
| Diametro de punto | `35 x min(dpr,2)` px, y `GL_ALIASED_POINT_SIZE_RANGE` | **<= 70 px** y dentro del maximo del driver |

El CI **no** valida nada de esto; se declara en el informe del gate y lo firma
David.

### 9.3 Capturas (T6), ancladas por sha256

| ID | Contenido |
|---|---|
| T6-1 | estaciones ON, `pointSize = 11` (default), camara por defecto |
| T6-2 | `pointSize = 3` (minimo del slider) |
| T6-3 | `pointSize = 15` (maximo del slider) |
| T6-4 | popup abierto sobre una estacion con `foF2`, con `code` visible en la captura |
| T6-5 (opcional) | popup sobre una estacion `stale` o sin `foF2`, si la corrida la tiene |

Ademas, **una** captura lado a lado web vs Android con la misma camara
(aproximada) para el juicio visual de color y tamano; se declara que la camara
no es identica y que el color no es paridad (seccion 5.A).

### 9.4 Evidencia

`work/r34-device-<head7>.txt` con: modelo, build, sha256 del APK, las 5 medidas
de seccion 9.2, ruta de las capturas y su sha256, y el rango de punto reportado por el
driver. Sin placeholders.

### 9.5 Firma (T7)

David firma en el PR: "gate R3.4 OK" + los sha256 de las capturas. GLM revisa el
diff hunk-a-hunk **antes** de esa firma o en paralelo, no despues del merge.

---

## 10. Riesgos y mitigaciones

**10.1 Techo de `gl_PointSize` del driver.** GLES solo garantiza `1.0`; Adreno
suele reportar valores grandes, pero el techo efectivo puede ser menor que
`35 x dpr` (70 px). Mitigacion: consultar `GL_ALIASED_POINT_SIZE_RANGE` y
clampear el techo del shader; **medirlo y reportarlo**. No se pre-construye un
camino de quads/sprite: eso seria otro slice y solo si la medida lo exige.

**10.2 `gl_PointSize` sin escribir.** En GLES no hay `GL_PROGRAM_POINT_SIZE` que
active nada: si una rama del vertex shader no escribe `gl_PointSize`, el tamano
queda indefinido (1 px en la practica) sin error de compilacion ni de link. Fallo
silencioso: los puntos "existen" pero no se ven. Mitigacion: escritura
incondicional en el shader + test de concordancia Kotlin/GLSL (seccion 7.3) +
captura T6-2, que lo delata.

**10.3 Orden de transparencias.** Puntos con `depthWrite=false` tras el globo y
antes de la atmosfera. Riesgo de halo recortado o de puntos ocultos por el
globo. Mitigacion: orden fijado en seccion 5.B; si la captura muestra artefacto, se
corrige el orden, no el alpha.

**10.4 Doble capa de punto (slider vs clamp).** Ya existe en el web; el riesgo es
"arreglarlo" en Android y romper paridad. Mitigacion: las dos capas se portan
tal cual y la tabla seccion 7.1 fija el comportamiento.

**10.5 Diverge el color.** Declarado (seccion 5.A). Riesgo de que GLM lo lea como
paridad rota: se dice explicitamente que **no se reclama** y por que.

**10.6 El popup se convierte en scope creep** (detalles, historial, graficas).
Mitigacion: seccion 8 es una lista cerrada; cualquier campo extra exige dictamen.

**10.7 Fetch duplicado.** Riesgo de que el renderer pida a red por su cuenta.
Mitigacion: el render solo consume `HudStore.fof2()`; C1 y seccion 2 lo hacen
verificable por diff (`data/**` sin cambios).

**10.8 Densidad de estaciones.** Overdraw con punto de 70 px y muchas estaciones.
Mitigacion: umbral de FPS y regresion 0.95 de T4; si falla, el ajuste es del
techo del shader **con numero nuevo**, no "a ojo".

---

## 11. DoD / criterios de aceptacion

1. `assembleDebug`, `unitTest`, `lint` en verde (CI 3/3) sobre el SHA del PR.
2. Los 6 casos de T3 y los 11 de seccion 7.1 pasan.
3. Los 11 mutantes de seccion 7.2 mueren con el test declarado; MUT equivalente
   declarado.
4. Test de concordancia Kotlin/GLSL (seccion 7.3) verde.
5. Popup: las filas de seccion 8 cubiertas por test, incluidos `stale`, `Cached` y
   `Failure`.
6. Gate de dispositivo seccion 9 ejecutado, con `work/r34-device-<head7>.txt` sin
   placeholders.
7. 4 capturas T6 ancladas por sha256 y firmadas por David.
8. `git diff --numstat <base>..<head>` no toca `data/**`, ni el checker
   congelado, ni workflows.
9. GLM revisa hunk-a-hunk; merge solo David.
10. `docs/r3/README.md` actualizado y #15 actualizado con el estado real.

---

## 12. No objetivos

- No replay, no scrub temporal, no `sampleStationAt` animado.
- No satelites, no rayos HF, no WSPR, no magnetosfera, no aurora.
- No grid de fo del shell, no texturas nuevas, no hires.
- No reescritura de `GlobeRenderer` ni del pase de atmosfera.
- No cambios en `data/**` ni nuevas fuentes de red.
- No renombrar ni tocar `R4_DISPLAY_CHECK_v6.py`.
- No "mejorar" el web (`uHasData`, `uHighlight`): divergencia con dictamen
  propio.
- No medir FPS con otro metodo que T4.
- No merge ni push sin autorizacion de David.

---

## 13. Correcciones documentales incluidas en este mismo PR

GLM las pidio dentro del PR docs-only para que se revisen con el plan.

### 13.1 `docs/TASKS.md`: reparacion determinista + filas desfasadas

**Mojibake.** El blob de `origin/main:docs/TASKS.md` decodifica como UTF-8 pero
es **doble codificacion**: el texto real esta en UTF-8 y se volvio a codificar
como CP1252 y luego UTF-8. Reparacion determinista verificada (round-trip
exacto, sin perdida):

```
s = blob.decode('utf-8')            # 1605 chars, valido
fixed = s.encode('cp1252').decode('utf-8')   # 1583 chars
```

Los unicos no-ASCII resultantes son: `U+00F3` x4, `U+00ED` x4, `U+2014` x3,
`U+00E1` x2, `U+00E9` x2, `U+00FA` x1, `U+2013` x1, `U+00F1` x1. Es decir,
tildes y rayas: el texto queda limpio. Ningun caracter se pierde ni se sustituye
por `?`. El fichero reparado se escribe UTF-8 **sin BOM** y con `\n`.

**Filas desfasadas** (la tabla de fases se corrige contra la evidencia, no contra
la memoria):

| Fila | Antes | Despues | Evidencia |
|---|---|---|---|
| R1 | "siguiente - Issue #2" | **cerrado** | R3 en curso sobre el esqueleto; CI 3/3 en main |
| R3 | "en progreso (R3.0 web 8cdc334, R3.1 Android 72612b9, R3.2 pendiente)" | "en progreso (R3.0-R3.3 cerrados 2026-11-07; **R3.4 pendiente**: plan `docs/r3/PLAN-R3.4-IONOSONDE-LAYER.md`)" | `docs/r3/README.md` |

Ademas se anade una linea sobre la relacion fases/tracks (ver 13.2) para que la
tabla no se lea como si el track de paridad hubiera consumido fases.

### 13.2 Denominacion `paridad-R5`

Motivo: el track de paridad visual cerrado el 2026-11-13 usa los nombres "R4" y
"R5", que **colisionan** con las fases R4 (Tiempo y profundidad) y R5 (Cielo
vivo) de `docs/r0/IMPLEMENTATION_SLICES.md`. Un lector de "R5 CERRADO" concluye
lo contrario de la verdad: la fase R5 no ha empezado.

Cambios (solo donde "R5" significa **track**; lineas del fichero ya modificado):

| Fichero | Linea | Antes | Despues |
|---|---|---|---|
| `docs/r4/R4_RISK_AND_DECISIONS.md` | 5 | `**Medidor W11/R5:**` | `**Medidor W11/paridad-R5:**` (+ nota de nomenclatura de 3 lineas) |
| `docs/r4/R4_RISK_AND_DECISIONS.md` | 21 | `R5 backlog; no entra en E.` | `paridad-R5 backlog; no entra en E.` + D4 declarado sin dueno |
| `docs/r4/R4_RISK_AND_DECISIONS.md` | 32 | `medidor R5 byte-exacto` | `medidor paridad-R5 byte-exacto` |
| `docs/r4/R4_RISK_AND_DECISIONS.md` | 33 | `re-auditoria R5` | `re-auditoria paridad-R5` |
| `docs/r4/GATE-E-VALIDATION.md` | 113 | `medidor R5 byte-exacto` | `medidor paridad-R5 byte-exacto` |

Numstat de estos dos ficheros contra `7f59646`: `8 4` (R4_RISK: 4 lineas
cambiadas + 4 anadidas) y `1 1` (GATE-E). Ningun otro caracter se toca.

**No se toca** `docs/r4/tools/R4_DISPLAY_CHECK_v6.py`, que contiene 7 menciones
a "R5": esta congelado por pre-registro y renombrar dentro de el invalidaria la
cadena de hashes. El glosario del renombrado se explica en `docs/r3/README.md` y
en `docs/TASKS.md`, fuera del instrumento.

**D4 (export de video)** queda **sin dueno**: su "R5 backlog" era el track. Este
PR **no** lo reasigna (seria inventar alcance); propone R6 o slice dedicado y lo
deja como decision de David (seccion 15.5).

### 13.3 `docs/r3/README.md`

La linea de R3.4 pasa a "plan depositado" con el enlace a este documento. Se
anade una nota de una linea sobre fases vs tracks.

---

## 14. Orden de PRs

| # | PR | Contenido | Gate |
|---|---|---|---|
| 1 | **este** (docs-only) | plan + reparacion TASKS + `paridad-R5` + README r3 | dictamen GLM + GO de David |
| 2 | codigo R3.4 | piezas A-E de seccion 5, tests seccion 7, popup seccion 8 | CI 3/3 + gate dispositivo seccion 9 + GLM hunk-a-hunk + firma David |

No se mezclan. El PR de codigo **no** empieza hasta que el plan este dictaminado.

---

## 15. Decisiones pendientes de David

1. **Aprobar los numeros T1-T7** de la tabla seccion 4 (sobre todo T4: regresion 0.95 y
   techo de 70 px).
2. **Aprobar el contrato del popup** seccion 8, incluido el aviso explicito de que **no
   hay paridad web** en esa pieza.
3. **Aprobar la divergencia de color** declarada en seccion 5.A (formula igual, muestra
   distinta, sin reclamar paridad).
4. **Autorizar el push** de la rama `docs/r3.4-plan` y la apertura del PR #1.
5. **Reubicar D4** (export de video): R6 o slice dedicado. No bloquea R3.4.

---

## 16. Appendix: verificaciones ejecutadas para escribir este plan

Todas contra el remoto, el 2026-11-13, con el arbol limpio en `7f59646`:

```
git ls-tree -r --name-only origin/main -- app/src/main/java/com/valledemena/ionosphere/{gl,render}
git grep -n "GL_POINTS\|gl_PointSize" origin/main -- app/src
git grep -n pointSize origin/main -- app/src
git grep -n IonosondeAdapter origin/main -- app
git show origin/main:docs/r3/README.md
git show origin/main:docs/r0/IMPLEMENTATION_SLICES.md
git show origin/main:docs/r0/TEST_GATES.md
gh issue list --state open
```

Y en el repo web (`8cdc334`, arbol limpio):

```
git rev-parse 8cdc334:src/components/ionosphere/{types.ts,shaders.ts,IonosphereScene.tsx,hud/LayersPanel.tsx,settingsStorage.ts}
```

Grep de `uHasData`/`uHighlight`/`uPointSize`/`popup` sobre `src/components/ionosphere/**`.

Y sobre el SDK instalado, para no dar por hecho la API GL:

```
python -c "zipfile: android/opengl/GLES20/30/31/32.class de platforms/android-35/android.jar"
  GL_PROGRAM_POINT_SIZE   -> ausente en GLES20, GLES30, GLES31 y GLES32
  GL_ALIASED_POINT_SIZE_RANGE -> presente en GLES20 (heredada por GLES30)
```

Y sobre los tests ya sellados de R3.1:

```
git show origin/main:app/src/test/java/com/valledemena/ionosphere/render/ColormapsPinsTest.kt
  -> 16 pines generados ejecutando src/lib/iono/colormaps.ts en 8cdc334, tolerancia 1e-4/canal
```

**Conclusion**: el slice es pequeno en codigo (una capa de puntos, un popup, un
clamp), pero tiene tres decisiones que no se pueden dejar a la implementacion:
el rango de color sin grid (seccion 5.A), el contrato del popup sin oraculo
(seccion 8) y el techo de punto del driver (seccion 10.1). Las tres estan
cerradas arriba con numeros.