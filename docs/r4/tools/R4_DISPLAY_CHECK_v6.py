#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
R4_DISPLAY_CHECK.py (GLM, v6 DEEP GATE — R5, 2026-11-12)
Emitida por ACTA-ERRATA-R4-CHECKER-V5-P0-S5-GLM-20261112 sobre la v4 rev.3
CONGELADA (ac36bb81…7fda · acta ACTA-CONGELACION-R4-CHECKER-V4-GLM-20261112).
Delta aditivo mínimo (errata 0334 del custodio, head a483f48): el test ES3
del slice P fija el header que P cambia — whitelist P + P0 lo admiten con
diff acotado. Cuenta P = 48 y umbrales SIN CAMBIO; v4 queda intacta.
==========================================

Contrato de aceptación del programa R4 (ii) «display parity» (fórmula III).
BASE sellada: main @ 00ec7cbc (post R3.3 + kc2g + higiene).
Pin web de referencia: 8cdc334 (constantes verificadas por GLM directamente
en el pin — ver PRECHECK-R4-CADENA-PIN-GLM-20261108.md).
Plan contractual: docs/r4/PLAN-R4-DISPLAY-PARITY.md (PR #30, 4c7bb4f).

HISTORIA DEL INSTRUMENTO
  v1 (8508ca63…)  congelada sobre la ratificación D1–D6 de David — ancla
                 issuecomment-6722016420 (2026-11-08T13:21:19Z).
  v2 (8b9e7264…)  corrección del defecto B4 (falso negativo estructural;
                 una línea, cero cambios de semántica). Visto bueno de David
                 empaquetado en «fusiona #32» (CIERRE-A/B §3.2). Acta:
                 ACTA-CORRECCION-R4-CHECKER-V2-B4-GLM-20261108.md.
  v3             emitida tras el merge del slice B (main = 9550914, PR #32)
                 según CIERRE-A/B §4.4/§5 — paquete ratificado por la palabra
                 «fusiona #32» de David. **Aditiva pura**: bloque nuevo W
                 (wiring real + deudas vencidas) para el slice C y
                 cumulativo; los bloques SELLOS/A/B/C/CAM/E son byte-idénticos
                 en semántica a v2 (precedente kc2g v1→v3). El FIRME de A
                 (16/16) y el de B (21/21) NO cambian. Acta:
                 ACTA-CONGELACION-R4-CHECKER-V3-GLM-20261109.md.
  v4             rev.3 conforme ANCLA-V4 §2 (contrato de diseño y
                 congelación del instrumento v4 · slice P «precisión», GLM
                 2026-11-11; David autorizó v4 con el delta propuesto:
                 selector P, S7 por blob con re-sellado declarado, W10
                 acotado, regresión obligatoria). DIEZ sustituciones
                 declaradas v3→v4: (1) cabecera v4 + marcador «NO CONGELADO
                 hasta acta GLM»; (2) CLI <ref> <A|B|C|D|E|P>; (3) ORDER =
                 [A,B,C,D,P,E] — el bloque E se activa por nombre
                 (ORDER.index(sl) >= ORDER.index("E")), P no lo dispara;
                 (4) WHITELIST["P"] = render/AtmoShaderSource.kt +
                 docs/r4/R4_RISK_AND_DECISIONS.md (F2: UNA doc — reparto
                 P = cambio + falsación; E = informe + medición);
                 (5) GOLDEN4_SEAL, dict ruta→blob con los cuatro blobs
                 sellados (verificados de forma independiente por dos
                 lados); (6) F5(a): INTOCABLE_ABS SIN los golden — sólo
                 no-golden (DataPanelHost, FramePacer, manifest, builds,
                 workflows); la integridad del golden queda EXCLUSIVAMENTE
                 en S7-por-blob. F5(b) — décima sustitución FORZOSA,
                 declarada aquí: la línea v3 «INTOCABLE_ABS = GOLDEN4 |
                 {…}» deja de ser válida (con GOLDEN4_SEAL como dict
                 lanzaría TypeError: dict | set) y se reescribe sin unión
                 y sin golden; (7) S7 por blob + re-sellado declarado
                 (work/reseal-<head7>.json con base_blob = blob sellado
                 original; el re-sello NO se escribe en el instrumento —
                 cada head lo declara, cada paquete se sostiene solo: F1);
                 (8) W10 acotado al pase de atmósfera, SIN ruta mediump
                 (F3 — pr_ok eliminado; precision-<sha7>.json sin efecto),
                 redacción «el pase» (F5(d)); (9) bloque [P]: P0 + P1;
                 (10) cuenta: selector P = 48 checks (10S+6A+5B+8C+11W+
                 6K+2P); E se mantiene en 50 exactos (F4: 48/48, sin
                 relleno). Procedencia del rev.3: montado desde la v3
                 CONGELADA (b56ee573…) + delta canónico §2; los drafts
                 rev.1 (8e63e814…) y rev.2 (038303df…) del lado OpenCode
                 quedan como historia declarada — este fichero deriva del
                 contrato §2 sobre la base congelada, no byte a byte de
                 ellos. Acta:
                 ACTA-CONGELACION-R4-CHECKER-V4-GLM-20261112 (§5.5,
                 condición (a) satisfecha por depósito verificado glmrelay).
  v5 (esta)      ERRATA P0/S5 (2026-11-12, head a483f48): el CI del head P
                 inicial (b2e1c02) falló SOLO en AtmoShaderSourceTest >
                 frag_es3_header porque el test afirmaba literalmente el
                 header anterior («precision mediump float;») que el slice
                 P cambia a highp. La actualización de esa única expectativa
                 quedó fuera de WHITELIST["P"] (S5) y rompió P0 («exactamente
                 un fichero»). Delta mínimo: (1) WHITELIST["P"] += T +
                 "render/AtmoShaderSourceTest.kt" (sólo ese test, NO todo
                 T+"render/"); (2) P0 admite exactamente DOS ficheros de
                 app/src con diff por fichero acotado a 1 línea (shader:
                 mediump->highp; test: expectativa contains precision
                 mediump/highp float). Cuenta P = 48 y umbrales SIN CAMBIO.
                 Acta: ACTA-ERRATA-R4-CHECKER-V5-P0-S5-GLM-20261112.
  v6 (esta)      R5 — GATE DEEP (2026-11-12, post-R4 e23b47e): W11 pasa a
                 gatear también DEEP. Delta mínimo: (1) W11 exige
                 delta_B_deep_by_sector completo en Android y web y
                 max|Δ_DEEP| <= PARITY_TOL; (2) PARITY_TOL = 2.0 queda fijada
                 en el instrumento y no se lee del comp; si el comp declara
                 parity_tol != 2.0, W11 falla cerrado; (3) texto del check
                 W11 actualizado. E2 sigue gateando solo THIN; P0, el bloque
                 P y el medidor w11-metric.py no se tocan. Base: v5 original
                 ba433297… (no la reconstrucción 2f14ae8b…).

USO
    python3 R4_DISPLAY_CHECK.py <ref> <SLICE>
    SLICE ∈ A|B|C|D|E|P (A=plumbing FBO, B=ACES+sRGB, C=wiring real+bloom/
                          god-rays, D=cámara, E=gate/tests, P=precisión
                          highp en el pase de atmósfera)

    Audita la rama <ref> del slice indicado. Los checks son CUMULATIVOS:
    auditar C exige que las constantes de A y B estén presentes en <ref>
    (los slices previos ya fusionados en main + el slice en curso).

DECISIONES D1–D6 RATIFICADAS POR DAVID — CONGELADAS (en piedra).
Ancla: issuecomment-6722016420 (tracker #15, 2026-11-08T13:21:19Z).
Enmienda posterior => GLM re-emite versionado (v4+), como v1->v3 kc2g.
    D1 = ii        RATIFICADA (relay 6721265942)
    D2 = keep30    RATIFICADA: TARGET_FPS=30 intacto (FramePacer es el techo
                   contra el que están definidos los gates >=29 del expediente;
                   los ~75Hz web son artefacto de rAF del navegador, no target)
    D3 = exact006  RATIFICADA: damping 0.06 port exacto (verificable estático)
    D4 = defer     RATIFICADA: export de video a R5 (el plan ya lo difiere)
    D5 = nasa      RATIFICADA: texturas NASA pin (paridad real de display) con
                   licencia+sha256 declaradas; el re-staging de B (placeholders
                   declarados) vence como requisito DURO en C (W8)
    D6 = match     RATIFICADA: cámara display-matching — fov 42 y distancia
                   inicial 3.3 R_E como el pin (slice D)

D7 (v3, definida en ANCLA-C, palabra de David retenida): APK con texturas
    NASA byte-exactas del pin (paridad estricta, ~31 MB) por defecto;
    enmienda de David (re-muestreo/compresión con hashes re-declarados)
    => v4 del instrumento.

BLOQUE W (nuevo en v3) — wiring real + deudas vencidas del slice C
(CIERRE-A/B §5, ratificado con «fusiona #32»):
    W1  flip real: ToneMapPass referenciado por código host (pass NO inerte)
    W2  OffscreenSurface usado por el host (secuencia offscreen->tonemap->
        default framebuffer)
    W3  fuga de resize arreglada: onSurfaceChanged llama release() del RT
        anterior (hoy cada resize filtra 1 FBO + 2 renderbuffers MSAA)
    W4  guard FUNCIONAL de extensión: glGetString(GL_EXTENSIONS) consultado
        en runtime (no constante declarativa muerta)
    W5  sin constantes decorativas: cada const val de gl/+render/ (golden4
        excluido) usada >= 1 vez en main (hoy: RT_OFFSCREEN_NOTE,
        EXT_COLOR_BUFFER_HALF_FLOAT, ACES_1_12 — cero usos)
    W6  tests reales del RT: width()/height()/isReady ejercitados y smoke
        vacío assertTrue(true) eliminado
    W7  test conductual del ToneMapPass: función pura de curva en main +
        assertEquals numérico en test; el espejo FRAGMENT/contains queda
        sustituido (sin referencias a FRAGMENT en app/src/test)
    W8  D5 sustancia: earth-day.jpg y earth-night.png byte-exactas del pin
        (tamaño+sha256), CREDITS.txt con licencia NASA public domain (sin
        placeholder/MIT-0), placeholders .bin un-staged
    W9  R1: uExposure ausente del fuente o fijado por glUniform1f(1.12f)
        desde el host (mecánica del pin)
    W10 R2: el pase de atmósfera (AtmoShaderSource.kt) declara precision
        highp (pin) — SIN ruta mediump (F3); el grep global de v3 daba un
        falso PASS con las highp de B/C mientras el atmo seguía en mediump
        (errata declarada, ANCLA-V4 §9)
    W11 captura comparativa debug vs Web (re-escalada de §B): métrica radial
        del expediente sobre captura Android en encuadre libre vs pin web;
        THIN min >= 3 en ambas y paridad max|Δ_THIN| <= 2.0; DEEP paridad
        max|Δ_DEEP| <= 2.0 (v6, R5). PARITY_TOL fijada en el instrumento.

BLOQUE P (nuevo en v4) — slice P «precisión» (ANCLA-V4 §2.9/§6, fórmula III):
    P0  el diff de CÓDIGO (app/src) del slice son exactamente DOS ficheros
        (errata v5): render/AtmoShaderSource.kt con exactamente UNA línea
        cambiada (mediump → highp) y su test render/AtmoShaderSourceTest.kt
        con exactamente UNA línea (expectativa del header ES3); la doc de
        falsación (docs/r4/) viaja en el mismo single-commit por F2/§6.3
        y la gobierna S5 (whitelist P)
    P1  halo propio del head P: evidencia work/comp-<P7>.json con
        reference_sha256 OBLIGATORIO (sha256 del par web sin bloom:
        web-nobloom-ON + web-OFF); THIN gateada (min >= 3 Y max|diff| <=
        2.0), DEEP reportada (no gateada)

EVIDENCIA (junto al script, en work/)
    work/ci-<head7>.json         CI 3/3 del head auditado (SIEMPRE del run
                                 del SHA exacto — regla del expediente)
    work/fps-<head7>.json        gate FPS (slice E): 6 ventanas >=29, preset
                                 16/6, metodo dumpsys SurfaceFlinger --latency
    work/halo-<head7>.json       gate halo (slice E): captura 610x620 DPR1,
                                 720 angulos, sectores 15deg, THIN 258-278,
                                 DEEP 262-287, limbo iluminado 75-225,
                                 delta_B = ON-OFF >= 3
    work/reseal-<head7>.json     (S7, v4) re-sellado declarado del head:
                                 {"files": {ruta: {"blob": <blob nuevo>,
                                  "base_blob": <blob sellado ORIGINAL>,
                                  "ancla": <ref del ancla/PR del cambio>,
                                  "cambio": <descripción>}}} — base_blob
                                 DEBE ser el blob sellado original; el
                                 re-sello NO se escribe en el instrumento
                                 (cada head lo declara en su paquete — F1)
    (RETIRADO v4) work/precision-<head7>.json — vía mediump de W10
                                 eliminada (F3): ese fichero deja de tener
                                 efecto y el instrumento no lo lee
    work/comp-<head7>.json       (W11) captura comparativa:
                                 {"head_sha":..., "method":...,
                                  "captures": {"android": {...}, "web": {...}},
                                  "parity_tol": 2.0} con cada captura =
                                 {"framing":"libre", "n_angles":720,
                                  "sector_deg":15, "illuminated_limb":[75,225],
                                  "bands":{"THIN":[258,278],"DEEP":[262,287]},
                                  "delta_B_by_sector":[...]} (sectores del
                                  limbo iluminado, en orden).
                                 (v6, R5) W11 exige además
                                 delta_B_deep_by_sector en AMBAS capturas y
                                 max|Δ_DEEP| <= PARITY_TOL (2.0). Si falta
                                 DEEP o el comp declara parity_tol != 2.0,
                                 W11 falla cerrado. (P1, v4) el comp del head
                                 P AÑADE "reference_sha256" OBLIGATORIO
                                 (sha256 del par web sin bloom); DEEP sigue
                                 siendo reporte en P1, no gate.
"""
import hashlib
import json
import os
import subprocess
import sys

REPO = os.environ.get("KC2G_REPO", "/home/z/my-project/android-pr16")
HERE = os.path.dirname(os.path.abspath(__file__))

BASE = "00ec7cbc830761ab838638773326c36a39435093"
PARITY_TOL = 2.0  # v6/R5: fijada en el instrumento; no se lee del comp

# Decisiones (ver docstring)
D2, D3, D4, D5, D6 = "keep30", "exact006", "defer", "nasa", "match"

P = "app/src/main/java/com/valledemena/ionosphere/"
T = "app/src/test/java/com/valledemena/ionosphere/"

# Golden 4 (sellados R3.2b) — v4: blobs sellados por ruta (ANCLA-V4 §2.5,
# verificados de forma independiente por dos lados esta sesión). La
# integridad del golden es competencia EXCLUSIVA de S7-por-blob (F5a):
# blob→sha es más fuerte que «no tocado» por ruta.
GOLDEN4_SEAL = {
    P + "render/AtmoShaderSource.kt":
        "5e58a67155d2c86b0ab85f972d36fc90e2c24db1",
    P + "render/AtmoShaderConstants.kt":
        "62f5f6c92dd0df8d2eef990c73e4bb3e1dfc0799",
    P + "render/Colormaps.kt":
        "6c9fe2bc920418e89e02bd6fe0ed89f5cda29db8",
    P + "render/EarthShaderConstants.kt":
        "8e8c9d16ff09f79fe62342fba48cde774f6aedda",
}
# F5(b) — sustitución forzosa DECLARADA (la décima): la línea v3
# «INTOCABLE_ABS = GOLDEN4 | {…}» lanza TypeError con GOLDEN4_SEAL como
# dict (dict | set no soportado); reescrita SIN unión y SIN golden (F5a).
INTOCABLE_ABS = {
    P + "ui/DataPanelHost.kt",
    P + "core/FramePacer.kt",            # D2: TARGET_FPS=30 intacto
    "app/src/main/AndroidManifest.xml",
    "app/build.gradle.kts",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle/libs.versions.toml",
    ".github/workflows",
}

# D5 sustancia (W8): texturas NASA del pin, byte-exactas (CIERRE-A/B §3.3)
NASA = [
    ("app/src/main/assets/earth-day.jpg", 1884678,
     "99f5faad74efe985fbf1714c8be7296ca9999759a1215b65f99b7f1df278dde5"),
    ("app/src/main/assets/earth-night.png", 27964110,
     "bfc05c5de429e52de8f6ab92fe0022df2041f648a6983cb4fede68ccf05ce795"),
]

# Whitelist por slice (entradas exactas o prefijos terminados en '/')
WHITELIST = {
    "A": [P + "gl/", T + "gl/", "app/src/main/assets/", "docs/r4/"],
    "B": [P + "gl/", T + "gl/", P + "render/", T + "render/",
          "app/src/main/assets/", "docs/r4/"],
    "C": [P + "gl/", T + "gl/", P + "render/", T + "render/",
          "app/src/main/assets/", "docs/r4/"],
    "D": [P + "gl/", T + "gl/", P + "core/", T + "core/",
          P + "ui/GlobeScreen.kt", T + "ui/", "docs/r4/"],
    "P": [P + "render/AtmoShaderSource.kt", T + "render/AtmoShaderSourceTest.kt",
          "docs/r4/R4_RISK_AND_DECISIONS.md"],
    "E": ["docs/r4/", "scripts/", T + "render/", T + "gl/"],
}
# v4 (§2.3): P antes de E — el bloque E se activa por NOMBRE
# (ORDER.index(sl) >= ORDER.index("E")), no por índice numérico,
# de modo que P no lo dispara.
ORDER = ["A", "B", "C", "D", "P", "E"]

results = []


def git(*args):
    r = subprocess.run(["git", "-C", REPO] + list(args), capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "replace")[:300])
    return r.stdout.decode("utf-8", "replace")


def rc_zero(*args):
    return subprocess.run(["git", "-C", REPO] + list(args),
                          capture_output=True).returncode == 0


def grep_ref(pattern, ref, pathspec, flags=""):
    """git grep -E en un ref; devuelve líneas match (lista)."""
    r = subprocess.run(["git", "-C", REPO, "grep", "-I", "-n", "-E"]
                       + (["-i"] if "i" in flags else [])
                       + [pattern, ref, "--", pathspec], capture_output=True)
    if r.returncode not in (0, 1):
        return []
    return r.stdout.decode("utf-8", "replace").splitlines()


def check(grupo, cid, desc, ok, detalle=""):
    results.append((grupo, cid, desc, bool(ok), detalle))


def wl_match(path, wl):
    return any(path == w or (w.endswith("/") and path.startswith(w)) for w in wl)


def ci_evidence(head, fname="ci-{}.json"):
    path = os.path.join(HERE, "work", fname.format(head[:7]))
    if not os.path.exists(path):
        return False, f"sin evidencia {os.path.basename(path)}"
    ci = json.load(open(path))
    runs = ci.get("check_runs", [])
    ok = (ci.get("head_sha", "").startswith(head[:7])
          and len(runs) == 3
          and all(r["conclusion"] == "success" and r["status"] == "completed" for r in runs))
    det = f"run {ci.get('run_id')}: " + ", ".join(f"{r['name']}={r['conclusion']}" for r in runs)
    return ok, det


def decl_files(symbol, ref):
    """Ficheros de app/src/main donde se DECLARA una clase `symbol`."""
    return {l.split(":", 2)[1] for l in grep_ref("class " + symbol + "[^A-Za-z0-9_]", ref, "app/src/main")}


def main():
    if len(sys.argv) < 3 or sys.argv[2].upper() not in ORDER:
        print("uso: R4_DISPLAY_CHECK.py <ref> <A|B|C|D|E|P>")
        return 2
    ref = sys.argv[1]
    sl = sys.argv[2].upper()

    head = git("rev-parse", ref).strip()
    main_sha = git("rev-parse", "origin/main").strip()

    # ----------------------------------------------------------------- sellos
    check("SELLOS", "S1", f"HEAD {ref} resuelve a {head[:10]}", len(head) == 40, head)
    check("SELLOS", "S2", "BASE 00ec7cb es ancestro de origin/main (histórico sellado sin reescritura)",
          rc_zero("merge-base", "--is-ancestor", BASE, "origin/main"), main_sha[:10])
    n = git("rev-list", "--count", f"origin/main..{ref}").strip()
    check("SELLOS", "S3", "single-commit sobre origin/main (fórmula III, un slice = un PR)",
          n == "1", f"rev-list count = {n}")
    parents = git("show", "-s", "--format=%P", head).strip().split()
    check("SELLOS", "S4", f"parent del head = origin/main ({main_sha[:10]})",
          parents == [main_sha], " ".join(p[:7] for p in parents))
    d_slice = [l.strip() for l in git("diff", "--name-only", f"origin/main..{ref}").splitlines() if l.strip()]
    fuera = [f for f in d_slice if not wl_match(f, WHITELIST[sl])]
    tocados_intoc = [f for f in d_slice if f in INTOCABLE_ABS or f.startswith(".github/workflows")]
    check("SELLOS", "S5", f"diff del slice {sl} dentro de su whitelist y sin intocables",
          not fuera and not tocados_intoc, f"fuera: {fuera}; intocables: {tocados_intoc}")
    wl_acum = [w for s in ORDER[:ORDER.index(sl) + 1] for w in WHITELIST[s]]
    d_acum = [l.strip() for l in git("diff", "--name-only", f"{BASE}..{ref}").splitlines() if l.strip()]
    fuera_a = [f for f in d_acum if not wl_match(f, wl_acum)]
    tocados_a = [f for f in d_acum if f in INTOCABLE_ABS]
    check("SELLOS", "S6", f"diff acumulado BASE..ref dentro de la unión de whitelists A..{sl}",
          not fuera_a and not tocados_a, f"fuera: {fuera_a}; intocables: {tocados_a}")
    # S7 v4 (§2.7): por BLOB + re-sellado declarado. Cada golden del ref
    # debe ser el blob sellado, o estar declarado en work/reseal-<head7>.json
    # con {files: {ruta: {blob, base_blob, ancla, cambio}}} y base_blob =
    # blob sellado original (un re-sello sólo vale si parte del golden
    # original). El re-sellado NO se escribe en el instrumento: se congela
    # una vez y cada head lo declara (cada paquete se sostiene solo — F1).
    reseal = {}
    p_rs = os.path.join(HERE, "work", f"reseal-{head[:7]}.json")
    if os.path.exists(p_rs):
        reseal = json.load(open(p_rs)).get("files", {})
    s7_bad, s7_ev = [], []
    for gp, gblob in GOLDEN4_SEAL.items():
        gn = gp.rsplit("/", 1)[-1]
        if not rc_zero("cat-file", "-e", f"{ref}:{gp}"):
            s7_bad.append(f"{gn}: AUSENTE en {ref[:10]}")
            s7_ev.append(f"{gn}: SIN evidencia")
            continue
        blob = git("rev-parse", f"{ref}:{gp}").strip()
        if blob == gblob:
            s7_ev.append(f"{gn}: blob sellado {gblob[:7]}")
        else:
            ev = reseal.get(gp)
            if (isinstance(ev, dict) and ev.get("blob") == blob
                    and ev.get("base_blob") == gblob
                    and ev.get("ancla") and ev.get("cambio")):
                s7_ev.append(f"{gn}: re-sello {blob[:7]} (base {gblob[:7]}) — "
                             f"evidencia: reseal-{head[:7]}.json")
            else:
                s7_bad.append(f"{gn}: blob {blob[:7]} != sellado {gblob[:7]}")
                s7_ev.append(f"{gn}: SIN evidencia")
    check("SELLOS", "S7", "golden 4 por blob: sellado o re-sello declarado (base_blob = sellado; reseal-<head7>.json)",
          not s7_bad, "; ".join(s7_ev))
    fp = P + "core/FramePacer.kt"
    rot = P + "core/Rotation.kt"
    rot_txt = git("show", f"{ref}:{rot}") if rc_zero("cat-file", "-e", f"{ref}:{rot}") else ""
    rot_ok = "TARGET_FPS" in rot_txt and "30" in rot_txt
    check("SELLOS", "S8", "FramePacer byte-intacto + Rotation.TARGET_FPS = 30 (D2 keep30)",
          fp not in d_acum and rot_ok,
          "FramePacer modificado" if fp in d_acum else ("" if rot_ok else "TARGET_FPS=30 no hallado en Rotation.kt"))
    d_data = [f for f in d_acum if f.startswith(P + "data/") or f.startswith(T + "data/")]
    check("SELLOS", "S9", "árbol data/ byte-intacto (R4 toca cero ficheros de datos)", not d_data, str(d_data))
    ok_ci, det_ci = ci_evidence(head)
    check("SELLOS", "S10", f"CI 3/3 del head {head[:7]} (evidencia work/ci-{head[:7]}.json del run del SHA exacto)",
          ok_ci, det_ci)

    # ------------------------------------------------- etapa A — plumbing FBO
    if ORDER.index(sl) >= 0:
        gl = P + "gl/"
        a1 = grep_ref("Framebuffer|glGenFramebuffers", ref, gl)
        check("A", "A1", "FBO offscreen presente en gl/ (framebuffers generados/administrados)", bool(a1), f"{len(a1)} refs")
        a2 = grep_ref("RGBA16F", ref, "app/src/main")
        check("A", "A2", "RT de color RGBA16F (HalfFloat análogo GLES)", bool(a2), "")
        a3 = grep_ref("RenderbufferStorageMultisample|glFramebufferTexture2DMultisample|MAX_SAMPLES", ref, gl)
        check("A", "A3", "MSAA 4x en el RT (multisample renderbuffer/texture)", bool(a3), "")
        a4 = grep_ref("EXT_color_buffer_(half_)?float", ref, "app/src/main")
        check("A", "A4", "guard de extensión EXT_color_buffer_(half_)float (RGBA16F renderizable)", bool(a4), "")
        a5 = grep_ref("DEPTH_COMPONENT|DEPTH24|DEPTH_RENDERBUFFER|depth.*[Rr]enderbuffer", ref, gl)
        check("A", "A5", "attachment de depth en el FBO", bool(a5), "")
        a6 = grep_ref("FBO|Offscreen|RenderTarget|realloc|Resize", ref, "app/src/test", "i")
        check("A", "A6", "tests de tamaño/realloc del RT (slice A: 'tests de tamaño' del plan)", bool(a6), "")

    # ------------------------------------------------- etapa B — ACES + sRGB
    if ORDER.index(sl) >= 1:
        b_src = grep_ref("ACES", ref, "app/src/main", "i")
        check("B", "B1", "ACES Filmic presente (tone mapping)", bool(b_src), "")
        b2 = [l for l in grep_ref("1\\.12f?", ref, "app/src/main")
              if "exposure" in l.lower() or "uExposure" in l]
        check("B", "B2", "exposure 1.12 ligado al tone map (uExposure 1.12, pin línea 307)",
              bool(b2), "")
        b3 = grep_ref("srgb|12\\.92|1\\.0 ?/ ?2\\.2|pow\\(.+, ?2\\.4", ref, "app/src/main", "i")
        check("B", "B3", "encode sRGB en fragment (transfer sRGB)", bool(b3), "")
        # anti-confluencia: el 1.12 de exposure vive con ACES; el de minDistance con la cámara
        b4_files = [l.split(":", 2)[:2] for l in grep_ref("ACES", ref, "app/src/main", "i")]
        b4_ok = any("1.12" in "".join(grep_ref("ACES", ref, f, "i")) + "".join(grep_ref("1\\.12", ref, f))
                    for f in {p for _, p in b4_files})
        check("B", "B4", "anti-confluencia: exposure 1.12 en contexto ACES (NO minDistance; son dos 1.12 distintos)",
              b4_ok, "")
        if D5 == "nasa":
            assets = [l for l in git("ls-tree", "-r", "--name-only", ref).splitlines()
                      if l.startswith("app/src/main/assets/")]
            lic = [f for f in assets if "LICENSE" in f.upper() or "README" in f.upper() or "CREDITS" in f.upper()]
            check("B", "B5", "(D5 nasa) assets de tierra presentes con licencia/credits declarados",
                  len(assets) >= 2 and bool(lic), f"assets: {len(assets)}; lic: {lic}")
        else:
            ph = P + "core/PlaceholderGlobeTexture.kt"
            check("B", "B5", "(D5 placeholder) PlaceholderGlobeTexture sin cambios",
                  ph not in d_acum, "")

    # ------------------------------------------- etapa C — bloom + god-rays
    if ORDER.index(sl) >= 2:
        c1a = grep_ref("0\\.55f?", ref, "app/src/main")
        c1b = grep_ref("0\\.72f?", ref, "app/src/main")
        c1c = grep_ref("[Ss]trength", ref, "app/src/main")
        check("C", "C1", "bloom: strength 0.55 / threshold 0.72 (pin UnrealBloomPass 0.55, 0.5, 0.72)",
              bool(c1a and c1b and c1c), "")
        c2 = grep_ref("[Hh]orizontal", ref, "app/src/main") and grep_ref("[Vv]ertical", ref, "app/src/main")
        check("C", "C2", "bloom separable (pasadas horizontal + vertical)", bool(c2), "")
        c3 = grep_ref("NTAPS *= *12|nTaps *= *12|12.*taps|TAPS *= *12", ref, "app/src/main", "i")
        check("C", "C3", "god-rays: NTAPS = 12 (pin godrays.ts:10)", bool(c3), "")
        c4 = grep_ref("DECAY *= *0\\.9|decay *= *0\\.9f?", ref, "app/src/main")
        check("C", "C4", "god-rays: DECAY = 0.9 (pin godrays.ts:11)", bool(c4), "")
        c5 = grep_ref("MAX_RADIUS *= *0\\.45|maxRadius *= *0\\.45f?", ref, "app/src/main", "i")
        check("C", "C5", "god-rays: MAX_RADIUS = 0.45 (fracción de alto; pin godrays.ts:12)", bool(c5), "")
        c6 = grep_ref("GAIN *= *0\\.12|gain *= *0\\.12f?", ref, "app/src/main")
        check("C", "C6", "god-rays: GAIN = 0.12 post-ACES (pin godrays.ts:15)", bool(c6), "")
        c7 = grep_ref("gate|sunBehindLimb|skip", ref, "app/src/main", "i")
        c7b = [l for l in c7 if "sun" in l.lower() or "gate" in l.lower()]
        check("C", "C7", "CPU gate de proyección solar (skip cuando el sol no aporta)", bool(c7b), "")
        c8 = grep_ref("post-ACES|despues de ACES|después de ACES|after ACES", ref, "app/src/main", "i")
        check("C", "C8", "orden documentado: god-rays post-ACES (último pase, pin shaders.ts:497+)", bool(c8), "")

    # --------------------------- etapa W — wiring real + deudas (NUEVA en v3)
    if ORDER.index(sl) >= 2:
        # W1: flip real — el host invoca ToneMapPass (pass NO inerte)
        decl_tp = decl_files("ToneMapPass", ref)
        host_tp = {l.split(":", 2)[1] for l in grep_ref("ToneMapPass", ref, "app/src/main")} - decl_tp
        check("W", "W1", "flip real: ToneMapPass referenciado por código host (fuera de su declaración)",
              bool(host_tp), f"host: {sorted(host_tp)}")

        # W2: el RT offscreen lo usa el host (secuencia offscreen -> tonemap -> default)
        decl_os = decl_files("OffscreenSurface", ref)
        host_os = {l.split(":", 2)[1] for l in grep_ref("OffscreenSurface", ref, "app/src/main")} - decl_os
        check("W", "W2", "OffscreenSurface usado por el host (secuencia offscreen -> tonemap -> default)",
              bool(host_os), f"host: {sorted(host_os)}")

        # W3: fuga de resize — onSurfaceChanged libera el RT anterior
        osc_files = {l.split(":", 2)[1] for l in grep_ref("onSurfaceChanged", ref, "app/src/main")}
        w3_bad = [f for f in osc_files if not grep_ref("release", ref, f)]
        check("W", "W3", "fuga arreglada: onSurfaceChanged llama release() del OffscreenSurface anterior",
              bool(osc_files) and not w3_bad, f"sin release(): {w3_bad}")

        # W4: guard FUNCIONAL de extensión (no constante declarativa)
        w4a = grep_ref("glGetString", ref, "app/src/main")
        w4b = grep_ref("GL_EXTENSIONS", ref, "app/src/main")
        check("W", "W4", "guard funcional: glGetString(GL_EXTENSIONS) consultado en runtime",
              bool(w4a and w4b), f"glGetString: {len(w4a)}; GL_EXTENSIONS: {len(w4b)}")

        # W5: sin constantes decorativas (cero usos) en gl/ + render/ (golden4 excluido)
        decor = []
        kt_files = [x for x in git("ls-tree", "-r", "--name-only", ref, "--",
                                   P + "gl/", P + "render/").splitlines()
                    if x.endswith(".kt") and x not in GOLDEN4_SEAL]
        for f in kt_files:
            for l in grep_ref("const val [A-Z][A-Z0-9_]*", ref, f):
                if "const val " not in l:
                    continue
                name = l.split("const val ")[1].split(" ")[0].split("=")[0].strip()
                uses = [u for u in grep_ref(name, ref, "app/src/main") if u.strip()]
                if len(uses) < 2:
                    decor.append(f"{name} ({f.split('/')[-1]})")
        check("W", "W5", "sin constantes decorativas: cada const de gl/+render/ (excl. golden4) usada >= 1 vez en main",
              not decor, str(decor))

        # W6: tests reales del RT — smoke vacío eliminado
        w6a = grep_ref("width\\(\\)|height\\(\\)|isReady", ref, T + "gl/")
        w6b = grep_ref("assertTrue\\(true\\)", ref, T + "gl/")
        check("W", "W6", "tests reales del RT: width()/height()/isReady ejercitados, sin assertTrue(true)",
              bool(w6a) and not w6b, f"api refs: {len(w6a)}; smoke vacío: {len(w6b)}")

        # W7: test conductual del tonemap (curva pura + valores esperados; espejo sustituido)
        curve_funs = []
        for f in kt_files:
            for l in grep_ref("fun [a-zA-Z0-9_]*(aces|tone|curve|srgb|eval)", ref, f, "i"):
                if "fun " not in l:
                    continue
                fname = l.split("fun ")[1].split("(")[0].strip()
                if fname:
                    curve_funs.append((f, fname))
        w7b_ok = False
        for _f, fname in curve_funs:
            for tl in grep_ref(fname, ref, T):
                tf = tl.split(":", 2)[1]
                if grep_ref("assertEquals\\([^)]*[0-9]\\.[0-9]", ref, tf):
                    w7b_ok = True
        w7c = grep_ref("FRAGMENT", ref, T)
        check("W", "W7", "test conductual del ToneMapPass: fun curva pura + assertEquals numérico; espejo FRAGMENT sustituido",
              bool(curve_funs) and w7b_ok and not w7c,
              f"curvas: {sorted({n for _, n in curve_funs})}; espejo FRAGMENT en test: {len(w7c)}")

        # W8: D5 sustancia — NASA byte-exacta + CREDITS + un-staging .bin
        nasa_ok, nasa_det = [], []
        for rel, sz, h in NASA:
            if rc_zero("cat-file", "-e", f"{ref}:{rel}"):
                s = git("cat-file", "-s", f"{ref}:{rel}").strip()
                blob = subprocess.run(["git", "-C", REPO, "cat-file", "blob", f"{ref}:{rel}"],
                                      capture_output=True).stdout
                dig = hashlib.sha256(blob).hexdigest()
                if str(sz) == s and dig == h:
                    nasa_ok.append(rel)
                else:
                    nasa_det.append(f"{rel}: {s}B/{dig[:12]}… != {sz}B/{h[:12]}…")
            else:
                nasa_det.append(f"{rel}: AUSENTE")
        cred_rel = "app/src/main/assets/CREDITS.txt"
        cred = git("show", f"{ref}:{cred_rel}") if rc_zero("cat-file", "-e", f"{ref}:{cred_rel}") else ""
        cl = cred.lower()
        cred_ok = ("nasa" in cl and "public domain" in cl
                   and "placeholder" not in cl and "mit-0" not in cl)
        bins = [x for x in git("ls-tree", "-r", "--name-only", ref, "--",
                               "app/src/main/assets/").splitlines() if x.endswith(".bin")]
        check("W", "W8", "D5 sustancia: NASA byte-exacta (tamaño+sha256 pin) + CREDITS public-domain + .bin un-staged",
              len(nasa_ok) == 2 and cred_ok and not bins,
              f"ok: {len(nasa_ok)}/2; {nasa_det}; credits_ok={cred_ok}; bins={bins}")

        # W9: R1 — uExposure fijado por host (glUniform1f 1.12) o eliminado del fuente
        uexp = grep_ref("uExposure", ref, "app/src/main")
        u1 = [l for l in grep_ref("glUniform1f", ref, "app/src/main")
              if "1.12" in l or "xposure" in l]
        check("W", "W9", "R1: uExposure ausente del fuente o fijado por glUniform1f(1.12f) desde el host",
              (not uexp) or bool(u1), f"uniform en fuente: {len(uexp)}; host set: {len(u1)}")

        # W10 v4 (§2.8, F3+F5(d)): acotado AL PASE de atmósfera, SIN ruta
        # mediump — precision-<sha7>.json deja de tener efecto (no se lee).
        # El grep global de v3 daba un falso PASS con las highp de B/C
        # mientras el pase de atmósfera seguía en mediump (errata §9 ANCLA-V4).
        hp = grep_ref("precision highp", ref, P + "render/AtmoShaderSource.kt")
        check("W", "W10", "R2: el pase de atmósfera (AtmoShaderSource.kt) declara precision highp (pin)",
              bool(hp), f"{len(hp)} decl highp en el pase de atmósfera")

        # W11: captura comparativa debug vs Web (métrica radial, encuadre libre)
        p_cp = os.path.join(HERE, "work", f"comp-{head[:7]}.json")
        cp_ok, cp_det = False, "sin evidencia"
        if os.path.exists(p_cp):
            j = json.load(open(p_cp))
            caps = j.get("captures", {})

            def cap_ok(c):
                return (c.get("n_angles") == 720 and c.get("sector_deg") == 15
                        and c.get("illuminated_limb") == [75, 225]
                        and c.get("bands", {}).get("THIN") == [258, 278]
                        and c.get("bands", {}).get("DEEP") == [262, 287]
                        and isinstance(c.get("delta_B_by_sector"), list)
                        and bool(c.get("delta_B_by_sector")))
            if (j.get("head_sha", "").startswith(head[:7])
                    and set(caps) >= {"android", "web"}
                    and all(cap_ok(caps[k]) for k in ("android", "web"))):
                a = caps["android"]["delta_B_by_sector"]
                w = caps["web"]["delta_B_by_sector"]
                if len(a) == len(w) and a and w:
                    diffs = [abs(x - y) for x, y in zip(a, w)]
                    declared_tol = j.get("parity_tol", PARITY_TOL)
                    da = caps["android"].get("delta_B_deep_by_sector") or []
                    dw = caps["web"].get("delta_B_deep_by_sector") or []
                    deep_ok = False
                    deep_det = "DEEP ausente"
                    if len(da) == len(dw) == 10:
                        dd = [abs(x - y) for x, y in zip(da, dw)]
                        deep_ok = max(dd) <= PARITY_TOL
                        deep_det = (f"DEEP parity {max(dd):.2f} <= {PARITY_TOL}; "
                                    f"min and/web {min(da):.2f}/{min(dw):.2f}")
                    if declared_tol == PARITY_TOL:
                        cp_ok = (min(a) >= 3 and min(w) >= 3
                                 and max(diffs) <= PARITY_TOL and deep_ok)
                        cp_det = (f"THIN min and={min(a):.2f}/web={min(w):.2f}; "
                                  f"max|diff|={max(diffs):.2f} <= {PARITY_TOL}; "
                                  f"{deep_det}")
                    else:
                        cp_ok = False
                        cp_det = (f"parity_tol declarado {declared_tol} != "
                                  f"{PARITY_TOL}; fail-closed")
                else:
                    cp_det = "sectores android/web de distinta longitud"
            else:
                cp_det = f"capturas/params incompletos: {sorted(caps)}"
        check("W", "W11", "captura comparativa debug vs Web: radial 720/15, limbo 75-225, THIN min>=3 ambos, max|dTHIN|<=2.0, max|dDEEP|<=2.0",
              cp_ok, cp_det)

    # ------------------------------------------------- etapa D — cámara (D6)
    if ORDER.index(sl) >= 3:
        if D6 == "match":
            k1 = grep_ref("perspectiveM\\([^)]*42f?", ref, P + "gl/")
            check("CAM", "K1", "(D6 match) fov 42 como el pin (pin línea 327; Android actual 45)",
                  bool(k1), "")
            k2 = grep_ref("minDistance.*1\\.12|1\\.12f?.*minDistance", ref, "app/src/main", "i")
            check("CAM", "K2", "minDistance 1.12 (pin línea 335 — NO confluir con exposure)", bool(k2), "")
            k3 = grep_ref("maxDistance.*30f?|30f?.*maxDistance", ref, "app/src/main", "i")
            check("CAM", "K3", "maxDistance 30 (pin línea 337)", bool(k3), "")
        if D3 == "exact006":
            k4 = grep_ref("damping.*0\\.06|0\\.06f?.*[Dd]amping", ref, "app/src/main")
            check("CAM", "K4", "(D3 exact) damping 0.06 port exacto (pin línea 334)", bool(k4), "")
        k5 = grep_ref("pan", ref, "app/src/main", "i")
        k5b = [l for l in k5 if "false" in l.lower() or "disable" in l.lower() or "enabled = false" in l.lower()]
        check("CAM", "K5", "pan deshabilitado (plan: 'pan disabled')", bool(k5b), "")
        k6 = grep_ref("autoRotate.*0\\.35|0\\.35f?.*autoRotate|AUTO_ROTATE.*0\\.35", ref, "app/src/main", "i")
        check("CAM", "K6", "autoRotate 0.35 (cinema; opcional — presente con el valor del pin o ausente)",
              True, "informativo" if not k6 else "presente")

    # ------------------------------------- etapa P — precisión (v4, §2.9)
    if sl == "P":
        # P0 (errata v5): el diff de CÓDIGO (app/src) del slice son
        # exactamente DOS ficheros — render/AtmoShaderSource.kt (1 línea,
        # mediump -> highp) y su test render/AtmoShaderSourceTest.kt
        # (1 línea, expectativa del header ES3). La doc de falsación
        # (docs/r4/) viaja en el mismo single-commit por F2/§6.3 y la
        # gobierna S5 (whitelist P).
        atmo = P + "render/AtmoShaderSource.kt"
        atmo_t = T + "render/AtmoShaderSourceTest.kt"
        e_code = [f for f in d_slice if f.startswith("app/src")]
        p0_ok = e_code == [atmo, atmo_t]
        p0_det = f"código: {e_code}; docs: {[f for f in d_slice if not f.startswith('app/src')]}"
        if p0_ok:
            def _u0(fpath):
                dl = git("diff", "-U0", f"origin/main..{ref}", "--", fpath).splitlines()
                mn = [l[1:] for l in dl if l.startswith("-") and not l.startswith("---")]
                pl = [l[1:] for l in dl if l.startswith("+") and not l.startswith("+++")]
                return mn, pl
            m_s, p_s = _u0(atmo)
            m_t, p_t = _u0(atmo_t)
            p0_ok = (len(m_s) == 1 and len(p_s) == 1
                     and "mediump" in m_s[0] and "highp" in p_s[0]
                     and len(m_t) == 1 and len(p_t) == 1
                     and 'contains("precision mediump float;")' in m_t[0]
                     and 'contains("precision highp float;")' in p_t[0])
            p0_det = (f"shader -{len(m_s)}/+{len(p_s)}: "
                      f"'{(m_s[0].strip() if m_s else '')}' -> "
                      f"'{(p_s[0].strip() if p_s else '')}'; "
                      f"test -{len(m_t)}/+{len(p_t)}: "
                      f"'{(m_t[0].strip() if m_t else '')}' -> "
                      f"'{(p_t[0].strip() if p_t else '')}'")
        check("P", "P0", "diff de código del slice = exactamente dos ficheros (AtmoShaderSource.kt mediump->highp y su test de expectativa), una línea cada uno",
              p0_ok, p0_det)

        # P1: halo propio del head P — evidencia work/comp-<P7>.json con
        # reference_sha256 OBLIGATORIO (sha256 del par web sin bloom);
        # THIN gateada (min >= 3 Y max|diff| <= 2.0); DEEP reportada.
        p_cp = os.path.join(HERE, "work", f"comp-{head[:7]}.json")
        p1_ok, p1_det = False, "sin evidencia"
        if os.path.exists(p_cp):
            j = json.load(open(p_cp))
            caps = j.get("captures", {})
            ref_sha = j.get("reference_sha256")
            if (j.get("head_sha", "").startswith(head[:7]) and ref_sha
                    and set(caps) >= {"android", "web"}
                    and all(cap_ok(caps[k]) for k in ("android", "web"))):
                a = caps["android"]["delta_B_by_sector"]
                w = caps["web"]["delta_B_by_sector"]
                ad = caps["android"].get("delta_B_deep_by_sector") or []
                if len(a) == len(w) and a and w and ad:
                    diffs = [abs(x - y) for x, y in zip(a, w)]
                    p1_ok = min(a) >= 3 and max(diffs) <= 2.0
                    p1_det = (f"THIN min and={min(a):.2f}; max|diff|={max(diffs):.2f} <= 2.0; "
                              f"DEEP and min/max={min(ad):.2f}/{max(ad):.2f}; "
                              f"reference_sha256 declarado")
                else:
                    p1_det = "sectores android/web de distinta longitud o DEEP ausente"
            else:
                p1_det = (f"params incompletos (capturas: {sorted(caps)}; "
                          f"reference_sha256: {'declarado' if ref_sha else 'AUSENTE'})")
        check("P", "P1", "halo del head P: comp con reference_sha256 (par web sin bloom); THIN min >= 3 y max|diff| <= 2.0; DEEP reportada",
              p1_ok, p1_det)

    # --------------------------------------------------- etapa E — gates
    # v4 (§2.3): el bloque E se activa por NOMBRE, no por índice —
    # P (índice 4 en ORDER) no debe dispararlo.
    if ORDER.index(sl) >= ORDER.index("E"):
        e_main = [f for f in d_slice if f.startswith("app/src/main")]
        check("E", "E0", "slice E sin código de producción (solo tests/docs/scripts/evidencia)",
              not e_main, str(e_main))
        p_fps = os.path.join(HERE, "work", f"fps-{head[:7]}.json")
        ok_fps, det_fps = False, "sin evidencia"
        if os.path.exists(p_fps):
            j = json.load(open(p_fps))
            wins = j.get("windows", [])
            ok_fps = (j.get("head_sha", "").startswith(head[:7])
                      and j.get("preset") == "16/6"
                      and "latency" in str(j.get("method", "")).lower()
                      and len(wins) == 6
                      and all(w.get("avg_fps", 0) >= 29 for w in wins))
            det_fps = f"{len(wins)} ventanas, min avg {min((w.get('avg_fps', 0) for w in wins), default=0):.2f}"
        check("E", "E1", "gate FPS: 6 ventanas >= 29 en preset 16/6 (runbook --latency del expediente)",
              ok_fps, det_fps)
        p_h = os.path.join(HERE, "work", f"halo-{head[:7]}.json")
        ok_h, det_h = False, "sin evidencia"
        if os.path.exists(p_h):
            j = json.load(open(p_h))
            cap = j.get("capture", {})
            bands = j.get("bands", {})
            secs = j.get("sectors", [])
            min_db = j.get("delta_B_min_illuminated")
            if min_db is None and secs:
                ill = j.get("illuminated_limb", [75, 225])
                in_ill = [s.get("delta_B", 0) for s in secs
                          if ill[0] <= ((s.get("deg", 0) + s.get("deg_end", 0)) / 2) <= ill[1]]
                min_db = min(in_ill) if in_ill else None
            ok_h = (j.get("head_sha", "").startswith(head[:7])
                    and cap.get("w") == 610 and cap.get("h") == 620 and cap.get("dpr") == 1
                    and j.get("n_angles") == 720 and j.get("sector_deg") == 15
                    and bands.get("THIN") == [258, 278] and bands.get("DEEP") == [262, 287]
                    and j.get("illuminated_limb") == [75, 225]
                    and min_db is not None and min_db >= 3)
            det_h = f"delta_B min iluminado: {min_db}"
        check("E", "E2", "gate halo: 610x620 DPR1, 720 angulos/15deg, THIN/DEEP, limbo 75-225, delta_B >= 3",
              ok_h, det_h)
        e3 = [l for l in git("ls-tree", "-r", "--name-only", ref).splitlines() if l.startswith("docs/r4/")]
        check("E", "E3", "informe de gate en docs/r4/ (tablas de validación del plan §7)",
              len(e3) >= 2, f"ficheros docs/r4: {len(e3)}")

    # ------------------------------------------------------------- veredicto
    n_pass = sum(1 for r in results if r[3])
    n_tot = len(results)
    print("=" * 78)
    print(f"R4_DISPLAY_CHECK.py v6 DEEP GATE (R5, 2026-11-12, base v5 original ba433297) — ref: {ref} @ {head[:10]} · slice {sl}")
    print(f"BASE sellada: {BASE[:10]} · pin web: 8cdc334 · D1-D6 RATIFICADAS · ancla 6722016420")
    print("=" * 78)
    cur = None
    for g, cid, desc, ok, det in results:
        if g != cur:
            print(f"\n[{g}]")
            cur = g
        line = f"  {cid:>4} [{'PASS' if ok else 'FAIL'}] {desc}"
        if det and not ok:
            line += f"  -> {det}"
        print(line)
    print("\n" + "=" * 78)
    print(f"TOTAL: {n_pass}/{n_tot} PASS")
    print(f"VEREDICTO: {'FIRME (slice ' + sl + ')' if n_pass == n_tot else 'NO FIRME'}")
    return 0 if n_pass == n_tot else 1


if __name__ == "__main__":
    sys.exit(main())
