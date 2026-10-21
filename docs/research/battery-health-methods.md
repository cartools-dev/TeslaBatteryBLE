# Battery health methods & data sources

Date: 2026-10-04

> Context: TeslaBatteryBLE is a local-first Android app (BLE, no cloud) that can read charge state (SOC, rated/estimated range, charge limit, energy added, charger power) and track parked drain. This brief asks which health estimates can be made locally from BLE-readable signals.

## Summary

- Best local-only SoH today is the **rated-range method** (displayed rated range ÷ original EPA rated range for the exact trim/year) plus the **energy-delta method** (kWh added ÷ SOC delta) averaged over many sessions; both use BLE-readable signals.
- **Coulomb counting is not possible over BLE** (no pack-current signal); it needs CAN (OBD) or Fleet Telemetry.
- Tesla's own **Service-Mode Battery Health Test** is the ground-truth reference and has no API; the app should let users log its result manually.
- Opt-in cloud, OBD dongles and fleet-cohort bands were researched and cut from this note; see git history.

## Estimation methods

| Method | Needs | Accuracy | Local-only? |
|---|---|---|---|
| (a) Rated-range | Rated range at known SOC (best at/near 100%, rested, moderate temp) + correct original EPA range for exact trim/year; BMS-calibrated pack | ±2–5% single reading; ±1–2% trended over weeks [1][2][3] | **Yes (BLE)** — rated range + SOC are BLE-readable |
| (b) Energy-delta / capacity | `energy_added` + SOC start/end of a charge session, large SOC swing (≥30–50%), no interruptions; repeat sessions | ±3–5% per session; ±1–3% averaged/filtered [4][5] | **Yes (BLE)** — energy added + SOC are BLE-readable |
| (c) Coulomb counting | Continuous pack-current integration (± offset/noise), full or large cycles; OCV rest correction | Exact in lab; drifts without OCV/Kalman correction [6] | **No over BLE** — no current signal; needs CAN or Telemetry |
| (d) Service-mode Health Test | Physical car + AC charger ≥5 kW, SOC <20%, up to 24 h, parked [7][8] | Reference-grade (direct V + ∫I between two OCV points) [9] | Yes (in-car, manual) but **no API** — user runs it, app logs result |

## Method details

### a) Rated-range method

Formula: `SoH ≈ (displayed_range / SOC_fraction) / EPA_range_when_new`. Best at exactly 100% (`displayed / EPA`); otherwise scale, e.g. 216 mi at 80% on a 300-mi-EPA car → 270 mi full → 90% SoH [4]. Tesla's displayed rated miles are linearly proportional to BMS capacity via a fixed Wh/mi constant, so falling full-charge miles track capacity loss [1]. Caveats: BMS estimate noise (±10–20 mi swings with temp, rest state, recent driving, OTA constant changes); always read rested ≥30 min, moderate temps, same SOC point, and trend over weeks, not single readings [2][3][5]. Must match EPA figure to exact model/trim/year — wrong baseline invalidates everything [4][5]. TeslaMate-style dashboards do exactly this (projected/rated range vs baseline over time).

### b) Energy-delta / capacity method

Formula: `Capacity_now = energy_added_kWh / SOC_delta_fraction`; `SoH = Capacity_now / Capacity_when_new`. Example: 26.25 kWh for 20→60% → 65.6 kWh usable; vs 75 kWh new → 87.5% [4]. Works with BLE charge sessions (energy added + SOC start/end already tracked). Accuracy improves with large SOC swings, stable SOC endpoints (rested, no sentry/climate load), and averaging; single sessions carry charging-loss and SOC-reporting error (AC `charge_energy_added` is charger-measured; DC variant is battery-measured). Best practice from community calculators: average the range-method and charge-method estimates and flag gaps >5 pp as mismatch [4].

### c) Coulomb counting (why BLE can't do it)

