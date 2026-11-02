
## Direct-source requirement
- Mobile should behave like C++: a self-contained app that pulls data from public endpoints, no external web backend as requirement (but it may use the web source's routes if available).
# Round 0 request to GLM

Source repositories:
- Desktop native: https://github.com/valledemena/ionosphere-live-3d
- Web reference: https://github.com/valledemena/ionosphere-live-3d-web

## Input facts
### Desktop
- OpenGL 4.0 core (`glfwWindowHint(4,0)`, `#version 400 core`)
- Ionospheric data real-time and degraded semantics: off / degraded / failed
### Web
- Next.js + Three.js, 15 API routes and public data sources

## Deliverables requested from GLM
1. Architecture decision: Java/Kotlin + native + OpenGL ES/GLES renderer vs optional serial? Must behave like desktop single app, with direct public APIs.
2. Normalized data contract for mobile.
3. Ordered delivery slices R0-R5.
4. Testing gates and device coverage.

## Open questions:\n0. No public server URL will be used by default; if one is available later, it may be used only as convenience.\n1. Native Android vs hybrid stack.
2. Rendering choice for mobile performance.
3. Cached item policy.
4. Test matrix.
5. Release slices.

