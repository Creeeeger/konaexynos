# KonaExynos

## Supported Devices

- **Exynos 9820**
- **Exynos 9825**

---

## What is this?

A simple Android app that lets you **edit custom GPU frequency tables without recompiling the kernel**.

---

## How does it work?

1. **Unpacks** the DTB image from your device.
2. **Converts** the `.dtb` file to a readable `.dts` file.
3. **Lets you edit** the frequency table via the app UI.
4. **Re-packs** and **flashes** the modified image back to your device.

---

## How to Use

1. **Install** the app on an Exynos 9820/9825 device.
2. **Grant root (su) permissions.**
3. Press **Edit GPU Freq Table**.
4. **Select your chipset.**
5. **Edit the table** as needed.
6. Press **Save GPU Freq Table**.
7. Press **Repack and Flash Image**.
8. **Reboot** and test if it works.

---

## What kind of improvement can I expect?

- You can under-clock for battery savings, or add new frequency steps.
- Don’t expect miracles—hardware and firmware limits still apply.

---

## Prebuilt Binaries

- [`dtc`](https://github.com/xzr467706992/dtc-aosp/tree/standalone)
- [`extract_dtb`](https://github.com/PabloCastellano/extract-dtb)
- `repack_dtb`: Self-crafted binary for merging DTB parts and creating a bootable image.

---

# GPU DVFS Limitations on Exynos Devices

A Clearer Explanation and Background

This document explains why custom GPU frequency tables on Exynos
devices are limited by **predefined hardware constraints** --
specifically the **ECT (Embedded Configuration Table)** loaded by the
bootloader. It summarizes practical observations, kernel research, and
why true GPU overclocking is currently not feasible for the public.

------------------------------------------------------------------------

# Round 2:

# G3D PLL / DVFS tables

---

## 1) What was discovered

My initial thought was “wrong and correct at the same time”:

- The PLL table is **signed and not directly editable during early init**.
- However, during initialization it gets **loaded into DRAM**, which *is* accessible later.
- Since the kernel already has functions to **dump the clock tables** (enable with `debug_fs=y`), it’s possible to extract all clock levels and multipliers from the live system.

---

## 2) PLL table example (PLL_G3D)

Example dump:

```text
[PLL NAME] : PLL_G3D
[PLL TYPE] : 14160
[NUM OF FREQUENCY] : 13
    [FREQUENCY] : 754000000  [P] : 4  [M] : 116  [S] : 0  [K] : 0
    [FREQUENCY] : 702000000  [P] : 4  [M] : 108  [S] : 0  [K] : 0
    [FREQUENCY] : 676000000  [P] : 4  [M] : 104  [S] : 0  [K] : 0
    [FREQUENCY] : 650000000  [P] : 4  [M] : 100  [S] : 0  [K] : 0
    [FREQUENCY] : 598000000  [P] : 4  [M] : 184  [S] : 1  [K] : 0
    [FREQUENCY] : 572000000  [P] : 4  [M] : 176  [S] : 1  [K] : 0
    [FREQUENCY] : 432250000  [P] : 4  [M] : 133  [S] : 1  [K] : 0
    [FREQUENCY] : 377000000  [P] : 4  [M] : 116  [S] : 1  [K] : 0
    [FREQUENCY] : 325000000  [P] : 4  [M] : 100  [S] : 1  [K] : 0
    [FREQUENCY] : 260000000  [P] : 4  [M] : 160  [S] : 2  [K] : 0
    [FREQUENCY] : 199875000  [P] : 4  [M] : 123  [S] : 2  [K] : 0
    [FREQUENCY] : 156000000  [P] : 4  [M] : 96   [S] : 2  [K] : 0
    [FREQUENCY] : 99937000   [P] : 4  [M] : 123  [S] : 3  [K] : 0
```

### Interpreting the parameters

For `K=0` (integer-N), the typical relationship is:

- **VCO**: `Fvco = (Fref * M) / P`
- **Output**: `Fout = Fvco / (2^S)`

Where:

- `P` = pre-divider  
- `M` = feedback multiplier  
- `S` = post-divider exponent (divide by 2^S)  
- `K` = fractional part (unused here because `K=0`)


From the table values, the **reference clock** resolves cleanly to:

- **Fref ≈ 26 MHz**

(Any tiny mismatch is just rounding of the printed `FREQUENCY` values.)

---

## 3) Why there’s “another level” on the 9825

On the 9825, the ECT PLL table essentially matches the PLL table, but the **DVFS domain table** does **not** include the extra top level.

DVFS dump:

```text
[DOMAIN NAME] : dvfs_g3d
[BOOT LEVEL IDX] : NONE
[RESUME LEVEL IDX] : NONE
[MAX FREQ] : 0
[MIN FREQ] : 4294967295
[NUM OF SFR] : 1
    [SFR ADDRESS] : 1a240140
[NUM OF LEVEL] : 12
    [LEVEL] : 702000(X)
    [LEVEL] : 676000(X)
    [LEVEL] : 650000(X)
    [LEVEL] : 598000(X)
    [LEVEL] : 572000(X)
    [LEVEL] : 433000(X)
    [LEVEL] : 377000(X)
    [LEVEL] : 325000(X)
    [LEVEL] : 260000(X)
    [LEVEL] : 200000(X)
    [LEVEL] : 156000(X)
    [LEVEL] : 100000(X)
```

**Key observation:**  
The DVFS levels stop at **702 MHz**, even though the PLL table contains a **754 MHz** entry. So the “extra” PLL level exists in the PLL table, but the DVFS policy list doesn’t reference it.

---

## Appendix: quick PLL sanity check

Example row `650000000` with `P=4, M=100, S=0`:

- `Fout = 26 MHz * 100 / (4 * 1) = 650 MHz`

Example row `598000000` with `P=4, M=184, S=1`:

- `Fvco = 26 MHz * 184 / 4 = 1196 MHz`
- `Fout = 1196 / 2 = 598 MHz`

----
