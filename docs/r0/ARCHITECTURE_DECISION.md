# Architecture decision - Android baseline (R0)

Author: GLM (relayed by David / assembled by Beam), 2026-11-02.

## Core choice
- **Native Kotlin + Jetpack Compose shell**.
- **Renderer on OpenGL ES 3.1**, shipping a port of the desktop GLSL 400 core
  pipeline (`#version 400 core` -> `#version 310 es` where possible, with a 300 es fallback
  where compute/ES3.1 is absent). Shader math lives in the repo, not in a black-box engine.
- **Do not** use React Native/r3f or SceneView as the main engine: the volumetric
  pipeline and battery/budget rules do not survive a JS bridge easily; SceneView
  is acceptable only as a glTF supplement (Earth variants), never as the core
  render loop.
- `render-on-demand` where the frame is static (Tesla plan: not every frame).

## Contracts, not languages
- Shared contract as **JSON Schema** (`docs/contracts`), from the web `types.ts`.
- TS types stay on the web side; Kotlin data classes (kotlinx.serialization) on Android.
- No shared runtime between the two stacks; shared invariants and golden tests.
- Degrade semantics from desktop (`off/degraded/failed` + honest reporting) are preserved byte-for-byte in concept.

## What not to do
- No SDL "web as runtime proxy" requirement in mobile v1.
- No cloud side-channel for private keys/certs in later release.
- No synthetic data.
