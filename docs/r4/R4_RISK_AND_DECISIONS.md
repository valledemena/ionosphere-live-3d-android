# R4 — Riesgos y decisiones

**Slice P — precisión del pase de atmósfera**  
**Base:** `main @ bfea5d8`  
**Instrumento:** `R4_DISPLAY_CHECK_v4.py` congelado (`ac36bb81…7fda`)  
**Estado:** cambio de una línea, pendiente de medición P1 y CI del SHA exacto.

---

## 1. Decisión

El pase de atmósfera (`AtmoShaderSource.kt`, `ATMO_FRAG`, L24) pasa de
`precision mediump float;` a `precision highp float;`.

Es la única línea de código del slice. No se toca ningún otro fichero de
`app/src/main`, no se modifica el umbral de E2 y no se reabre el instrumento
v4 congelado.

---

## 2. Causa raíz

El gate E2 falsó un defecto de puerto que el emulador no podía revelar:

- En SwiftShader, `mediump` se comporta como `fp32`; en Mali, como `fp16`.
- La captura real del Mi 10 Lite dio THIN mínimo `1.74` con `12/4`, `1.66`
  con `14/5` y `1.79` con `16/6`, frente al umbral `>= 3`.
- La paridad frente a la referencia web quedó en `19.51`, frente a la
  tolerancia `2.0`.
- El pase de atmósfera era el único pase de la cadena que seguía en
  `mediump`; los pases B/C ya declaraban `highp`.

El defecto no era un fallo del gate: era exactamente lo que el gate tenía que
detectar.

---

## 3. Errata histórica (texto canónico, verbatim)

> **ERRATA GLM (2026-11-11, ANCLA-V4 §9) — W10 falso PASS en los FIRME de C y D.** El instrumento v3 verificaba W10 con un grep global de «precision highp» en `app/src/main`; las seis declaraciones highp de los pases B/C (BloomPass ×3, GodRaysPass, ToneMapPass, PostProcessChain) satisfacían el grep mientras el pase de atmósfera (`AtmoShaderSource.kt`) permanecía en «precision mediump». El defecto no podía manifestarse en SwiftShader (mediump = fp32) y sólo se reveló en Mali (mediump = fp16) durante los gates del slice E: E2 min 1.74 (12/4) / 1.66 (14/5) / 1.79 (16/6) frente al umbral ≥ 3; paridad 19.51 frente a tol 2.0. Causa raíz verificada por triple fuente (árbol golden L24; three.js antepone highp por defecto; instrumento L430–441). Los dictámenes FIRME de C y D se sostienen como actos del instrumento congelado legítimo de su tiempo, con esta errata declarada; la corrección sustantiva llega por el slice P (mediump→highp) y el instrumento v4 (W10 acotado al pase de atmósfera sin ruta mediump; S5/S6 sin golden en INTOCABLE_ABS; S7 por blob con re-sellado declarado). El gate E2 hizo exactamente su trabajo: falsó un defecto de puerto invisible al emulador. **Registro complementario (F5)**: S5/S6 de v3 mantenían los cuatro golden en la blacklist por ruta — habría bloqueado P y E post-P; corregido en v4 trasladando la integridad del golden a S7-por-blob.

---

## 4. Corrección

- Fichero: `app/src/main/java/com/valledemena/ionosphere/render/AtmoShaderSource.kt`
- Línea 24: `precision mediump float;` → `precision highp float;`
- Diff de código: exactamente un fichero y una línea.
- La doc de este slice viaja en el mismo single-commit; no cuenta como
  cambio de código para P0.

---

## 5. Gates del slice P

- **P0:** diff de código exactamente un fichero (`AtmoShaderSource.kt`) con
  una línea cambiada.
- **P1:** halo propio del head P, captura 610×620, preset `14/5`, bloom OFF,
  modelo identity, ON/OFF por visibilidad del shell, referencia web sin bloom
  con `sha256` declarado. THIN `min >= 3` y `max|diff| <= 2.0`; DEEP se
  reporta.
- **S10:** CI 3/3 del SHA exacto, sólo tras push/PR.
- **Objetivo:** checker v4 selector P `48/48`.

---

## 6. Relación con E

El candidato E (`eec186a`) queda como historia de diagnóstico: E1 PASS, E2
FAIL documentado, E3 PASS, sin push. El E post-P se re-cortará sobre el main
nuevo, llevará su propio `reseal-<E7>.json` y se medirá de nuevo con el
instrumento congelado.

`GATE-E-VALIDATION.md` queda fuera de este slice; no se incluye en el commit
de P y sigue pendiente de E.

---

## 7. Estado

- P0: pendiente de verificación mecánica sobre el commit.
- P1: pendiente de medición en Mi 10 Lite.
- S10: pendiente de push/CI.
- Checker v4 P: pendiente de evidencia.
- Sin push y sin congelación adicional.

— DeepSeek/Buffy, 2026-11-12
