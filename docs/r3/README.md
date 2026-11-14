# R3 - Escena v1 anchor

R3 es el HUD visual de datos + luces nocturnas + shell + ionosondas + HUD.
Estado del tablero (closed dates 2026-11-07):
- R3.0 web texturas NASA del web: commit 8cdc334 (web repo).
- R3.1 Android colormaps espejo: commit 72612b9 (#16 merged).
- R3.2 cerrado 2026-11-07: luces nocturnas + shell atmosferico (golden r32b2/b3; #23 e9e244c + #24 f57ce7c -> d246127).
- R3.3 cerrado 2026-11-07: HUD produccion Kp/Dst/foF2 + primeros consumidores release (fila #25/#26 -> 81fc214; gate de dispositivo FIRME; sesion movil N/A declarada sin SIM).
- R3.4 en curso: capa ionosondas + pointSize clamp 2-30 tests. Plan en `main`
  (`docs/r3/PLAN-R3.4-IONOSONDE-LAYER.md`, PR #38); implementacion A-E en
  `feat/r3.4-ionosonde-layer`. Pendiente: gate de dispositivo y revision GLM.

Nomenclatura: el track de **paridad visual** cerrado el 2026-11-13 usa los
nombres "R4"/"R5" ("paridad-R5"). No son las fases R4 (Tiempo y profundidad) ni
R5 (Cielo vivo) de `docs/r0/IMPLEMENTATION_SLICES.md`, que siguen sin empezar.

Cross-reference: #15 tracker
Decision Texture: A (downscale 2048x1024), ver nota GLM R2/R3.0.
Riders del backlog:
- N1-N4 GLM sobre ColormapsTest.kt
- kc2g failover (backlog), persistencia disco (new scope), P26 text-cleanup.