Integrate current over time (Ah in/out); over 100→0% gives usable capacity directly [6]. Drift from sensor bias/noise and <100% coulombic efficiency forces OCV rest recalibration or Kalman/particle-filter observers (dual-EKF standard in BMS literature) [6]. BLE exposes no current signal — only SOC, range, energy-added, power. Current lives on CAN (`pack current`, motor currents) and in Fleet Telemetry (`PackCurrent` at HV contactors, `DiMotorCurrent*`, `DCChargingPower`). So local coulomb counting requires the OBD path.

### d) Tesla service-mode Battery Health Test

What it does: controlled discharge (below 10%, possibly to 0%) then full AC recharge, measuring OCV-adjacent voltages at two stable no-load points plus ∫I between them to infer capacity vs rated [9]. Entry: Controls > Service > Battery Health (newer UI surfaces it without the old "service" passcode); conditions: Park, no battery/thermal alerts, no pending update, <20% SOC, AC charger ≥5 kW, up to 24 h, features (Sentry/climate) disabled [7][8]. Output: single SoH % vs when-new; may recalibrate range display [7]. No API exposes it; result must be read off the screen and typed into any app. Run rarely (each run is a full cycle; Tesla rate-limits re-runs; avoid public stations that drop sessions during the zero-draw discharge phase) [8]. Quick in-app alternative: Controls > Service > Battery Health panel / Tesla app Request Service > Battery & Charging > Range gives a pass/normal-range evaluation without the full test [8].

## Local plan (BLE only, no new hardware, no cloud)

1. Trended rated-range SoH: log rated range + SOC per session/park; compute full-range estimate, compare to user-confirmed EPA baseline (exact trim/year picker); show trend + confidence, not single readings.
2. Energy-delta SoH: per charge session `kWh / ΔSOC`; keep only clean sessions (ΔSOC ≥30%, uninterrupted, rested endpoints); running median.
3. Fused display: average the two estimators when both fresh; show gap flag (>5 pp = "check data") [4]; peer-band overlay from static published curves.
4. Charging-efficiency proxy: energy-added vs SOC-gain over time; parked-drain rate (already tracked) as health-adjacent signal.
5. Manual ground truth: field to log Service-Mode test % + date; recalibrate baseline display.
6. What NOT to claim: cell imbalance, remaining-life years, or "% SoH to 1 decimal" — show ranges and trend arrows.

## Sources (accessed 2026-10-04)

- [1] BatterMachine, "How Tesla Calculates Usable vs Rated SOC and Range" — https://www.battermachine.com/post/how-tesla-calculates-usable-vs-rated-soc-and-range-estimates
- [2] Tesla Motors Club, "The most accurate way to measure battery degradation" — https://teslamotorsclub.com/tmc/threads/the-most-accurate-way-to-measure-battery-degradation.170883/
- [3] Tesla Owners Online, "Battery Degradation - Rated Range calculation" — https://www.teslaownersonline.com/threads/battery-degradation-rated-range-calculation.14417/
- [4] LumenCalculator, "Battery Degradation Calculator" (range + charge formulas, 5 pp agreement rule) — https://lumencalculator.com/battery-degradation-calculator/
- [5] Vehiclers, "How to Check Tesla Battery Health" (method table; 295/326 = 90.5% example) — https://www.vehiclers.com/tesla/how-to-check-tesla-battery-health/
- [6] BatterMachine, "Methods for State of Health Estimation in Tesla Batteries" (coulomb counting, OCV, EKF/UKF, ML taxonomy) — https://www.battermachine.com/post/methods-for-state-of-health-estimation-in-tesla-batteries
- [7] Tesla Owner's Manual (Model 3), Battery Health / Health Test procedure — https://www.tesla.com/ownersmanual/model3/en_us/GUID-B9807218-7291-4F68-9AFF-7C525CF498F3.html
- [8] Not a Tesla App, "Tesla's Battery Health Test" (AC-only, up to 24 h) — https://www.notateslaapp.com/news/2049/teslas-battery-health-test-see-your-battery-health-in-app-or-in-service-mode
- [9] BatterMachine, "Tesla Battery Health Test: Procedure, Principles, and Real-World Results" (OCV two-point + ∫I) — https://www.battermachine.com/post/tesla-battery-health-test-procedure-principles-and-real-world-results
