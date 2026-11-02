# Test gates (R0 baseline)

Author: GLM, 2026-11-02.

- `app` JVM unit tests:
  - adapter cache and TTL
  - degradation state transitions
  - JSON schema shape
- `androidTest` (emulator, SwiftShader) verifies:
  - GL ES 3.1 presence
  - `input` actions basic (rotation)
- **CI** GitHub Actions:
  - `assembleDebug`, `test`, `lint`
  - reject marker/merges
- **Release** AAB: sign and minify; tests not always run on release? In proposed gates: unit tests stay in PR stage.
- Device matrix (David) = model list later (note: Mi 10 Lite API30, Adreno 620).
R0/R1–R2 do not require no fixtures from SAO.

