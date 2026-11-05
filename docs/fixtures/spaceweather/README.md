# Fixtures SpaceWeather - 3-Day Forecast

## Procedencia

`https://services.swpc.noaa.gov/text/3-day-forecast.txt` — descargado
2026-11-05 09:36 UTC (GET con `User-Agent: IonosphereLive3D-Android/0.2`),
1957 bytes tal como lo emite el servicio.

**Licencia**: obra del Gobierno de EE. UU. (NOAA/SWPC), dominio publico.

## Fidelidad

- Texto plano tal cual (`3-day-forecast.txt`), 1957 caracteres, sin
  alteración de contenido. Solo se normalizaron finales de línea del archivo
  de trabajo del repo (el servicio emite `\n`).
- El adapter consume este fixture en el test de parser sin red.

## Qué NO está aquí

Los otros 10 endpoints NOAA (JSON, hasta 2.5 MB) no se fijan como fixtures
completos: son vivos y enormes. El parser de forecast es la única pieza
field-by-field (texto plano), igual que `DstParser`/`IonosondeParser` en su
momento. El adapter de dos niveles los cubre con `source` por bloque y
degradación honesta.
