# H5 - SpaceWeather: donde vive el agregador (decision >= codigo)

Estado: **doc de decision para David**. No hay codigo aun. La leccion de los
anchors aplica: la premisa se fija antes de escribir, no despues.

## Linea base (espejo web, pinado)

- `src/app/api/space-weather/route.ts` - blob `de56407f` (verificado con
  `git hash-object` contra `ionosphere-live-3d-web` @ `aeeea38`).
- 276 lineas. 11 endpoints NOAA SWPC en paralelo por ciclo, agregados a un
  unico payload con 8 bloques (`kp`, `f107`, `wind`, `mag`, `xray`, `protons`,
  `sunspots`, `forecast`), cada uno con `source` dinamico.
- El modelo Kotlin `data/model/SpaceWeatherData.kt` en main ya es espejo exacto
  de esa respuesta. El TTL `90 s` ya esta en `DataTtl.SPACE_WEATHER_MS`.

## Medicion real (2026-11-05, gzip, transferido, snapshot unica)

| Endpoint | Bytes | Uso en el agregador |
|---|---:|---|
| `/json/rtsw/rtsw_wind_1m.json` | **2 555 865** | solo `proton_density` (una fila) |
| `/json/rtsw/rtsw_mag_1m.json` | **1 459 839** | serie Bx ~200 pts + bxNow |
| `/json/goes/primary/xrays-1-day.json` | **647 551** | serie 1 dia (~1440 pts) |
| `/json/goes/primary/integral-protons-1-day.json` | **242 315** | serie >=10 MeV + valor |
| `/json/goes/primary/xrays-6-hour.json` | 155 593 | serie 6 h (~360 pts) |
| `/json/sunspot_report.json` | 129 027 | regiones activas unicas |
| `/json/planetary_k_index_1m.json` | 27 925 | Kp + serie 96 pts |
| `/json/f107_cm_flux.json` | 22 661 | F10.7 + serie 72 pts |
| `/text/3-day-forecast.txt` | 794 | maxKp + rationale |
| `/products/summary/solar-wind-speed.json` | 59 | velocidad |
| `/products/summary/solar-wind-mag-field.json` | 60 | bt/bz |

**Total: ~5.46 MB por ciclo de 90 s.** El servidor web paga eso una vez para
todos sus usuarios. Un cliente movil directo lo pagaria por si mismo, a cada
refresco, en WiFi o en datos moviles.

---

## Opciones

### A - Directo fiel al espejo (11 requests / ciclo)

Un `SpaceWeatherAdapter` con las 11 llamadas en paralelo, igual que la ruta web.

- A favor: maxima fidelidad; `source` por bloque hereda la semantica web 1:1.
- En contra: **~5.5 MB por refresco** (inaceptable en movil); 11 requests;
  re-trabajo `selectMagSource` (libra `parker` del web) obligatorio desde el
  dia uno.

### B - Via proxy (el movil llama a la ruta del web)

1 request contra el deploy web.

- A favor: 1 request; agregador ya escrito.
- En contra: rompe la premisa del proyecto ("autonoma, no necesita servidor");
  el movil casado con un deploy (costo, Mantenimiento, SPOF); contradice
  README y `DATA_LAYER.md` que ya prometen fuentes directas; con el app en
  mano, sin deploy activo, no hay datos.

### C - Directo a dos niveles (recomendada)

Misma Decision A en estructura (directo a NOAA, mismo modelo, `source` por
bloque), pero el ciclo separa por peso:

- **Nivel 1** - ciclo de 90 s: kp, f107, speed summary, mag-field summary,
  xray 6 h, forecast. **~230 KB por ciclo.**
- **Nivel 2** - TTL propio de 60 min (es dato que en la practica no cambia
  minuto a minuto para el HUD): rtsw_wind (densidad), rtsw_mag (Bx),
  xray 1 dia, protones, manchas. Se piden detras, sin bloquear Nivel 1.
- Mientras Nivel 2 no llega, sus bloques quedan honestamente
  `source: "unavailable"` (contrato existente, cero dato inventado). Es el
  mismo patron de degradacion de `IonosondeAdapter` (extras anclas).

Tradeoffs declarados de C: la fidelidad temporal de los bloques Nivel 2 se
reduce de 90 s a 60 min (los valores que usan: densidad, Bx, series profundas,
protones, manchas - ninguno alimenta al globo por segundo); dos colas en vez
de una (+ test de ambas); Nivel 1 adelgaza el anti-horda de 11 a 6 requests
por ciclo.

## Recomendacion

**C.** B es la unica que contradice la premisa del proyecto - y A paga 5.5 MB
por refresco por defendibilidad de puerto. C conserva el modelo completo,
degrada honesto y el consumo de datos queda en la cohorte de Ionosonde
(~230 KB/ciclo de vida).

El nivel 2 de 60 min se decide si una serie profunda acaba alimentando el HUD
(R3); entonces se mueve de nivel, no se re-escribe nada.

---

## Decision de David

- [ ] A  (directo fiel, ~5.5 MB/ciclo)
- [ ] B  (via ruta web proxy)
- [ ] C  (directo a dos niveles, ~230 KB ciclo + nivel profundo 60 min)

Nota de implementacion posterior a la decision: el adapter trae parser propio
para el forecast (texto plano, regex `greatest expected 3 hr Kp` /
`Rationale:`), tests sin red con fixtures licenciados y el mismo regimen de
`Outcome`/cache del resto de la casa.
