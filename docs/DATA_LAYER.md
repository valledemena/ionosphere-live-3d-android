# Capa de datos (R2) - como entra la informacion

El movil llama directo a las fuentes publicas, autocontenido. No necesita servidor desplegado - las rutas `/api` del repositorio web son especificacion de referencia, no runtime.

## Rutas cubiertas (R2)
- **space-weather** (90 s)
- **aurora** (5 min)
- **dst** (15 min)
- **ionosondes** (10 min datos / 24 h catalogo / 60 min extras / 5 min anti-herd)

## Arquitectura, TTL y degradacion

Los adaptadores (`data/source/*`) devuelven `Outcome` (`Live` / `Cached` / `Failed`):
- Live: datos disponibles con source declarado.
- Cached: ultimo-bueno cuando la red falla (stale si paso el TTL).
- Failed: sin red y sin cache.

Precedencia: live > cache > (model unavailable).

UA identificativo: `IonosphereLive3D-Android/0.x (...)`. Timeouts 12-25 s con cancelacion por coroutines. Un fallo HTTP nunca crashea: degrada.

## Fixtures dorados

Los fixtures provienen de upstream real con licencia declarada en header (sin SAO/EB040 por permiso del Observatorio de l'Ebre).

## Referencia

Ver `docs/r0/DATA_CONTRACT.md`, `docs/r0/IMPLEMENTATION_SLICES.md` §R2.

## kc2g failover (R3.4-compatible backlog)
- Cuarto tier de ionosondas: live > kc2g > cache (techo 24 h) > model/down.
- Fuente declarada en payload: `kc2g fallback` (no hardcodeada en UI, B5).
- Endpoints: `prop.kc2g.com/api/stations.json` (array, 101 filas); consumo solo si la ronda LGDC queda sin estaciones (failover-only).
- Ficheros: `data/model/Kc2gData.kt`, `data/model/Kc2gDataTest.kt`, `data/source/ionosondes/IonosondeAdapter.kt`, `data/source/ionosondes/Kc2gFailoverTest.kt`; fixtures en `docs/fixtures/kc2g/`.
- Semantica S6: excepcion de transporte -> `Failed` (si sin cache); bodies no-ok -> `Live("unavailable")` (rama (B)); cache conservado; void cache run repeatedly does not survive store call.
- No toca manifest/build ni rendering.
