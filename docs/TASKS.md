# Task board

Status key: [ ] not started, [~] in progress, [x] done.

[~] R0 - Prime the mobile app scaffolding, tooling and permissions model.
[ ] R1 - Decide Android stack: native Kotlin/Jetpack Compose vs React Native.
[~] R2 - Discover source data contract points between web and desktop.
[ ] R3 - Build normalized data provider adapter surface (API + local cache).
[ ] R4 - Implement offline cold start / refresh behavior matching project degradation.
[ ] R5 - Define tests/checks and CI strategy for mobile code on GitHub.

Notes:
- Web endpoints in `ionosphere-live-3d-web/src/app/api` are the initial reference for data.
- Desktop C++ app gives behavior, defaults, and honest degradation ("off/failed" labels) for fidelity.
- Desktop repo has native Ebro/GIRO-style adapters; Android may proxy through the web layer to avoid duplicating network code.
