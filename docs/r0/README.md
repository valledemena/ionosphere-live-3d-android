# docs/r0 — gate de la baseline R0 (GLM)

Actualizado: 2026-11-02, tras `db0274a` y la re-entrega parcial de GLM.
Un artefacto es "verbatim" **solo** si su SHA256 coincide con el de la tabla.
No hay interpretación: el hash no se negocia.

## Gate: 6 de 8

### Publicados y verificados byte a byte en `origin/main`

| # | Artefacto | SHA256 |
|---|---|---|
| 1 | `docs/r0/ARCHITECTURE_DECISION.md` | `487b1bb245dace60f3f7ee8e78e777a99ebb018aafe969364f537bddcbc50d7d` |
| 2 | `docs/r0/OWNERSHIP_AND_RELEASING.md` | `348ad7010e0ba7a5efcfb4395b9f07eebcf25c801f2e5255c75fed39ae665a90` |
| 3 | `docs/r0/TEST_GATES.md` (v1.1) | `b56c1cf26b391b16989b29ca3df376506a1924b608dac0575d3738df3d995d3f` |
| 4 | `docs/r0/relay-primitive.md` | `495a8550af7d3391727ab870e896f1505e3278e712de88c46f64b738499a3021` |
| 5 | `docs/GLM_R0_RESPONSE.md` | `523788fb4759b266558cdb835c6250da3d7037142bf841c8aaf6af0f7175fd0b` |
| 6 | Comentario del Issue #1 (`6577699347`) | `1a42f73992b96adfc985dc76d0172c1c88292ccb79cc2830fb1d8c4ea66e1e38` |

### Pendientes — no recibidos como fichero en este equipo

| # | Artefacto | SHA256 esperado |
|---|---|---|
| 7 | `docs/r0/DATA_CONTRACT.md` | `82e846b2c972540e43f6bcdf19b0451e1f7a0b88490e231245e1c8b79612a439` |
| 8 | `docs/r0/IMPLEMENTATION_SLICES.md` | `967fc9d32e7763fffd9393ba4a6ed3ceb25e45fd2698d1cf7a1d73ba09814297` |

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

## Historial de entrega (por qué el gate llegó a 6/8 y no antes)

La primera entrega del canal GLM incluyó los cinco documentos, pero la
transferencia a esta máquina fue **selectiva**: llegaron `OWNERSHIP`,
`TEST_GATES`, `relay-primitive` y `GLM_R0_RESPONSE`; los otros tres nunca
aparecieron en disco. Eso explica los cuatro commits con digests (`9376cda`,
`5bdf689` y previos): no había bytes que copiar.

La re-entrega de GLM del 2026-11-02 ha vuelto a ser parcial: ha llegado
`ARCHITECTURE_DECISION.md` (hash exacto, 4 613 B, sin BOM) y **no** han llegado
`DATA_CONTRACT.md` ni `IMPLEMENTATION_SLICES.md`. No se publica nada que no se
tenga: las dos rutas siguen vacías.

Regla adoptada en la cadena: **una entrega no cuenta hasta que el receptor
confirma los hashes**. Mientras falte un hash, R0 sigue abierto.

## Cómo se cierra (8/8)

1. Llegan los dos ficheros que faltan (ficheros, no texto embebido en otro
   markdown: extraer texto obliga a re-codificar y rompe el hash).
2. Copia con `WriteAllBytes` → hash **antes** de commitear.
3. Push → verificación post-push con el método de arriba.
4. README a 8/8 → acuse de recibo en el Issue #1 → **R0 cerrado y el Issue #2
   (R1) abierto en el mismo movimiento**.

`ARCHITECTURE_DECISION.md` es el documento que vincula R1 en adelante: ya está.
Sin `DATA_CONTRACT.md` no hay especificación de endpoints/TTLs para R2, y sin
`IMPLEMENTATION_SLICES.md` no hay mapa de fases publicado (aunque sus nombres
están citados por GLM en el Issue #1 y en `docs/TASKS.md`).
