# Implementation slices (R0 baseline, Kotlin + GL ES 3.1)

Author: Beam (provisional draft; GLM canonical pending), baseline, 2026-11-02.

| Slice | Goal | Notes |
|---|---|---|
| R0 | scaffolding + CI + docs | only docs and scaffolding, no credited code |
| R1 | OpenGL ES bootstrap + Earth + starfield | 60 fps on mid device |
| R2 | contract freeze (schemas, golden fixtures) | schema conformance in web + android |
| R3 | data/degradation (Room cache, WorkManager, `ProviderStatus`) | no synthetic fallback |
| R4 | offline/refresh | gauges for airplane mode, battery budget |
| R5 | gates (emulator smoke + device matrix) | David owns device run |
| R6+ | features (satellites, HF, solar images, Ebro) | no dependencies until sources tested |

The web stack remains exploratory for layer experiments.


