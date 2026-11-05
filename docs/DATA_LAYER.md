# Capa de datos (R2) - como entra la informacion

El movil llama directo a las fuentes publicas, autocontenido. No necesita servidor desplegado - las rutas `/api` del repositorio web son especificacion de referencia, no runtime.

## Rutas cubiertas (R2)
- **space-weather** (90 s)
- **aurora** (5 min)
- **dst** (15 min)
- **ionosondes** (10 min datos / 24 h catalogo / 60 min extras / 5 min anti-herd)

## Arquitectura, TTL y degradación

Los adaptadores (`data/source/*`) devuelven `Outcome` (`Live` / `Cached` / `Failed`):
- Live: datos disponibles con source declarado.
- Cached: último-bueno cuando la red falla (stale si pasó el TTL).
- Failed: sin red y sin cache.

Precedencia: live > cache > (model unavailable). `stale` > 6 h en estaciones.

UA identificativo: `IonosphereLive3D-Android/0.x (...)`. Timeouts 12-25 s con cancelacion por coroutines. Un fallo HTTP nunca crashea: degrada.

## Fixtures dorados

Los fixtures provienen de upstream real con licencia declarada en header (sin SAO/EB040 por permiso del Observatorio de l'Ebre).

## Referencia

Ver `docs/r0/DATA_CONTRACT.md`, `docs/r0/IMPLEMENTATION_SLICES.md` §R2.
