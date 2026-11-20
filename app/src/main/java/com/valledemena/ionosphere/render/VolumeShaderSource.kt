package com.valledemena.ionosphere.render

/**
 * Fuentes GLSL del pase volumetrico Ne(h) (R4.6.3 density + chapman).
 *
 * Port 1:1 de `VOL_VERT`/`VOL_FRAG` (`src/components/ionosphere/shaders.ts`
 * del pin web `8cdc334`, que a su vez es el port GLSL3 de
 * `src/Ionosphere/VolumeRenderer.cpp:48-141`). Adaptacion a ES 3 igual que el
 * resto del renderer:
 *
 * - cuerpo sin `#version` (lo antepone [com.valledemena.ionosphere.gl.ShaderProgram],
 *   que prueba 310 es y cae a 300 es);
 * - `varying` -> `in/out`, `gl_FragColor` -> `out vec4 fragColor`;
 * - el muestreo de la capa por `texelFetch` queda identico (la textura de capa
 *   va en NEAREST: un LINEAR corromperia las fronteras D/E/F1/F2).
 *
 * Unica divergencia declarada: el mapeo de longitud. La web usa el marco
 * ESCENA (`phi = atan(n.z, -n.x)`); el motor Android usa
 * `theta = pi - lon` (`core/iono/DensityVolume.kt: densityDir`), de modo que
 * el indice 0 de la rejilla (`lonIdx 0 = -180`) corresponde a
 * `u = fract(-atan(n.z, n.x) / 2pi)`. Verificado contra `sampleVolumeAt`
 * (`core/iono/VolumeSamples.kt`) en `VolumeShaderSourceTest`.
 *
 * Sin iso-bandas en el volumen: el glow de R4.6.7 vive en el raymarch de la
 * atmosfera, no aqui, y este slice no lo reabre.
 */
object VolumeShaderSource {

    /** Vertice: la malla ya vive en radio [AtmoShaderConstants]-independiente mundo. */
    const val VOLUME_VERT = """
uniform mat4 uMvp;
in vec3 aPosition;
out vec3 vWorldPos;
void main() {
  vWorldPos = aPosition;
  gl_Position = uMvp * vec4(aPosition, 1.0);
}
"""

    /** Cuerpo del fragmento; el bucle se corta en `uSteps` (<= 64 de referencia). */
    val VOLUME_FRAG: String = """
precision highp float;
precision highp int;
uniform highp sampler3D uDensity;
uniform highp sampler3D uLayer;
uniform highp sampler2D uColormap;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform float uInnerR;
uniform float uOuterR;
uniform float uOpacity;
uniform int uSteps;
uniform int uMode;      // 0 = density, 1 = chapman (capas D/E/F1/F2)
uniform ivec3 uVolRes;
in vec3 vWorldPos;
out vec4 fragColor;

bool raySphere(vec3 ro, vec3 rd, float r, out float t0, out float t1) {
  float b = dot(ro, rd);
  float c = dot(ro, ro) - r * r;
  float disc = b * b - c;
  if (disc < 0.0) return false;
  float s = sqrt(disc);
  t0 = -b - s;
  t1 = -b + s;
  return true;
}

void main() {
  vec3 ro = uCamPos;
  vec3 rd = normalize(vWorldPos - uCamPos);

  float tOutA, tOutB;
  if (!raySphere(ro, rd, uOuterR, tOutA, tOutB)) discard;
  float tEnter = max(tOutA, 0.0);
  float tExit = tOutB;

  float tInA, tInB;
  if (raySphere(ro, rd, uInnerR, tInA, tInB) && tInA > tEnter) {
    tExit = min(tExit, tInA);
  }
  if (tExit <= tEnter) discard;

  float dt = (tExit - tEnter) / float(uSteps);
  float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
  float tEnterJ = tEnter + dt * jit;
  vec3 accumColor = vec3(0.0);
  float accumAlpha = 0.0;

  for (int i = 0; i < ${VOL_STEPS_MAX}; i++) {
    if (i >= uSteps) break;
    float t = tEnterJ + dt * (float(i) + 0.5);
    if (t >= tExit) break;

    vec3 p = ro + rd * t;
    float sh0, sh1;
    float sunShadow = 1.0;
    if (raySphere(p, uSunDir, uInnerR, sh0, sh1) && sh0 > 1e-4) sunShadow = 0.0;
    float r = length(p);
    float altNorm = clamp((r - uInnerR) / (uOuterR - uInnerR), 0.0, 1.0);

    vec3 n = p / max(r, 1e-6);
    float lat = asin(clamp(n.y, -1.0, 1.0));
    // Marco MOTOR (no ESCENA): u = fract(-atan(n.z, n.x) / 2pi), lonIdx 0 = -180.
    float phi = -atan(n.z, n.x);
    float u = fract(phi / 6.28318530718);
    vec2 uv = vec2(u, lat / 3.14159265 + 0.5);

    float d = texture(uDensity, vec3(uv, altNorm)).r;
    vec3 col;
    if (uMode == 1) {
      // ID exacto por texelFetch (el LINEAR corromperia fronteras: 1.7 no
      // es ninguna capa).
      ivec3 ip = ivec3(clamp(vec3(floor(uv.x * float(uVolRes.x)),
                                         floor(uv.y * float(uVolRes.y)),
                                         floor(altNorm * float(uVolRes.z))),
                                  vec3(0.0), vec3(uVolRes) - 1.0));
      int w = int(texelFetch(uLayer, ip, 0).r * 255.0 + 0.5);
      if (w == 0)      col = vec3(1.00, 0.55, 0.10);
      else if (w == 1) col = vec3(0.20, 0.85, 0.30);
      else if (w == 2) col = vec3(1.00, 0.90, 0.20);
      else             col = vec3(0.20, 0.80, 1.00);
    } else {
      col = texture(uColormap, vec2(d, 0.5)).rgb;
    }
    col *= sunShadow;
    float a = clamp(d * d * uOpacity * dt * 40.0, 0.0, 1.0) * sunShadow;

    accumColor += (1.0 - accumAlpha) * a * col;
    accumAlpha += (1.0 - accumAlpha) * a;
    if (accumAlpha > 0.98) break;
  }
  if (accumAlpha <= 0.001) discard;
  fragColor = vec4(accumColor, accumAlpha);
}
"""
}
