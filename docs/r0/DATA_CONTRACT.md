# DATA_CONTRACT — v1 (R0, GLM)

**Estado**: BASELINE GLM R0. **Autoría**: GLM, canal GLM→David, 2026-11-02.
**Responde a**: Issue #1 (deliverable 2, pregunta 4).

## 1. Principio

El móvil llama **directo a las fuentes públicas, autocontenido** (aclaración de David, 2026-11-02: no hay web desplegada; el móvil se comporta como el C++). Las rutas `/api` de `ionosphere-live-3d-web` son la **especificación de referencia** (código + tests vitest), **no una dependencia runtime**. El contrato compartido = esquemas + fixtures dorados.

## 2. Fuente de verdad de esquemas

`src/components/ionosphere/types.ts` (web) — espejo documentado de los payloads (con referencias cruzadas a `App.cpp`). En Android:

1. `docs/contracts/*.schema.json` — JSON Schema generado y commiteado (documentación viva).
2. Modelos Kotlin espejo en `app/data/model/`.
3. **Fixtures dorados**: respuesta pública grabada (raw upstream) + payload normalizado esperado → validan los parsers Kotlin en CI y permitirían revalidar la web con los mismos ficheros.

Grabar respuestas de estas APIs públicas para tests está permitido. **Regla dura heredada: PROHIBIDO ingerir fixtures SAO del Observatorio del Ebro sin permiso** (el móvil v1 no usa esa vía; la regla aplica a cualquier uso futuro).

## 3. Tabla de endpoints (upstreams y TTL verificados hoy desde el código fuente de las rutas web)

| Endpoint | Upstream(s) públicos | TTL móvil (espejo web) |
|---|---|---|
| space-weather | SWPC + GOES-18/19 (S3) + Kyoto | 90 s |
| aurora | services.swpc.noaa.gov (ovation) | 5 min |
| lightning | GOES S3 + api.weather.gov | 5 min |
| wspr | WSPRnet | 5 min |
| msis | modelo local (inputs SWPC) | 5 min (inputs) |
| cme | NASA CCMC DONKI (WSA-ENLIL) | 10 min |
| flares | NASA CCMC DONKI (FLR) | 10 min |
| ionosondes | GIRO DIDBase (lgdc.uml.edu) + kc2g failover-only | datos 10 min · catálogo 24 h · extras anclas 60 min · anti-rebaño 5 min |
| dst | Kyoto WDC realtime | 15 min |
| ae | Kyoto WDC realtime (lag documentado en `ageDays`) | 15 min |
| solar-image | SDO (latest_512) | 15 min |
| rbe | NASA CCMC DONKI (RBE) | 30 min |
| satellites | Celestrak/SatNOGs (TLE) + propagación sgp4 en cliente | catálogo 6 h |
| voyager | JPL Horizons | 6 h |

Al portar cada endpoint (R2+), Beam adjunta en el PR la constante TTL extraída de la `route.ts` correspondiente; GLM la contrasta. Si hay discrepancia con esta tabla, gana el código web y se actualiza esta tabla.

## 4. Semántica de degradación (no negociable)

- Precedencia declarada por endpoint, idéntica a la web. Ejemplo ionosondes: **live > kc2g > caché > model**, con `source` dinámico en el payload (nunca constante).
- **Nunca se sirve "vacío" como bueno. Nunca datos inventados.** Todo panel muestra fuente + edad del dato. `stale` > 6 h en estaciones. Offline = último-bueno con edad visible y estado "offline" declarado.
- Ventana de replay: 168 h de historia (cap web: 2500 filas/estación; en móvil puede reducirse por memoria — si se cambia, se DECLARA en el payload o la UI).

## 5. Comportamiento de red móvil

- Mismos timeouts que la web (12–25 s) con cancelación (coroutines). Un fallo HTTP nunca crashea: degrada.
- UA identificativo: `IonosphereLive3D-Android/0.x (https://github.com/valledemena/ionosphere-live-3d-android)`.
- Cortesía upstream idéntica (ionosondes: 6 workers, 250 ms entre peticiones). El modo `full=1` no aplica en móvil (era diagnóstico de servidor).
- **Foreground-only en v1**: sin polling en background. WorkManager opcional en el futuro, con gate de batería de David.

## 6. Pendientes que cierra este contrato en R2

- Validar DONKI sin clave desde la IP de un dispositivo real (la web lo llama hoy sin `api_key`, verificado; el límite por IP de cliente puede diferir).
- Confirmar la tabla completa endpoint-a-endpoint contra cada `route.ts` al portar.
