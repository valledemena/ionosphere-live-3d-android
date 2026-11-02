# OWNERSHIP_AND_RELEASING (R0, GLM)

**Estado**: BASELINE GLM R0. **Autoría**: GLM, canal GLM→David, 2026-11-02.
**Responde a**: Issue #1 (deliverable 5, "OWNERSHIP_AND releasing").

## 1. Roles (espejo de WORKFLOW.md, sin cambios de fondo)

- **David** — propietario. Publica y firma, valida en dispositivos reales, approve/reject final, gestiona accesos. Nadie define términos en su nombre.
- **GLM** — arquitectura, especificaciones, revisión hunk-a-hunk. **GLM no tiene ni puede tener una cuenta GitHub** (modelo ejecutándose en un sandbox; no registra cuentas ni posee credenciales — política dura de su entorno). Su canal es **GLM→David→repo**, textual y con línea de procedencia. La verificación necesita solo lectura, y el repo público ya la da; es además **estructuralmente preferible** que el verificador no tenga permisos de escritura en el repo que verifica (separación de funciones: verificador ≠ committer).
- **Beam** — tooling local de PC, scaffolding, PRs, automatización, investigación de paquetes. No define términos en nombre de GLM ni de David (lección OK-2).

## 2. Protocolo de autoría

Ningún fichero puede declarar autoría "GLM" sin haber pasado por el canal GLM→David con línea de procedencia.

**Incidente registrado (2026-11-02, commit 99c103e)**: `docs/GLM_R0_RESPONSE.md` se commiteó con firma "Response-from-GLM-2026-11-02" y afirmaciones de verificación ("desktop reference confirmed exact… verified") que GLM no había realizado en esta cadena — la respuesta R0 constaba como **pendiente** en ese momento. Tratamiento:

- El contenido del fichero se **reemplaza** por la respuesta R0 real (estos cinco docs + el comentario en Issue #1), con cabecera de procedencia.
- Lo que aquel fichero anticipaba y esta baseline ahora sostiene (Kotlin/Compose + GLES 3.1, compartir contrato y no lenguaje, llamadas directas, regla SAO), queda **confirmado por GLM a partir de ahora**; lo que difiera, queda corregido (el mapa de slices válido es el de IMPLEMENTATION_SLICES.md).
- El registro del incidente queda en este doc y en la cadena. No es un bloqueo para R0; sí lo es para repetir el patrón.

## 3. Cuenta bot

**No se crea.** Si en el futuro David quisiera un bot committer, es decisión y gestión suyas (credenciales incluidas). Para la verificación no aporta nada: el bot lo operaría igualmente David, y eliminaría la separación verificador/committer que protege la cadena.

## 4. Releasing

- Versionado semántico `v0.x`; tags creados por David.
- AAB + APK firmados por David; publicación en **GitHub Releases** enlazando el tag (= oferta de fuente GPL-3: el repo es la fuente).
- `NOTICE` / pantalla "Fuentes": terceros y fuentes de datos (ver ARCHITECTURE_DECISION §5).
- Canal `whiteboard/`: Beam lo crea en el PR de R0 y lo mantiene como espejo de resúmenes para agentes sin acceso de lectura a GitHub (WORKFLOW.md ya lo define).

## 5. Cadena y continuidad

- Esta cadena (tercer repo) se registra como **OK-3**, sucesora de OK-2 (cerrada). Acta R0: `OK-3-R0-baseline-glm.md` (entregada a David junto a estos docs).
- Las actas locales de OK-2 del sandbox anterior ya no están en el disco actual; la continuidad de OK-2 vive en los repos y en el historial del chat. Este doc es el primer eslabón verificable de OK-3 dentro del repo.
