# OWNERSHIP_AND_RELEASING (R0, GLM)

**Estado**: BASELINE GLM R0. **AutorÃ­a**: GLM, canal GLMâ†’David, 2026-11-02.
**Responde a**: Issue #1 (deliverable 5, "OWNERSHIP_AND releasing").

## 1. Roles (espejo de WORKFLOW.md, sin cambios de fondo)

- **David** â€” propietario. Publica y firma, valida en dispositivos reales, approve/reject final, gestiona accesos. Nadie define tÃ©rminos en su nombre.
- **GLM** â€” arquitectura, especificaciones, revisiÃ³n hunk-a-hunk. **GLM no tiene ni puede tener una cuenta GitHub** (modelo ejecutÃ¡ndose en un sandbox; no registra cuentas ni posee credenciales â€” polÃ­tica dura de su entorno). Su canal es **GLMâ†’Davidâ†’repo**, textual y con lÃ­nea de procedencia. La verificaciÃ³n necesita solo lectura, y el repo pÃºblico ya la da; es ademÃ¡s **estructuralmente preferible** que el verificador no tenga permisos de escritura en el repo que verifica (separaciÃ³n de funciones: verificador â‰  committer).
- **Beam** â€” tooling local de PC, scaffolding, PRs, automatizaciÃ³n, investigaciÃ³n de paquetes. No define tÃ©rminos en nombre de GLM ni de David (lecciÃ³n OK-2).

## 2. Protocolo de autorÃ­a

NingÃºn fichero puede declarar autorÃ­a "GLM" sin haber pasado por el canal GLMâ†’David con lÃ­nea de procedencia.

**Incidente registrado (2026-11-02, commit 99c103e)**: `docs/GLM_R0_RESPONSE.md` se commiteÃ³ con firma "Response-from-GLM-2026-11-02" y afirmaciones de verificaciÃ³n ("desktop reference confirmed exactâ€¦ verified") que GLM no habÃ­a realizado en esta cadena â€” la respuesta R0 constaba como **pendiente** en ese momento. Tratamiento:

- El contenido del fichero se **reemplaza** por la respuesta R0 real (estos cinco docs + el comentario en Issue #1), con cabecera de procedencia.
- Lo que aquel fichero anticipaba y esta baseline ahora sostiene (Kotlin/Compose + GLES 3.1, compartir contrato y no lenguaje, llamadas directas, regla SAO), queda **confirmado por GLM a partir de ahora**; lo que difiera, queda corregido (el mapa de slices vÃ¡lido es el de IMPLEMENTATION_SLICES.md).
- El registro del incidente queda en este doc y en la cadena. No es un bloqueo para R0; sÃ­ lo es para repetir el patrÃ³n.

## 3. Cuenta bot

**No se crea.** Si en el futuro David quisiera un bot committer, es decisiÃ³n y gestiÃ³n suyas (credenciales incluidas). Para la verificaciÃ³n no aporta nada: el bot lo operarÃ­a igualmente David, y eliminarÃ­a la separaciÃ³n verificador/committer que protege la cadena.

## 4. Releasing

- Versionado semÃ¡ntico `v0.x`; tags creados por David.
- AAB + APK firmados por David; publicaciÃ³n en **GitHub Releases** enlazando el tag (= oferta de fuente GPL-3: el repo es la fuente).
- `NOTICE` / pantalla "Fuentes": terceros y fuentes de datos (ver ARCHITECTURE_DECISION Â§5).
- Canal `whiteboard/`: Beam lo crea en el PR de R0 y lo mantiene como espejo de resÃºmenes para agentes sin acceso de lectura a GitHub (WORKFLOW.md ya lo define).

## 5. Cadena y continuidad

- Esta cadena (tercer repo) se registra como **OK-3**, sucesora de OK-2 (cerrada). Acta R0: `OK-3-R0-baseline-glm.md` (entregada a David junto a estos docs).
- Las actas locales de OK-2 del sandbox anterior ya no estÃ¡n en el disco actual; la continuidad de OK-2 vive en los repos y en el historial del chat. Este doc es el primer eslabÃ³n verificable de OK-3 dentro del repo.

