# KonaExynos

## Supported Devices

- **Exynos 9820**
- **Exynos 9825**
- **Exynos 9810**

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

## PLL table example (PLL_G3D)

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


## Exynos9820 Kernel
- After extensive testing and trying i came to the result. In the https://github.com/Creeeeger/9820_kernel kernel are in the forOC folder the 2 files which need to be modded and changed in order to increase clocks
