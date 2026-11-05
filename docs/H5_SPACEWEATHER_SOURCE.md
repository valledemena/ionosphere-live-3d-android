# H5 - SpaceWeather: donde vive el agregador (decision >= codigo)

Estado: **doc de decision para David**. No hay codigo aun. La leccion de los
anchors aplica: la premisa se fija antes de escribir, no despues.

> v2 (2026-11-05): v1 etiquetaba mal la medicion (tamanios crudos presentados
> como "gzip transferido"). Corregida por doble via independiente: curl
> `--compressed` contando bytes de transferencia, y endpoints con
> `Accept-Encoding: identity` para el crudo. Los numeros de transferencia
> coinciden con la medicion externa de auditoria (max delta 1 %). Gracias
> **a** la correccion: el argumento decisivo ya no es el peso en red, es el
> **coste de parseo** (gzip ahorra red, no CPU).

## Linea base (espejo web, pinado)

- `src/app/api/space-weather/route.ts` - blob `de56407f` (verificado con
  `git hash-object` contra `ionosphere-live-3d-web` @ `aeeea38`).
- 276 lineas. 11 endpoints NOAA SWPC en paralelo por ciclo, agregados a un
  unico payload con 8 bloques (`kp`, `f107`, `wind`, `mag`, `xray`, `protons`,
  `sunspots`, `forecast`), cada uno con `source` dinamico.
- El modelo Kotlin `data/model/SpaceWeatherData.kt` en main ya es espejo exacto
  de esa respuesta. El TTL `90 s` ya esta en `DataTtl.SPACE_WEATHER_MS`.

## Medicion real (2026-11-05, doble columna: crudo/parseo vs wire gzip)

| Endpoint | crudo (parseo) | wire gzip | Uso en el agregador |
|---|---:|---:|---|
| `/json/rtsw/rtsw_wind_1m.json` | **2 555 879** | **94 663** | solo `proton_density` (una fila) |
| `/json/rtsw/rtsw_mag_1m.json` | **1 459 462** | **147 025** | serie Bx ~200 pts + bxNow |
| `/json/goes/primary/xrays-1-day.json` | **647 542** | **97 646** | serie 1 dia (~1440 pts) |
| `/json/goes/primary/integral-protons-1-day.json` | **241 468** | **30 167** | serie >=10 MeV + valor |
| `/json/goes/primary/xrays-6-hour.json` | 155 595 | 21 512 | serie 6 h (~360 pts) |
| `/json/sunspot_report.json` | 129 027 | 10 208 | regiones activas unicas |
| `/json/planetary_k_index_1m.json` | 27 925 | 1 368 | Kp + serie 96 pts |
| `/json/f107_cm_flux.json` | 22 661 | 1 217 | F10.7 + serie 72 pts |
| `/text/3-day-forecast.txt` | 1 957 | 794 | maxKp + rationale |
| `/products/summary/solar-wind-speed.json` | 59 | 59 | velocidad |
| `/products/summary/solar-wind-mag-field.json` | 60 | 60 | bt/bz |

**Totales: ~5.24 MB crudo que hay que parsear / ~405 KB en red por ciclo.**

OkHttp acepta gzip por defecto, asi que la columna "wire gzip" es lo que el
movil transfiere. Pero la columna "crudo" es lo que Ktor/OkHttp **entrega al
parser**: 2.5 MB de JSON que se deserializan para extraer UN valor. El costo
es CPU/GC/bateria, no red - y ese no lo ahorra gzip.

## Aritmetica por hora (ciclo 90 s, app en primer plano, 40 ciclos)

| | Opcion A (11/ciclo) | Opcion C (dos niveles) |
|---|---:|---:|
| Red por ciclo | ~405 KB | ~25 KB (Nivel 1) |
| Red por hora | **~16.2 MB/h** | **~1.38 MB/h** |
| Parseo por ciclo | ~5.24 MB | ~0.21 MB |
| **Parseo por hora** | **~209 MB/h** | **~13.4 MB/h** |

Medido en crudo, fuera de la capa de compresion: A significa deserializar
~5 MB de JSON cada 90 segundos en un telefono. Eso es el argumento, no la red.

---

## Opciones

### A - Directo fiel al espejo (11 requests / ciclo)

Un `SpaceWeatherAdapter` con las 11 llamadas en paralelo, igual que la ruta web.

- A favor: maxima fidelidad; `source` por bloque hereda la semantica web 1:1.
- En contra: **~16 MB/h en red y ~209 MB/h de JSON parseado** en movil; 11
  requests; re-trabajo `selectMagSource` (libra `parker` del web) obligatorio
  desde el dia uno.

### B - Via proxy (el movil llama a la ruta del web)

1 request contra el deploy web.

- A favor: 1 request; agregador ya escrito.
- En contra: rompe la premisa del proyecto ("autonoma, no necesita servidor");
  el movil casado con un deploy (costo, mantenimiento, SPOF); contradice
  README y `DATA_LAYER.md` que ya prometen fuentes directas; con el app en
  mano, sin deploy activo, no hay datos.

### C - Directo a dos niveles (recomendada)

Misma opcion A en estructura (directo a NOAA, mismo modelo, `source` por
bloque), pero el ciclo separa por peso:

- **Nivel 1** - ciclo de 90 s: kp, f107, speed summary, mag-field summary,
  xray 6 h, forecast. **~25 KB en red / ~0.21 MB parseo por ciclo.**
- **Nivel 2** - TTL propio de 60 min: rtsw_wind (densidad), rtsw_mag (Bx),
  xray 1 dia, protones, manchas. Se piden detras, sin bloquear Nivel 1.
- Mientras Nivel 2 no llega, sus bloques quedan honestamente
  `source: "unavailable"` (contrato existente, cero dato inventado). Es el
  mismo patron de degradacion de `IonosondeAdapter` (extras anclas).

Tradeoffs declarados de C: la fidelidad temporal de los bloques Nivel 2 se
reduce de 90 s a 60 min (density, Bx, series profundas, protones, manchas -
ninguno alimenta al globo por segundo); dos colas en vez de una (+ test de
ambas); anti-horda pasa de 11 a 6 requests por ciclo.

## Recomendacion

**C.** B es la unica que contradice la premisa del proyecto. A paga ~209 MB/h
de solo parseo. C conserva el modelo completo, degrada honesto y el coste por
ciclo queda en la cohorte de Ionosonde.

El nivel 2 se re-lee con mas frecuencia si una serie profunda acaba
alimentando el HUD (R3); entonces se mueve de nivel, no se re-escribe nada.

---

## Decision de David

- [ ] A  (directo fiel, ~16 MB/h red / ~209 MB/h parseo)
- [ ] B  (via ruta web proxy)
- [ ] C  (directo a dos niveles, ~1.4 MB/h red / ~13 MB/h parseo)

Nota de implementacion posterior a la decision: el adapter trae parser propio
para el forecast (texto plano, regex `greatest expected 3 hr Kp` /
`Rationale:`), tests sin red con fixtures licenciados y el mismo regimen de
`Outcome`/cache del resto de la casa.
