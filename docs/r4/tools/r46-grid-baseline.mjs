#!/usr/bin/env node
/**
 * r46-grid-baseline.mjs — arnes reproducible de la medicion base de R4.6.0.
 *
 * Mide la construccion de la rejilla de perfiles del pin web 8cdc3348 SIN
 * duplicar fisica: materializa del commit pin los modulos puros del pin,
 * los empaqueta con el esbuild del propio repo web y ejecuta la cadena de
 * produccion (stations -> interpProfiles -> applyDayNight -> buildDensityVolume).
 *
 * Uso:
 *   node r46-grid-baseline.mjs <ruta-del-clon-web> [--runs N] [--warmup N]
 *
 * Requisitos: git, Node >= 20.11 (import.meta.dirname), un clon de
 * valledemena/ionosphere-live-3d-web con node_modules instalado (esbuild
 * viene como dependencia de vite).
 * No escribe en el clon: trabaja en un directorio temporal.
 *
 * Invocacion de esbuild robusta: se inspecciona la cabecera del fichero y se
 * elige la forma primaria segun su naturaleza — ejecucion directa del path si es
 * un ejecutable nativo (ELF/PE), `node <bin>` si es un script JS — con fallback
 * a la otra forma si la primaria falla. La forma usada se declara por stderr y en
 * el campo esbuild_invocation del JSON. Motivo: `npm ci` con scripts de
 * instalacion sustituye node_modules/esbuild/bin/esbuild por el binario nativo en
 * Linux, donde `node <bin>` muere con SyntaxError.
 *
 * Salida: un JSON por stdout (entorno, pin verificado, tiempos y memoria).
 */
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { closeSync, existsSync, mkdtempSync, openSync, readFileSync, readSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";

const PIN = "8cdc3348ab087d10dd43807af217da4e246d996d";
const FILES = {
  "src/lib/iono/profileGrid.ts": "73207e86298742c3928078660b9ee2d93e9fde33",
  "src/lib/iono/ionomath.ts": null,
  "src/lib/iono/densityVolume.ts": null,
  "src/lib/iono/layerProfile.ts": null,
};

const argv = process.argv.slice(2);
const webRootArg = argv.find((a) => !a.startsWith("--"));
if (!webRootArg) {
  console.error("uso: node r46-grid-baseline.mjs <ruta-del-clon-web> [--runs N] [--warmup N]");
  process.exit(2);
}
// Absoluta SIEMPRE: el bundle se lanza con cwd en el temporal, asi que un
// webRoot relativo resolveria contra el directorio equivocado (bug real,
// detectado al ejecutar el arnes copiado con ruta relativa).
const webRoot = path.resolve(webRootArg);
const flag = (name, dflt) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] ? Number(argv[i + 1]) : dflt;
};
const RUNS = flag("runs", 15);
const WARMUP = flag("warmup", 3);

function git(args, cwd) {
  return execFileSync("git", args, { cwd, encoding: "utf8", maxBuffer: 1 << 26 }).trim();
}

// 1. El clon debe contener el commit pin como objeto (no hace falta checkout).
try {
  git(["cat-file", "-e", `${PIN}^{commit}`], webRoot);
} catch {
  console.error(`ERROR: el clon no contiene el commit pin ${PIN}. Haz: git fetch --all`);
  process.exit(3);
}

// 2. Verificacion de arbol: blobs de los modulos puros en el pin.
const blobs = {};
for (const rel of Object.keys(FILES)) {
  blobs[rel] = git(["rev-parse", `${PIN}:${rel}`], webRoot);
}
const failures = Object.entries(FILES).filter(([rel, want]) => want && blobs[rel] !== want);
if (failures.length) {
  console.error(`ERROR: blob inesperado en el pin: ${JSON.stringify(failures)}`);
  process.exit(4);
}

// 3. Materializacion del subarbol del pin en un temporal (git archive + tar).
const tmp = mkdtempSync(path.join(os.tmpdir(), "r46-"));
const cleanup = () => rmSync(tmp, { recursive: true, force: true });
process.on("exit", cleanup);
// git archive a fichero + tar -x: no depende del shell ni del separador |.
const tarPath = path.join(tmp, "pin.tar");
execFileSync("git", ["archive", `--output=${tarPath}`, PIN, "src/lib/iono"], { cwd: webRoot });
execFileSync("tar", ["-xf", "pin.tar"], { cwd: tmp }); // rutas relativas: MSYS tar lee "C:" como host remoto

