# KonaExynos

## Supported Devices

- **Exynos 9820**
- **Exynos 9825**
- For new devices: _study the code yourself and add support_.

---

## What is this?

A simple Android app that lets you **edit custom GPU frequency tables without recompiling the kernel
**.

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

## Limitations & Notes

- **Some Samsung kernels may block this mod**.
- Kernel logs reveal a **predefined table**—frequencies that actually work:

```
6,908,984754,-;dvfs_type : dvfs_g3d - id : a
6,909,984761,-;  num_of_lv      : 12
6,910,984769,-;  num_of_members : 1
6,911,984778,-;  DVFS CMU addr:0x1a240140
6,912,984787,-;  lv : [ 702000], volt = 681250 uV 
6,913,984797,-;  lv : [ 676000], volt = 668750 uV
6,914,984807,-;  lv : [ 650000], volt = 662500 uV
6,915,984816,-;  lv : [ 598000], volt = 656250 uV
6,916,984826,-;  lv : [ 572000], volt = 650000 uV
6,917,984835,-;  lv : [ 433000], volt = 625000 uV
6,918,984845,-;  lv : [ 377000], volt = 612500 uV
6,919,984854,-;  lv : [ 325000], volt = 587500 uV
6,920,984864,-;  lv : [ 260000], volt = 568750 uV 
6,921,984874,-;  lv : [ 200000], volt = 568750 uV
6,922,984883,-;  lv : [ 156000], volt = 543750 uV
6,923,984892,-;  lv : [ 100000], volt = 537500 uV
```

- **Only these frequencies are supported** (enforced in SRAM).
- If you create a custom table, the voltages are still hard-linked to these frequency steps.
- _Tricking_ this system may require advanced kernel
  work—see [this commit](https://github.com/Creeeeger/Galaxy_S10_5G_Kernel/commit/da293bfb95effcfcba1900a4a3fb15a95b471ef9#diff-830b66ed3916a0a50cb5b270b4a2b5d1ace91f93ccac5534b69c041558aba923).

---

## Issues & Contributions

- Found a bug? **[Open an issue](../../issues)**
- Want to help? **[Send a pull request](../../pulls)**
- Contributions welcome!

---

## TODO

- [ ] Automatically sort frequencies when adding new ones.

---

_You’re welcome to fork, hack, and contribute!_


- for 9830 (990) the table is stored in the dtb image
- for 9825 (of F62) its stored in the dtbo image
