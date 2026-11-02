# GLM Round 0 response (post-verification)

- OpenGL 4.0 desktop reference confirmed exact.
- Web repo and Android repo verified.
- Proposed direction: Kotlin + Jetpack Compose + renderer on OpenGL ES 3.1.
  - Do not use React Native/r3f to maintain volumetric rendering fidelity.
  - Do not use SceneView except for stable glTF assets.
- Structure: share contract, not language; JSON Schema codegen for Kotlin + TS models.
- Data strategy: direct calls v1, web `/api` is contract source, not dependency.
- Slices are already mapped R0-R5, with R1 stack decision and R2 data contract as priorities.
- GLM acceptance gates: `ARCHITECTURE_DECISION.md`, `DATA_CONTRACT.md`, `IMPLEMENTATION_SLICES.md`, `TEST_GATES.md`.
- Do not ingest SAO fixtures without Observatori permission (same hard rule applies).
- Blockers: devices list (model, Android version, GPU/RAM), whether a hosted web instance exists, texture memory ceiling.

Response-from-GLM-2026-11-02

## Direct-source requirement (clarification from David)
- There is no public web deployed. Mobile must behave like C++: self-contained with direct public calls; if web exists, it can be an auxiliary data source, but not a required dependency.