const entry = path.join(tmp, "entry.ts");
writeFileSync(entry, readFileSync(path.join(import.meta.dirname, "r46-entry.ts"), "utf8"));

// 4. Bundle con el esbuild del repo web (viene con vite).
const esbuildBin = path.join(webRoot, "node_modules", "esbuild", "bin", "esbuild");
if (!existsSync(esbuildBin)) {
  console.error("ERROR: falta node_modules/esbuild en el clon web (npm ci).");
  process.exit(5);
}
const outFile = path.join(tmp, "bundle.mjs");

// 4.a Invocacion robusta. `npm ci` con scripts de instalacion deja
// node_modules/esbuild/bin/esbuild como binario nativo en Linux; `npm ci
// --ignore-scripts` (o un npm sin el postinstall del paquete) lo deja como el
// lanzador JS. La cabecera del fichero dice cual de las dos formas es valida:
// ejecucion directa del path, o `node <bin>`. Se intenta la forma primaria y,
// si falla, la otra; la usada se declara (stderr + campo del JSON).
function nativeFormat(file) {
  const fd = openSync(file, "r");
  const buf = Buffer.alloc(4);
  try {
    readSync(fd, buf, 0, 4, 0);
  } finally {
    closeSync(fd);
  }
  if (buf[0] === 0x7f && buf[1] === 0x45 && buf[2] === 0x4c && buf[3] === 0x46) return "ELF";
  if (buf[0] === 0x4d && buf[1] === 0x5a) return "PE";
  return null; // script JS (el shim de npm empieza por #!/usr/bin/env node)
}
const BUNDLE_ARGS = [entry, "--bundle", "--platform=node", "--format=esm", "--target=node20", `--outfile=${outFile}`, "--log-level=warning"];
const native = nativeFormat(esbuildBin);
const forms = native ? ["direct", "node-bin"] : ["node-bin", "direct"];
const attempts = [];
let esbuildInvocation = null;
for (const form of forms) {
  const cmd = form === "direct" ? esbuildBin : process.execPath;
  const args = form === "direct" ? BUNDLE_ARGS : [esbuildBin, ...BUNDLE_ARGS];
  try {
    const out = execFileSync(cmd, args, {
      cwd: tmp,
      stdio: ["ignore", "inherit", "pipe"],
      encoding: "utf8",
      maxBuffer: 1 << 26,
    });
    if (out) process.stderr.write(out);
    esbuildInvocation = form;
    break;
  } catch (err) {
    const detail = String(err.stderr || err.message || "").split("\n").find((l) => l.trim()) ?? "";
    attempts.push(`${form}: exit ${err.status ?? "?"} — ${detail}`);
  }
}
if (!esbuildInvocation) {
  console.error(`ERROR: esbuild fallo en las dos formas (${native ?? "script"}):\n  ${attempts.join("\n  ")}`);
  process.exit(6);
}
process.stderr.write(`[r46] esbuild: cabecera=${native ?? "script"}; forma usada=${esbuildInvocation}` +
  `${attempts.length ? `; primaria fallida: ${attempts[0]}` : "; forma primaria"}\n`);

// 5. Ejecucion: el bundle imprime el JSON de medicion.
const report = execFileSync(process.execPath, ["--expose-gc", outFile, String(RUNS), String(WARMUP)], {
  encoding: "utf8",
  maxBuffer: 1 << 26,
});

const meta = {
  harness: "r46-grid-baseline.mjs",
  pin: PIN,
  harness_sha256: createHash("sha256").update(readFileSync(process.argv[1])).digest("hex"),
  esbuild_invocation: esbuildInvocation,
  esbuild_attempts: attempts,
  web_root_head: git(["rev-parse", "HEAD"], webRoot),
  web_root_status: git(["status", "--porcelain"], webRoot).length === 0 ? "limpio" : "sucio (irrelevante: se lee del pin, no del arbol de trabajo)",
  pin_blobs: blobs,
  pin_file_stats: Object.keys(FILES).map((rel) => {
    const buf = readFileSync(path.join(tmp, rel));
    return {
      path: rel,
      bytes: buf.length,
      lines: buf.toString("utf8").split("\n").length - 1,
      cr: (buf.toString("utf8").match(/\r/g) ?? []).length,
      sha256: createHash("sha256").update(buf).digest("hex"),
    };
  }),
};
console.log(JSON.stringify({ ...meta, measure: JSON.parse(report) }, null, 2));
