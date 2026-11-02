# docs/r0 — gate de la baseline R0 (GLM)

Actualizado: 2026-11-02, tras el recibo del paquete `OK-3-R0-canonicos-faltantes.zip`.
Un artefacto es "verbatim" **solo** si su SHA256 coincide con el de la tabla.
No hay interpretación: el hash no se negocia.

## Gate: 8 de 8 — R0 cerrado

| # | Artefacto | SHA256 |
|---|---|---|
| 1 | `docs/r0/ARCHITECTURE_DECISION.md` | `487b1bb245dace60f3f7ee8e78e777a99ebb018aafe969364f537bddcbc50d7d` |
| 2 | `docs/r0/DATA_CONTRACT.md` | `82e846b2c972540e43f6bcdf19b0451e1f7a0b88490e231245e1c8b79612a439` |
| 3 | `docs/r0/IMPLEMENTATION_SLICES.md` | `967fc9d32e7763fffd9393ba4a6ed3ceb25e45fd2698d1cf7a1d73ba09814297` |
| 4 | `docs/r0/OWNERSHIP_AND_RELEASING.md` | `348ad7010e0ba7a5efcfb4395b9f07eebcf25c801f2e5255c75fed39ae665a90` |
| 5 | `docs/r0/TEST_GATES.md` (v1.1) | `b56c1cf26b391b16989b29ca3df376506a1924b608dac0575d3738df3d995d3f` |
| 6 | `docs/r0/relay-primitive.md` | `495a8550af7d3391727ab870e896f1505e3278e712de88c46f64b738499a3021` |
| 7 | `docs/GLM_R0_RESPONSE.md` | `523788fb4759b266558cdb835c6250da3d7037142bf841c8aaf6af0f7175fd0b` |
| 8 | Comentario del Issue #1 (`6577699347`) | `1a42f73992b96adfc985dc76d0172c1c88292ccb79cc2830fb1d8c4ea66e1e38` |

Los ocho canónicos están dentro y ninguno ha pasado por un script que genere o
re-codifique contenido.

## Verificación (cualquiera, sin las actas)

Fichero local:

```powershell
Get-FileHash docs\r0\ARCHITECTURE_DECISION.md -Algorithm SHA256
```

Blob tal como está en el remoto (Windows, sin `Out-String`, que corrompe):

```powershell
cmd /c "git show origin/main:docs/r0/ARCHITECTURE_DECISION.md > %TEMP%\a.blob"
Get-FileHash "$env:TEMP\a.blob" -Algorithm SHA256
```

Ambos deben dar el valor de la tabla. Si no coincide, no es verbatim y no se
commitea.

## Historial de entrega (por qué el gate llegó tarde)

La primera entrega del canal GLM incluyó los cinco documentos, pero la
transferencia a esta máquina fue **selectiva**: llegaron `OWNERSHIP`,
`TEST_GATES`, `relay-primitive` y `GLM_R0_RESPONSE`; los otros tres nunca
aparecieron en disco. Eso explica los commits con digests (`9376cda`,
`5bdf689` y previos): no había bytes que copiar, y un resumen con etiqueta
"GLM canonical" no es un original.

La segunda re-entrega fue también parcial (solo `ARCHITECTURE_DECISION.md`).
Resuelto con el paquete `OK-3-R0-canonicos-faltantes.zip`: los tres ficheros
extraídos del zip coinciden con el gate **byte a byte**, sin BOM.

Regla adoptada en la cadena: **una entrega no cuenta hasta que el receptor
confirma los hashes**. Es simétrica: obliga al que entrega y al que recibe.

## Errata registrada (2026-11-02)

En el recibo del commit `d374341` se describió `ARCHITECTURE_DECISION.md` como
"40 líneas". Son **57**. Causa: se contó con `Measure-Object -Line`, que descarta
las líneas en blanco (17 en ese fichero); el recuento correcto es el de
`Get-Content` o el del diff. El hash —que es lo que arbitra— era y es correcto.
Se deja anotado porque un número mal medido es un número mal medido, aunque el
artefacto fuera bueno.

## Siguiente

R0 cerrado. **R1 vive en el Issue #2**, con el alcance de
`docs/r0/IMPLEMENTATION_SLICES.md` §R1 y el gate de dispositivo de
`docs/r0/TEST_GATES.md` v1.1.
