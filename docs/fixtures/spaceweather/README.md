# Fixtures SpaceWeather - 3-Day Forecast

## Procedencia

`https://services.swpc.noaa.gov/text/3-day-forecast.txt` - descargado
2026-11-05 con `User-Agent: IonosphereLive3D-Android/0.2`.

**Fecha del issue capturado**: `:Issued: 2026 Nov 05 0030 UTC`.

- **1890 bytes** en la primera captura del `bed55f8` y ahora corregido:
  **1957 bytes, byte-exact del servicio** (verificado por `git hash-object`
  contra una re-harvest curl a 1957 con **0 CRs** y hash
  `048c8aaebc0eb937a4c2886ef0962b1b506d5ffd`), con **0 CRs**. El delta de
  67 bytes de la version anterior provenia de re-tipear el texto a mano
  (los espacios finales de la tabla Kp, filas 00-03UT a 21-00UT, se
  perdian); NO de finales de linea - el servicio emite `\n` puro, sin CRs.

**Licencia**: obra del Gobierno de EE. UU. (NOAA/SWPC), dominio publico.

## Fidelidad

- Texto plano tal cual (`3-day-forecast.txt`), 1957 bytes, sin alteracion
  de contenido. Es un snapshot del issue vigente en la hora de captura; el
  servicio lo rotacion ~2x/dia (0030/1230 UTC) - un re-harvest posterior
  capturaria el issue siguiente, no este.
- El adapter consume este fixture en el test de parser sin red (y en el
  smoke del stub).

## Que NO esta aqui

Los otros 10 endpoints NOAA (JSON, hasta 2.5 MB) no se fijan como fixtures
completos: son vivos y enormes. El parser de forecast es la unica pieza
field-by-field (texto plano), igual que `DstParser`/`IonosondeParser` en su
momento. El adapter de dos niveles los cubre con `source` por bloque y
degradacion honesta.
