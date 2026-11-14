# R3 - Escena v1 anchor

R3 es el HUD visual de datos + luces nocturnas + shell + ionosondas + HUD.
Estado del tablero (closed dates 2026-11-07):
- R3.0 web texturas NASA del web: commit 8cdc334 (web repo).
- R3.1 Android colormaps espejo: commit 72612b9 (#16 merged).
- R3.2 cerrado 2026-11-07: luces nocturnas + shell atmosferico (golden r32b2/b3; #23 e9e244c + #24 f57ce7c -> d246127).
- R3.3 cerrado 2026-11-07: HUD produccion Kp/Dst/foF2 + primeros consumidores release (fila #25/#26 -> 81fc214; gate de dispositivo FIRME; sesion movil N/A declarada sin SIM).
- R3.4 cerrado 2026-11-14: capa ionosondas + pointSize clamp 2-30 tests.
  Plan en `main` (`docs/r3/PLAN-R3.4-IONOSONDE-LAYER.md`, PR #38);
  implementacion A-E en PR #39, merge `e12e100`. Gate T4/T6 con umbral
  enmendado a 29,5: medium desenchufado 29,52/29,54 fps; ratio ON/OFF 1,0008;
  low 29,90; punto <=70 px. Acta `work/r34-device-23bb047-final.txt` y capturas
  T6-1..T6-4 ancladas en el relay; T7 firmado por David
  (pull/39#issuecomment-6875951335).

R3 - Escena v1 queda completo (R3.0-R3.3 cerrados 2026-11-07; R3.4 cerrado
2026-11-14 en `e12e100`). El cierre administrativo de #15 se publica aparte.

Nomenclatura: el track de **paridad visual** cerrado el 2026-11-13 usa los
nombres "R4"/"R5" ("paridad-R5"). No son las fases R4 (Tiempo y profundidad) ni
R5 (Cielo vivo) de `docs/r0/IMPLEMENTATION_SLICES.md`, que siguen sin empezar.

Cross-reference: #15 tracker
Decision Texture: A (downscale 2048x1024), ver nota GLM R2/R3.0.
Riders del backlog:
- N1-N4 GLM sobre ColormapsTest.kt
- kc2g failover (backlog), persistencia disco (new scope), P26 text-cleanup.

