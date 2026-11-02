# docs/r0 — estado real de la baseline R0 (GLM)

Actualizado: 2026-11-02, tras la verificación GLM del commit `5bdf689`.
Gate objetivo: tabla SHA256 del acta `OK-3-R0-verificacion-e88da17.md` §2.
Un fichero es "verbatim" solo si su SHA256 coincide: no hay interpretación posible.

## Publicado y verificado byte a byte — 5 de 8

| Artefacto | SHA256 (gate) | Estado |
|---|---|---|
| `docs/r0/OWNERSHIP_AND_RELEASING.md` | `348ad7010e0b…` | GLM canónico, copia de bytes |
| `docs/r0/TEST_GATES.md` (v1.1) | `b56c1cf26b39…` | GLM canónico, copia de bytes |
| `docs/r0/relay-primitive.md` | `495a8550af7d…` | GLM canónico, copia de bytes |
| `docs/GLM_R0_RESPONSE.md` | `523788fb4759…` | GLM canónico, reemplazo completo |
| Comentario del Issue #1 | `1a42f73992b9…` | publicado (id `6577699347`) |

Los tres primeros ya estaban en `e88da17`; el cuarto se publica en este commit.
Ninguno de ellos pasa por ningún script que genere o re-codifique contenido: se
copian bytes (`WriteAllBytes`) desde el paquete INTERCAMBIO y se verifica el hash
antes de darlos por hechos.

## No recibido en este equipo — NO publicado — 3 de 8

| Artefacto | SHA256 esperado | Estado |
|---|---|---|
| `ARCHITECTURE_DECISION.md` | `487b1bb245da…` | **no entregado** |
| `DATA_CONTRACT.md` | `82e846b2c972…` | **no entregado** |
| `IMPLEMENTATION_SLICES.md` | `967fc9d32e77…` | **no entregado** |

Los tres stubs que ocupaban estas rutas hasta `5bdf689` eran digests de Beam con
la etiqueta "GLM canonical"; se han retirado en este commit. Un resumen no se
convierte en verbatim porque se le ponga una etiqueta.

**Constatación de hecho**: no existe en este equipo ningún fichero con esos tres
SHA256. Se han buscado en `Desktop`, `Downloads`, `Documents` y el directorio
temporal de trabajo, por hash y por nombre, y no aparecen. El acta de baseline
(`OK-3-R0-baseline-glm.md` §0) los sitúa en un directorio `OK-3-R0-docs/` que
nunca llegó a este equipo; el paquete INTERCAMBIO recibido contiene únicamente
`OWNERSHIP_AND_RELEASING.md`, `TEST_GATES.md`, `relay-primitive.md` y
`GLM_R0_RESPONSE-reemplazo.md`. No es una excusa: es la razón por la que las tres
rutas siguen vacías.

## Cómo se cierra el gate (8/8)

1. GLM entrega los tres ficheros como **ficheros**, no como texto embebido en otro
   markdown (extraer texto de un md obliga a re-codificar y rompe el hash).
2. Se copian con `WriteAllBytes` a `docs/r0/` y se calcula el hash **antes** de
   commitear; si no coincide con la tabla, no se commitea.
3. Tras el push, `git show origin/main:docs/r0/X.md | sha256sum` (en Windows:
   `cmd /c "git show origin/main:docs/r0/X.md > tmp" && sha256sum tmp`) debe dar
   el mismo valor. Verificación post-push en este commit: los 4 artefactos
   publicados coinciden.
4. Con 8/8, R0 cierra y R1 abre como Issue #2.

## Nota sobre `ARCHITECTURE_DECISION.md`

Es el documento que vincula R1 en adelante: sin él, R1 no tiene baseline contra la
que revisarse. Por eso R1 no arranca todavía.
