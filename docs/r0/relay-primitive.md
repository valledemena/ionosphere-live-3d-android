# relay-primitive — contrato de publicación del canal GLM→David

**Estado**: complemento de la baseline R0. **Autoría**: GLM, canal GLM→David, textual, 2026-11-02. Publicado por David en nombre de GLM.
**Ubicación destino**: `docs/relay-primitive.md`.

## 1. El contrato (invariante, independiente de la herramienta)

1. **GLM redacta** (documentos, comentarios, revisiones) y entrega el texto a David.
2. **David publica** el texto **verbatim** — con Beam, con script (`relay-publish.ps1`), con deploy key o a mano — con **línea de procedencia** (canal GLM→David, fecha).
3. **GLM verifica** lo publicado por **lectura pública** (API / raw / clone): texto íntegro, procedencia correcta, sin cambios no anunciados. Este bucle ya está operativo.
4. **GLM no autentica nunca**: no posee ni usa cuentas, claves ni credenciales (política dura de su entorno, y condición de independencia: el verificador no es committer).

## 2. La deploy key `glm-mobile-relay`

- Es la **herramienta de ejecución de David** (push no interactivo del relay), **no un acceso de GLM**. Operada solo en la máquina de David; el secreto nunca viaja por chat, repo ni artefactos (Beam ya lo cumple: solo la mitad pública salió al chat).
- **Autenticación ≠ autoría**: los commits hechos con la key llevan el autor git que David configure. La autoría real de cada texto la fija ÚNICAMENTE la línea de procedencia. Un commit hecho con la key no "es de GLM" por el nombre de la key.
- Higiene (recomendaciones de GLM, ver acta OK-3-R0-relay-key-verificacion.md):
  - **Pin del host key de github.com** en `known_hosts` (huellas oficiales publicadas en docs.github.com; Ed25519: `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`). Sustituye a `StrictHostKeyChecking=no` + `UserKnownHostsFile=NUL`, que desactiva toda protección anti-MITM.
  - Clave privada **fuera de `Temp`** → `%USERPROFILE%\.ssh\` con ACL restrictivas (los directorios temporales son barridos/escaneados por otras herramientas).
  - `main` **sin force-push** (branch protection). Direct-push a main permitido SOLO para textos del relay publicados por David; todo código, siempre por PR.
  - Rotar la key si alguna vez aparece en logs, chat o ficheros (hoy no es el caso).

## 3. Opciones de implementación (elección de David)

- **A** — script + deploy key: publica sin copiar/pegar. Válida.
- **B** — relay manual con `to-glm/` + `from-glm/`: válida; útil como fallback.
- Ambas cumplen el contrato §1. **GLM no elige la herramienta de David**; exige los invariantes §1 y la higiene §2 en cualquiera de las dos.

## 4. Cola de publicación (a fecha de este doc)

Pendiente de publicar (paquete R0, bloquea R1): `docs/r0/` (los cinco documentos GLM), reemplazo de `docs/GLM_R0_RESPONSE.md`, comentario en Issue #1, corrección real de WORKFLOW.md (rol GLM = relay, no "writes code/PRs"), TASKS.md alineado al mapa R0–R6, creación de `whiteboard/`. Detalle y evidencia: `docs/r0/OWNERSHIP_AND_RELEASING.md` y las actas OK-3 en poder de David.

## 5. Prohibiciones

- Ningún artefacto con autoría "GLM" sin línea de procedencia del canal GLM→David.
- Ningún secreto en repo, chat ni actas.
- Ningún texto de GLM editado al publicar (verbatim; correcciones = nuevo texto de GLM).
