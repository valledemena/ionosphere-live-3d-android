# PLAN R4 (ii) — Display parity display + control de cámara (Android)

Base sellada: `main @ 00ec7cb` (R3.3 HUD + kc2g)
Pin web de referencia: `8cdc334` (limpio, antes/después)
Fecha plan: 2026-11-08 · Beam

Contrapartida GLM: `download/PRECHECK-R4-CADENA-PIN-GLM-20261108.md` + zip (constantes y criterios con sus fuentes).

## 0) No tocar (intocables en esta fila)

- `data/`, `gl/`, `render/` existentes, build, manifest
- golden 4 files + `ui/DataPanelHost.kt`
- todo lo publicado para R3.3 y kc2g

## 1) Objetivo arquitectónico

Traer el Android GLES 3.1 a equivalencia funcional con la cadena web:

```
Scene world-space (Earth + atmo shell + ion layers)
→ Offscreen HalfFloat RT (4×MSAA) → RenderPass
→ Bloom separable — strength .55 · radius .5 · threshold .72
→ OutputPass — ACES Filmic (renderer.toneMapping = ACESFilmic) · exposure 1.12 (uExposure 1.12)
               + sRGB encode
               (exposure is NOT interactive; controls.minDistance = 1.12 is a camera
                limit and must not confluirse)
→ God-rays CPU gate + 12-tap additive pass (post-ACES)
→ canvas (SurficeView), UI (HUD) arriba
```

Capas web → Android:

| Web (three / EffectComposer) | Android (GLES3.1) |
|---|---|
| WebGLRenderer HalfFloat RT ×4 samples | FBO color + depth, RGBA16F, DEPTH, 4×MSAA |
| RenderPass | Earth + atmo shell + shells into FBO |
| UnrealBloomPass | horizontal + vertical separable Gaussian (trail) |
| OutputPass (ACES) | ACES tone mapping + sRGB encode in fragment |
| god-rays CPU gate (12 taps, NTAPS, decay=.9, maxradius=.45) | CPU sun-projection gate + 12-tap pass |
| OrbitControls (fov42, minDist 1.12, max30, damping .06, autoRotate .35 cinema) | `CameraController` mutables (yaw/pitch/roll), pan disabled; touch via gesture; auto-r optional |
| Shell additive, atmo shell | Idéntico; atmo queda en el mismo FBO |
| Sun glow | composite, no inline modulation |
| Video export / resize | export deferred; tomar el FBO sólo desacelerado |

## 2) Medición (definida ANTES de cualquier código)

- **Captura:** 610×620 canvas, DPR 1 (físico análogo); recorte del limbo iluminado/crepuscular 75–225°; bandas THIN=258–278, DEEP=262–287; 720 ángulos/360°, 15° sectores; `delta_B = ON−OFF`, umbral ≥ 3.
- **FPS:** 6 ventanas ≥29 sostenido en encuadre worst-case (cámara cercana, pasos 16/6), método expediente `--latency` BLAST.
- **Reference Camera:** web distance 3.3 R_E, fov 42; actual Android 3.2 R_E, fov45; la migración de cámara exacta entra en Slice D (si David la ratifica como display-matching).
- **Controls:** manual + autoRotate boolean.
- **Device constraint:** MIUI bloquea input injection; pruebas de cámara son humanas; hazard de doze permanece.

## 3) Decisiones antes de código

- **D1:** ratificar (i) naive o (ii) paridad visual — GLM technical note: (ii) es razonable, es el único verifiable con gate.
- **D2:** target FPS cap — web composer ~75 Hz vs Android 60 Hz; TARGET_FPS=30 existente.
- **D3:** OrbitControls damping 0.06 — port exacto o tuning touch.
- **D4:** exports (video) fuera ahora o R5.
- **D5:** placeholder `2048×1024` vs NASA pin.

## 4) Plan de slicing (props)

### Slice A — Offscreen RT + compositing shell (plumbing)
- Realloc `GlobeSurfaceView` a FBO (color+depth, 4×MSAA, RGBA16F); draw Earth + atmo shell.
- No cambio visual inmediato; tests de tamaño.
- Smoke en Mi 10 Lite.

### Slice B — ACES + sRGB
- Fragment pass ACES Filmic + exposure 1.12.
- Captura comparativa debug vs Web.

### Slice C — Bloom/god-rays
- Separable Gaussian (strength .55/radius .5/threshold .72).
- CPU gate sun projection + 12-tap additive (NTAPS=12, GAIN=.12).

### Slice D — Camera controls
- Touch swipe, pinch, rotate (pan disabled).
- Reutilizar golden shader.

### Slice E — Tests/checker + gate
- R4_DISPLAY_CHECK.py con BASE `00ec7cb` + golden RT.
- 6 ventanas FPS + halo radial.
- Device gate human (Mi 10 Lite).

## 5) Dependencias / costos
- GLES 3.1 commands (FBO color/depth, MRT), offscreen RT.
- CPUs: god-rays 12-tap por pass ~ cheap; bloom 2 passes; Mi 10 Lite ~30 fps.
- MSAA + half-float (RGBA16F) en GLES.

## 6) Risk / guardrails
- No alterar `render/AtmoShaderSource.kt` golden salvo slice autorizado.
- Keep `data/` mínimo; no contratos sin GLM.
- Plan sketch, implement later via formula III y GLM checker.

## 7) Entregables (docs-only ahora)
- `docs/r4/PLAN-R4-DISPLAY-PARITY.md`
- `R4_RISK_AND_DECISIONS.md` entries D1-D5
- Tabla de medición de validation

**Return:** one-page plan listo para GLM generar `R4_DISPLAY_CHECK.py` y Beam ejecutar slicing.

No código, plan phase only.

