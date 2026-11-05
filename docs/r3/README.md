# R3 - Escena v1 anchor

R3 es el HUD visual de datos + luces nocturnas + shell + ionosondas + HUD.
Estado del tablero (closed dates 2026-11-06):
- R3.0 web texturas NASA del web: commit 8cdc334 (web repo).
- R3.1 Android colormaps espejo: commit 72612b9 (#16 merged).
- R3.2 en arranque: luces nocturnas + shell atmosferico + texture decisions.
- R3.3 pendiente: HUD produccion Kp/Dst/foF2 + primeros consumidores release.
- R3.4 pendiente: capa ionosondas + pointSize clamp 2-30 tests.

Cross-reference: #15 tracker
Decision Texture: A (downscale 2048x1024), ver nota GLM R2/R3.0.
Riders del backlog:
- N1-N4 GLM sobre ColormapsTest.kt
- kc2g failover (backlog), persistencia disco (new scope), P26 text-cleanup.

