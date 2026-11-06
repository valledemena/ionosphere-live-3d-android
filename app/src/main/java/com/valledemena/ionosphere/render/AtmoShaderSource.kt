package com.valledemena.ionosphere.render

// Port 1:1 of ATMO_VERT / ATMO_FRAG from the web reference
// (repo ionosphere-live-3d @ 8cdc334, src/components/ionosphere/shaders.ts L113-215).
// Syntax layer adapted to the Android renderer (ShaderProgram prepends #version 310 es):
//   - varying -> in/out, gl_FragColor -> declared out fragColor, precision added in FRAG
//   - vertex attribute / matrix names follow the Earth program convention
//     (aPosition / uMvp / uModel, see GlobeRenderer L80-89)
// Physics constants are interpolated from AtmoShaderConstants (single source of truth).
object AtmoShaderSource {
    const val ATMO_VERT = """
uniform mat4 uMvp;
uniform mat4 uModel;
in vec3 aPosition;
out vec3 vFragPos;
void main() {
  vec4 wp = uModel * vec4(aPosition, 1.0);
  vFragPos = wp.xyz;
  gl_Position = uMvp * wp;
}
"""

    val ATMO_FRAG: String = """
precision mediump float;
uniform vec3 uSunDir;
uniform vec3 uCameraPos;
uniform float uIntensity;
uniform float uSteps;        // 8..16 (calidad)
uniform float uLightSteps;   // 3..6
in vec3 vFragPos;
out vec4 fragColor;

const float PLANET_R = ${AtmoShaderConstants.PLANET_R};
const float ATM_R    = ${AtmoShaderConstants.ATM_R};   // ~162 km
const float H_R      = ${AtmoShaderConstants.H_R};
const float H_M      = ${AtmoShaderConstants.H_M};
// beta scaled to planet units (beta_m * R_planet_m): Rayleigh (450,550,650nm) / Mie
const vec3  BETA_R   = vec3(${AtmoShaderConstants.BETA_R_R}, ${AtmoShaderConstants.BETA_R_G}, ${AtmoShaderConstants.BETA_R_B});
const float BETA_M   = ${AtmoShaderConstants.BETA_M};
const float G_MIE    = ${AtmoShaderConstants.G_MIE};

float raySphere(vec3 ro, vec3 rd, float r) {
  float b = dot(ro, rd);
  float c = dot(ro, ro) - r * r;
  float h = b * b - c;
  if (h < 0.0) return -1.0;
  return -b - sqrt(h);
}

// density accumulated toward the sun along [p, atmosphere exit]
vec2 lightOD(vec3 p, vec3 sunDir) {
  float tExit = raySphere(p, sunDir, ATM_R);
  if (tExit <= 0.0) return vec2(0.0);
  float dt = tExit / uLightSteps;
  float odR = 0.0, odM = 0.0;
  for (int i = 0; i < ${AtmoShaderConstants.LIGHT_STEPS_MAX}; i++) {
    if (float(i) >= uLightSteps) break;
    vec3 q = p + sunDir * (dt * (float(i) + 0.5));
    float h = max(length(q) - PLANET_R, 0.0);
    odR += exp(-h / H_R) * dt;
    odM += exp(-h / H_M) * dt;
  }
  return vec2(odR, odM);
}

void main() {
  vec3 ro = uCameraPos;
  vec3 rd = normalize(vFragPos - uCameraPos);
  vec3 sun = normalize(uSunDir);

  // segment inside the atmosphere
  float t0 = raySphere(ro, rd, ATM_R);
  if (t0 < 0.0) discard;
  float t1 = raySphere(ro, rd, PLANET_R); // planet entry (if hit) or atmosphere exit
  if (t1 < 0.0) t1 = distance(ro, vFragPos); // back-face fragment: far limit

  // camera outside: t0 = entry; if inside, t0 should be 0
  if (dot(ro, ro) > ATM_R * ATM_R && t0 > 0.0) { /* ok */ } else { t0 = max(t0, 0.0); }
  float segLen = t1 - t0;
  if (segLen <= 0.0) discard;

  int N = int(uSteps);
  float dt = segLen / uSteps;
  float mu = dot(rd, sun);
  float phaseR = 3.0 / (16.0 * 3.14159265) * (1.0 + mu * mu);
  float g = G_MIE;
  float phaseM = 3.0 / (8.0 * 3.14159265) * ((1.0 - g * g) * (1.0 + mu * mu)) /
                ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));

  vec3 sumR = vec3(0.0);
  vec3 sumM = vec3(0.0);
  float odR = 0.0, odM = 0.0;

  for (int i = 0; i < ${AtmoShaderConstants.STEPS_MAX}; i++) {
    if (i >= N) break;
    vec3 p = ro + rd * (t0 + dt * (float(i) + 0.5));
    float h = max(length(p) - PLANET_R, 0.0);
    float dR = exp(-h / H_R) * dt;
    float dM = exp(-h / H_M) * dt;
    odR += dR; odM += dM;

    // planet shadow: the ray to the sun from p must not cross Earth
    float tPlanet = raySphere(p, sun, PLANET_R);
    if (tPlanet > 0.0) continue;

    vec2 lod = lightOD(p, sun);
    vec3 tau = BETA_R * (odR + lod.x) + BETA_M * ${AtmoShaderConstants.EXTINCTION_MIE_SCALE} * (odM + lod.y);
    vec3 att = exp(-tau);
    sumR += dR * att;
    sumM += dM * att;
  }

  vec3 col = (sumR * BETA_R * phaseR + sumM * BETA_M * phaseM) * uIntensity * ${AtmoShaderConstants.INTENSITY_SCALE};

  // faint sundisk blended into the limb (redder at dusk via extinction)
  fragColor = vec4(col, 1.0);
}
"""
}
