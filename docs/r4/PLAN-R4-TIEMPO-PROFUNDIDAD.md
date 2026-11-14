# PLAN R4 — TIEMPO Y PROFUNDIDAD

**Estado**: PROPUESTA docs-only, pendiente de dictamen de GLM y GO de David.
**Sin código.** **Fecha**: 2026-11-14.
**Base**: `origin/main` = `52543abc0b0339b162c8365e416cffe55e0f0b7e` (merge de PR #40).
**Oráculo web**: `8cdc3348ab087d10dd43807af217da4e246d996d`.
**Alcance canónico**: `docs/r0/IMPLEMENTATION_SLICES.md`, fase R4.
**Autor**: Buffy (Freebuff), a partir del alcance de GLM.

---

## 0. Qué es este documento y qué no es

Este PR es **solo documentación**. No toca `app/`, no toca `data/**`, no toca
`.github/`, no toca el checker congelado
`docs/r4/tools/R4_DISPLAY_CHECK_v6.py` (sha256
`5a48cb189c33b7a4e81bed65263caf30d51272265a62c36e7eaf21afa4a6dea4`).

Hace cuatro cosas, y solo cuatro:

1. Fija el alcance de R4 sin ampliarlo ni reducirlo.
2. Lo descompone en slices pequeños, cada uno con su propio PR y su gate.
3. Define las puertas puras que se portan del oráculo web antes de escribir GL.
4. Deja explícitas las decisiones que **no** se pueden inventar durante la
   implementación: defaults, época de replay, muestreo de historia, calidad del
   volumen, frontera NDK-MSIS y presupuesto de dispositivo.

**No aprueba nada.** El GO es de David; el dictamen es de GLM; el merge es de
David. Este documento no autoriza código, no autoriza merge y no convierte R4 en
una fase abierta hasta que sus slices pasen sus propios gates.

### 0.1 Nomenclatura

El track de **paridad visual** ya cerrado usa los nombres "R4" y "R5" (de ahí
`paridad-R5`). **No** son esta fase R4 (Tiempo y profundidad) ni la fase R5
(Cielo vivo) de `docs/r0/IMPLEMENTATION_SLICES.md`. Cuando este plan dice "R4",
habla de la fase de tiempo y profundidad; cuando dice "paridad-R5", habla del
track visual histórico.

---

## 1. Alcance canónico

Texto de `docs/r0/IMPLEMENTATION_SLICES.md`, sin redefinirlo:

> **R4 — Tiempo y profundidad**
> Replay 168 h + TimeBar: las puertas puras de la web (`replayAdvance`,
> `replaySliderOffset`, `timeBarEffectiveMinutes`, `timeBarClockLabel`) se
> portan con tests espejo de `replay.test.ts`. Muestreo de historia
> (`sampleStationAt`). Cortina Ne(h), iso-bands, raymarch (density/chapman)
> tras `atmoQuality`. Decisión NDK-MSIS aquí si hace falta el fondo de modelo
> (con su licencia).
> **DoD**: paridad de replay vs web; informe de batería de David (sesión de
> 30 min).

### 1.1 Entregables de la fase

| ID | Entregable | Naturaleza | Gate principal |
|---|---|---|---|
| **E1** | Núcleo puro de replay: avance, slider, época efectiva y reloj | Kotlin puro + JUnit | tests espejo exactos |
| **E2** | TimeBar y reproducción automática: LIVE/REPLAY, scrub, play/pause, velocidad, loop | UI + estado | dispositivo + paridad de estado |
| **E3** | `sampleStationAt` sobre historia dispersa de 168 h | Kotlin puro + JUnit | tests espejo + uso en capa |
| **E4** | Modelo Ne(h) y volumen de densidad | Kotlin puro + JUnit | constantes/perfil/volumen espejo |
| **E5** | Cortina Ne(h) e iso-bands | GL/UI | visual + FPS |
| **E6** | Raymarch `density`/`chapman` detrás de `atmoQuality` | GL/UI | FPS + compilación GL |
| **E7** | Decisión NDK-MSIS, con licencia | ADR docs-only | decisión explícita |
| **E8** | Informe de paridad y batería de 30 min | evidencia | sesión completa |

### 1.2 Fuera de alcance

- R5 (satélites, CME/ENLIL, WSPR, lightning, electrojets, cinturones, anillo
  1963, magnetosfera).
- R6 (release, firma, AAB, hardening final).
- Cualquier fuente de red nueva o cambio de contrato upstream.
- Cualquier cambio en `data/**` para fabricar historia: la historia ya existe
  en el modelo actual.
- Cualquier cambio de defaults visuales ya cerrados en R3/paridad-R5.
- Cualquier implementación NDK-MSIS dentro de este PR o de R4.0.
- Reabrir el gate T4/T6 de R3.4 o la decisión de umbral 29,5.

---

## 2. Línea base verificada

Medido sobre `origin/main` = `52543ab`, 2026-11-14:

| Afirmación | Comprobación | Resultado |
|---|---|---|
| La historia de estaciones ya existe | `StationHistoryPoint` e `IonosondeStation.history` en `data/model/IonosondeData.kt` | sí, `t: String`, `foF2: Double` |
| La app ya conserva historia suficiente | `IonosondeAdapter.kt` y parser | `takeLast(2500)` puntos por estación |
| No hay replay en la app | grep de `replay`, `TimeBar`, `timeMode` en `app/src` | sin símbolos de replay |
| No hay cortina, volumen ni iso-bands | grep de `raymarch`, `curtain`, `showVolume`, `showIso` | sin camino de render |
| La atmósfera ya tiene pasos configurables | `AtmoShaderConstants` | `DEFAULT_STEPS = 12`, `DEFAULT_LIGHT_STEPS = 4` |
| No hay un enum `atmoQuality` en la app | grep de `atmoQuality` | sin símbolo; hoy solo overrides de pasos |
| El checker está congelado | `docs/r4/tools/R4_DISPLAY_CHECK_v6.py` | sha256 `5a48cb18…` |
| La matriz de gates vive en R0 | `docs/r0/TEST_GATES.md` | existe; no hay `docs/TEST_GATES.md` en la raíz |

Consecuencia de diseño: **R4 no necesita una ruta de red nueva**. El replay
cambia la época con la que se lee la historia ya cacheada; no vuelve a pedir
datos al servidor ni reescribe `updated`/TTL.

---

## 3. Oráculo web: pin y semántica exacta

Pin: `8cdc3348ab087d10dd43807af217da4e246d996d`.

| Fichero del oráculo | Uso en R4 |
|---|---|
| `src/components/ionosphere/types.ts` | funciones puras, defaults, tipos de settings |
| `src/lib/iono/replay.test.ts` | tests espejo de replay e historia |
| `src/components/ionosphere/hud/TimeBar.tsx` | contrato de UI y controles |
| `src/components/ionosphere/builders.ts` | malla/volumen y constantes de raymarch |
| `src/lib/iono/densityVolume.ts` | layout, rejilla, alturas, rango logarítmico |
| `src/lib/iono/slice.ts` | rejilla de cortina y endpoints |
| `src/components/ionosphere/IonosphereScene.tsx` | cableado de época, cortina e iso-bands |

### 3.1 Funciones puras que se portan

| Función | Semántica fijada | Valores de referencia |
|---|---|---|
| `replayAdvance` | `next = minutesBack - dt*speed*20`; si `next <= 0`, vuelve a `maxMin` con loop o a `0` sin loop; si no, `min(maxMin, next)`. 1x = 20 min/s. | `1000,1,0.5` → `990`; `4x` → `960`; `5` con loop → `10080`; `5` sin loop → `0`; `20000` → `10080` |
| `replaySliderOffset` | `maxReplayMin - position`; es su propia inversa. La izquierda es `-max` y la derecha es «ahora». | `10080,10080` → `0`; `10080,0` → `10080`; `9960` → `120` |
| `timeBarEffectiveMinutes` | Solo resta minutos en `replay`; en `live` la época efectiva es 0. | `("replay",120)` → `120`; `("live",120)` → `0` |
| `timeBarClockLabel` | `nowMs == 0` → `"--:--:--"`; si no, `HH:MM UTC` de `nowMs - effMin*60_000`. | `T=2026-10-19T13:12:56Z` → `13:12 UTC`; con 120 → `11:12 UTC` |
| `sampleStationAt` | Sin historia → `foF2` actual; antes del primer punto → primero; después del último → último; búsqueda binaria + lerp; si un extremo es `<= 0`, toma el positivo. | ver §3.2 |

Defaults del oráculo que R4 debe respetar:

- `timeMode = "live"`, `replayMinutes = 0`, `replayPlaying = false`.
- `replaySpeed = 2`, `replayLoop = true`.
- Rango de replay: `0..10080` minutos (168 h).
- TimeBar: paso de 5 minutos; velocidades `[0.5, 1, 2, 4, 8]`.
- `showSlice = false`, `showVolume = false`, `showIso = false`,
  `volumeMode = "density"`, `volumeOpacity = 1.0`.
- El clamp de `pointSize` ya se cerró en R3.4; R4 no lo vuelve a portar.

### 3.2 Casos de `sampleStationAt`

Con `T0 = 2026-10-05T12:00:00Z` y una serie dispersa
`(0 h, 6)`, `(9 h, 9)`, `(30 h, 5)`, `(150 h, 8)`:

| Caso | Esperado |
|---|---|
| `T0 - 1 h` | `6` (clamp al primero) |
| `T0 + 200 h` | `8` (clamp al último) |
| `T0 + 19,5 h` | `7` (lerp en hueco de 9 h a 30 h) |
| `T0 + 30 h` | `5` (valor exacto) |
| estación sin historia, `foF2 = 7.5` | `7.5` |
| hueco `(0 h, 0)` y `(10 h, 8)` en `T0 + 5 h` | `8` (toma el positivo) |

La serie es **dispersa** (ionogramas, no cadencia fija). El lerp rellena huecos;
no se debe presentar un valor interpolado como medición puntual. La UI puede
declarar la época y la estación, pero no inventar una observación donde no la
hay.

---

## 4. Contrato de implementación

### 4.1 Época y reloj

- Época efectiva: `nowMs - timeBarEffectiveMinutes(timeMode, replayMinutes) *
  60_000`. `nowMs` se inyecta; no se llama `System.currentTimeMillis()` dentro de
  las funciones puras.
- En `live`, la época es «ahora» aunque `replayMinutes` conserve un valor.
- El reloj se muestra en UTC con formato `HH:MM`, no en hora local.
- El tick de reproducción usa el `dt` real transcurrido y la fórmula del
  oráculo; no se avanza por frame asumido.
- Scrub y play comparten el mismo estado; no puede haber dos relojes.

### 4.2 Estado y persistencia

El estado mínimo es `timeMode`, `replayMinutes`, `replayPlaying`,
`replaySpeed`, `replayLoop`, con los defaults de §3.1.

Reglas de saneado, antes de persistir o aplicar:

- `replayMinutes`: finito, `0..10080`; fuera de rango se recorta.
- `replaySpeed`: solo los valores de la lista del TimeBar; desconocido → `2`.
- `replayPlaying`: booleano; al volver a LIVE se pone `false`.
- `replayLoop`: booleano.
- `timeMode`: `live`/`replay`; desconocido → `live`.
- Persistencia con las mismas claves y rangos que la web, sin inventar nombres
  nuevos. La UI nunca escribe un estado que el núcleo puro no acepte.

### 4.3 Lectura de historia

- `sampleStationAt` es pura y no muta `IonosondeStation`.
- El timestamp ISO del modelo se normaliza a UTC antes de parsear (`Z` si falta),
  igual que el oráculo.
- La época de replay se aplica a la capa de estaciones y a los valores derivados
  que la web samplea en `IonosphereScene.tsx:1658-1663`; no se aplica a `updated`
  ni a la edad de caché.
- Sin historia no hay error: se usa `foF2` actual, como el oráculo.
- No se añaden fetch, caché nueva ni mutación de `data/**`.

### 4.4 Volumen Ne(h) y cortina

Contrato del volumen, espejo de `densityVolume.ts`:

- Rejilla `72 × 72 × 48` (lon × lat × alt).
- Alturas `60..500 km`, con `altMin + (altMax-altMin) * (i/(n-1))^1.5`.
- Dato `log10(Ne)` normalizado en `[8, 12.5]`; `VOL_PEAK_FLOOR = 8.5`.
- Layout `data[(altIdx*latRes + latIdx)*lonRes + lonIdx]`; `lonIdx 0 = -180`,
  `latIdx 0 = sur`, `altIdx 0 = altMin`.
- `layer` guarda la rama ganadora (`0=D, 1=E, 2=F1, 3=F2`).
- La cortina usa `SEG 64 × LVL 20`, slerp entre endpoints, viridis y alfa
  proporcional a la densidad; sus endpoints y su clave de rebuild se declaran
  antes de medir.
- El volumen y la cortina se construyen desde perfiles ya disponibles; no se
  inventan perfiles ni se rellena una estación sin datos.

### 4.5 Calidad y defaults

- `showSlice`, `showVolume` y `showIso` empiezan en `false`, como el oráculo.
- El raymarch usa 64 pasos como referencia del oráculo. Cualquier reducción por
  `atmoQuality` se pre-registra en el PR de R4.6 **antes** de medir; no se elige
  después de ver el resultado.
- `atmoQuality` solo gobierna el coste del volumen. No cambia los pasos de la
  atmósfera ya cerrados (`12/4`) ni reabre el gate de R3.4.
- Los overrides explícitos de pasos de atmósfera siguen funcionando; la calidad
  no los pisa.
- `density` y `chapman` son modos del mismo volumen; `chapman` no cambia la
  rejilla ni el layout.

---

## 5. Slices

Regla: **un slice = un PR pequeño**, con CI verde, revisión hunk-a-hunk de GLM
y gate de dispositivo de David antes del merge. Ningún slice se mergea por este
documento.

| Slice | Contenido | Fuera | Gate de salida |
|---|---|---|---|
| **R4.0** | Este plan + fila de R4 en `docs/TASKS.md` | código, ramas de feature | PR docs-only; CI 3/3; sin merge sin GO |
| **R4.1** | Núcleo puro de replay + settings saneados + tests espejo | UI, GL | JUnit exacto y mutantes |
| **R4.2** | TimeBar, LIVE/REPLAY, scrub, play/pause, velocidad, loop | datos nuevos | dispositivo + estado estable |
| **R4.3** | `sampleStationAt` + época de replay en estaciones/HUD | historias sintéticas | JUnit + lado a lado |
| **R4.4** | Modelo Ne(h) y volumen puro (constantes, layout, perfil) | GL, cortina | JUnit de volumen/perfil |
| **R4.5** | Cortina Ne(h) + iso-bands, toggles off por defecto | raymarch | visual + FPS con capa ON |
| **R4.6** | Raymarch `density`/`chapman` tras `atmoQuality` | NDK | shader + FPS + defaults off |
| **R4.7** | ADR de decisión NDK-MSIS y licencia | implementación NDK | decisión firmada |
| **R4.8** | Cierre: paridad de replay y batería 30 min | features nuevas | informe + CI |

### R4.0 — Planificación (este PR)

- Añade este documento y actualiza la fila R4 de `docs/TASKS.md` a
  «planificada», sin declararla cerrada.
- No crea ramas de código, no toca `app/`, `data/**`, `.github/**` ni el checker.
- **DoD**: solo docs, CI 3/3, revisión de GLM. El merge sigue requiriendo GO de
  David.

### R4.1 — Núcleo puro de replay

- Implementa `replayAdvance`, `replaySliderOffset`,
  `timeBarEffectiveMinutes`, `timeBarClockLabel` y el saneado de settings.
- Porta `replay.test.ts` completo en lo que corresponde a estas funciones.
- Añade mutantes locales: `20 → 10`, `<= 0 → < 0`, quitar loop, quitar clamp,
  invertir el slider, restar en LIVE. Cada mutante debe romper al menos un test.
- **No** añade UI, GL ni red.
- **DoD**: JUnit verde; paridad numérica exacta; CI 3/3.

### R4.2 — TimeBar y reproducción

- Añade la barra con toggle LIVE/REPLAY, slider 0..10080 paso 5, reloj UTC,
  play/pause, velocidad `[0.5,1,2,4,8]`, loop y reset a LIVE.
- El slider usa `replaySliderOffset` en ambos sentidos, sin doble conversión.
- El tick usa `dt` real; al llegar al presente aplica loop o se detiene según
  `replayLoop`.
- Persiste solo las claves de §4.2, con saneado.
- **DoD**: en dispositivo, LIVE y REPLAY conmutan sin saltos; el pulgar no se
  mueve al soltar; el reloj coincide con la época; no hay deriva tras 5 min.
- **Evidencia**: captura del TimeBar y acta de interacción; sin captura no hay
  gate.

### R4.3 — Muestreo de historia

- Porta `sampleStationAt` y lo usa en la capa de estaciones y en los valores
  derivados que dependen de la época.
- Prueba los seis casos de §3.2, incluida la serie dispersa de 168 h.
- Declara en la UI, cuando corresponda, que el valor es de la época de replay;
  no afirma una medición puntual en un hueco interpolado.
- **DoD**: los valores coinciden con el oráculo para la misma estación y la
  misma época; sin historia se ve el `foF2` actual; no hay fetch nuevo.

### R4.4 — Modelo Ne(h) y volumen

- Porta constantes, layout, curva de alturas, normalización logarítmica,
  `volumeIndex`, `volumeAltKm` y el perfil de capas.
- Tests espejo de `densityVolume.test.ts` y `layerProfile.test.ts` para la parte
  pura; sin GL.
- **DoD**: una rejilla de prueba tiene las mismas dimensiones, índices y valores
  que el oráculo; los bordes y los huecos (`<= 0`) quedan cubiertos.

### R4.5 — Cortina e iso-bands

- Añade la cortina `SEG 64 × LVL 20` y las iso-bands con `showIso` off por
  defecto.
- Rebuild por clave de endpoints + stamp; early-out si la clave no cambia.
- **DoD**: con la capa ON, la cortina coincide con la web en la misma época y
  encuadre; con OFF, el frame y los FPS vuelven al baseline; sin fugas de
  textura/geometría al alternar.
- **Evidencia**: captura lado a lado declarada como composición, no como captura
  literal; FPS con ON/OFF.

### R4.6 — Raymarch density/chapman

- Añade el volumen raymarch con `showVolume` off por defecto, `volumeMode`
  `density`/`chapman`, opacidad y pasos.
- La tabla de pasos por `atmoQuality` se escribe en el PR antes de medir.
- **DoD**: compila en el dispositivo; `density` y `chapman` conmutan sin
  recompilar; con OFF no hay coste; con ON se cumple el presupuesto de §6.
- **Evidencia**: FPS ON/OFF por modo y calidad, más captura de cada modo.

### R4.7 — Decisión NDK-MSIS

- Se ejecuta después de R4.4 y R4.6, cuando ya se sabe si el perfil de capas
  cubre la cortina y el volumen con paridad aceptable.
- Si la cobertura es suficiente: **NO** se añade NDK; se documenta el descarte.
- Si hace falta fondo de modelo: ADR con ABI, licencia, tamaño, build,
  fallback y impacto en FPS. La implementación, si se autoriza, va en un PR
  aparte y no dentro de R4.7.
- **DoD**: decisión explícita de David + dictamen de GLM; licencia declarada;
  ninguna dependencia nativa entra sin ese ADR.

### R4.8 — Cierre de fase

- Repite la paridad de replay contra el pin web y recoge el informe de batería
  de 30 min.
- No añade features; solo corrige defectos encontrados por los gates.
- **DoD**: §7 completo, CI 3/3, informe de batería y paridad, y revisión final
  de GLM. El cierre de la fase lo autoriza David.

---

## 6. Puertas puras (JUnit)

| Gate | Qué fija | Cobertura mínima |
|---|---|---|
| **G1** | `replayAdvance` | default 2; 1x/4x; loop; sin loop; clamp superior; `dt` fraccionario |
| **G2** | `replaySliderOffset` | extremos; caso 9960→120; ida y vuelta; invariancia |
| **G3** | `timeBarEffectiveMinutes` | replay resta; live no resta; live con historial largo |
| **G4** | `timeBarClockLabel` | `nowMs=0`; LIVE; REPLAY −2 h; UTC exacto |
| **G5** | `sampleStationAt` | clamp; lerp; exacto; sin historia; cero; serie 168 h |
| **G6** | Settings | defaults; rangos; valores desconocidos; no finito |
| **G7** | Volumen/perfil | `72×72×48`; alturas; rango log; índice; capas; bordes |
| **G8** | Cortina | endpoints; slerp; clave de rebuild; rejilla `64×20` |

Disciplina de mutantes (TEST_GATES §2): los mutantes se ejecutan en local y se
reportan en el PR; **no se commitean**. Un guard sin test que lo mate no está
cubierto.

---

## 7. Gates de dispositivo y evidencia

Dispositivo de referencia: Xiaomi Mi 10 Lite 5G, Android 11 / API 30,
Snapdragon 765G, Adreno 620 (GLES 3.2), 6 GB RAM.

### 7.1 FPS

- Medición con SurfaceFlinger, como en R3.4; no con FramePacer. La limitación se
  declara en el acta.
- Dispositivo desenchufado, primer plano, sin interacción durante la ventana.
- Seis ventanas sostenidas.
- Umbral de R4: **medium ≥ 29,5 fps** (decisión T4 de R3.4) y **low ≥ 20 fps**
  (TEST_GATES §5). R4 no reabre esos números.
- Cada capa se mide OFF y ON, en la misma vista y época. Una regresión que baje
  del umbral bloquea el slice; no se compensa con otra capa.

### 7.2 Batería

- Sesión de **30 min** en REPLAY, pantalla encendida, primer plano, sin carga,
  con la capa R4 activa que corresponda.
- Se registran: porcentaje inicial/final, %/h, temperatura, FPS por ventana,
  minuto de replay alcanzado, crashes/ANR y si hubo throttling.
- **Pasa** si la sesión termina, la reproducción sigue avanzando y no hay
  crash/ANR ni apagado térmico. El porcentaje se **informa**, no se convierte en
  un umbral inventado.
- Si hay throttling, se declara y se repite con la calidad que corresponda; no
  se oculta.

### 7.3 Paridad visual

- Misma época, mismo encuadre, mismo tamaño de framebuffer y mismo pin.
- Lado a lado **declarado como composición**, como en R3.4; no es una captura
  literal de un único framebuffer.
- La procedencia de cada panel se certifica; el HUD se mantiene aparte.
- GLM revisa los pares; David valida en el dispositivo.

### 7.4 Atribución

- APK del commit exacto (`head_sha`), `pm path` y hash del APK en el acta.
- Sin APK atribuible no hay gate, aunque las capturas existan.

---

## 8. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Historia dispersa tratada como cadencia fija | Suavizado falso | `sampleStationAt` exacto; declarar huecos; no afirmar medición puntual |
| Duplicar historia por estación | RAM y GC | Referenciar la lista existente; no copiar en cada tick |
| Tick por frame asumido | Deriva del reloj | `dt` real inyectado; tests con `dt` fraccionario |
| Slider con doble conversión | El pulgar salta | `replaySliderOffset` es involutiva; test ida/vuelta |
| Scrub que dispara fetch | Red y jank | La época solo lee caché; no hay ruta de red en replay |
| Volumen 3D caro en Adreno 620 | Caída de FPS | Defaults OFF; calidad pre-registrada; medir ON/OFF |
| Cortina con fuga de textura/geometría | Memoria y crash | Dispose en rebuild/drop; alternar 20 veces en el gate |
| `atmoQuality` pisando overrides | Regresión R3.4 | La calidad solo gobierna el volumen; overrides intactos |
| NDK-MSIS entra sin licencia | Bloqueo legal/distribución | ADR obligatorio; sin ADR no hay dependencia |
| Confundir R4 con paridad-R5 | Alcance y evidencia falsos | Aviso de nomenclatura en este plan y en `TASKS.md` |
| Medir con USB enchufado | Techo falso | Desenchufado y declarado, como T4 |
| Captura sin SHA | Evidencia no atribuible | `head_sha` + hash de APK en el acta |

---

## 9. Secuencia y política de PRs

1. **R4.0** (este PR): plan docs-only. Sin código.
2. **R4.1** → **R4.2** → **R4.3**: tiempo y historia, sin GL.
3. **R4.4** → **R4.5** → **R4.6**: profundidad, con la cortina después del
   volumen y el raymarch al final.
4. **R4.7**: decisión NDK, cuando los datos de R4.4/R4.6 existan.
5. **R4.8**: cierre, paridad y batería.

Cada PR:

- Sale del `main` vigente, no de otro slice sin mergear.
- Toca solo su frontera; `data/**`, `.github/**` y el checker quedan fuera.
- Pasa CI 3/3 (`assembleDebug`, `unitTest`, `lint`).
- Recibe revisión hunk-a-hunk de GLM.
- Recibe gate de dispositivo de David.
- Solo lo mergea David.

---

## 10. DoD de la fase R4

R4 se declara cerrada solo cuando:

1. **E1** está portado con tests espejo exactos y mutantes cubiertos.
2. **E2** funciona en dispositivo: LIVE/REPLAY, scrub, play/pause, velocidad,
   loop y reset, sin deriva.
3. **E3** coincide con el oráculo en la serie dispersa de 168 h y en los casos
   límite.
4. **E4** reproduce dimensiones, layout y perfil del volumen.
5. **E5** muestra cortina e iso-bands con paridad declarada y sin fugas.
6. **E6** conmuta `density`/`chapman` detrás de `atmoQuality`, con defaults OFF
   y presupuesto FPS.
7. **E7** tiene decisión NDK-MSIS explícita y licencia declarada.
8. **E8** entrega informe de batería de 30 min y paridad de replay.
9. CI 3/3 en el PR final y revisión de GLM.
10. David autoriza el cierre.

Si algún punto no se cumple, R4 sigue abierta; no se sustituye por una captura
ni por una declaración sin evidencia.

---

## 11. Decisiones abiertas (para David/GLM, no para implementar por sorpresa)

| ID | Pregunta | Propuesta de este plan |
|---|---|---|
| **Q1** | ¿El volumen y la cortina necesitan NDK-MSIS o basta el perfil de capas? | Diferir a R4.7; decidir con la paridad medida, no antes |
| **Q2** | ¿Qué tabla exacta de pasos usa `atmoQuality`? | Pre-registrarla en R4.6 antes de medir; 64 es la referencia del oráculo |
| **Q3** | ¿Se persisten `timeMode`/`replayMinutes` entre sesiones? | Sí, con las claves y el saneado de §4.2, como la web |
| **Q4** | ¿Las iso-bands incluyen etiquetas en R4? | Off por defecto; etiquetas solo si el gate visual las exige, sin ampliar el slice |
| **Q5** | ¿El informe de batería exige un %/h máximo? | No inventarlo; reportar %/h y usar «sesión completa sin throttling» como criterio R4 |

---

## 12. Estado de este PR

- **Toca**: `docs/r4/PLAN-R4-TIEMPO-PROFUNDIDAD.md` y la fila R4 de
  `docs/TASKS.md`.
- **No toca**: `app/`, `data/**`, `.github/**`, checker congelado, workflows.
- **No crea** rama de feature, no compila APK, no mide dispositivo.
- **No mergea**: pendiente de dictamen de GLM y GO de David.

La planificación queda abierta con este documento; la fase no.
