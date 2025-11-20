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

## 1. What the Kernel Logs Tell Us

Through kernel logging, we can see that the GPU DVFS driver exposes a
**fixed, predetermined frequency/voltage table**.\
Only these frequencies are supported by the hardware at runtime:

    702000 kHz – 681250 uV  
    676000 kHz – 668750 uV  
    650000 kHz – 662500 uV  
    598000 kHz – 656250 uV  
    572000 kHz – 650000 uV  
    433000 kHz – 625000 uV  
    377000 kHz – 612500 uV  
    325000 kHz – 587500 uV  
    260000 kHz – 568750 uV  
    200000 kHz – 568750 uV  
    156000 kHz – 543750 uV  
    100000 kHz – 537500 uV

These values are **not calculated by the kernel**.\
They are **injected at boot** from the device's **ECT**, a configuration
blob loaded by the **bootloader** before the kernel even starts - these values are 
then just throws around by vclk, acme, lut, cal-if, pq-mos, and how they are all called.
Yet in the end they all come from the ect.

------------------------------------------------------------------------

## 2. Why Custom Frequency Tables Don't Work

You can patch your kernel to *display* higher custom frequencies in the
UI.\
But the ACPM (the microcontroller that actually manages clocks and
voltages) ignores them.

### Why?

Because the ACPM always maps your requested frequency to the **closest
valid index in the original ECT table**.

Example:

    Original:
    702 MHz → index 1
    676 MHz → index 2
    650 MHz → index 3

    Custom user-edited table:
    1000 MHz → index 1 (UI)
    845 MHz  → index 1 (UI)
    702 MHz  → index 1
    676 MHz  → index 2
    650 MHz  → index 3

The UI might show: \> "GPU is running at 1.0 GHz!"

But the ACPM internally sees: \> "Set index 1 → 702 MHz"

So **actual hardware frequency never exceeds 702 MHz**---no matter what
custom table you create.

------------------------------------------------------------------------

## 3. Why Not Just Modify the ECT?

Because the ECT is:

-   Loaded **only** by the **bootloader**
-   Stored in a region you **cannot edit from the kernel**
-   Not writable from userspace or kernelspace
-   Validated and protected by Samsung's hardware chain-of-trust

Editing the ECT would require:

1.  Exploiting the **bootrom**  🏋🏻‍♀️🏋🏻‍♀️🏋🏻‍♀️🏋🏻‍♀️🏋🏻‍♀️
2.  Achieving arbitrary write access  🏋🏻‍♀️🏋🏻‍♀️
3.  Repackaging and injecting a modified ECT during boot 🏋🏻‍♀️

This is: - **Not legally acceptable** - **Not (quickly) realistically
achievable** - **Not something the kernel can override**

Thus the hardware-enforced GPU frequency limits cannot be changed
publicly.

------------------------------------------------------------------------

## 4. So Is GPU Tweaking Completely Useless?

No. You can still:

-   **Undervolt** to reduce heat and improve efficiency
-   **Underclock** for battery gains
-   **Force higher minimum frequencies** to stabilize performance in
    games
-   **Fine‑tune DVFS behavior** for smoother workloads

What you *cannot* do: - Truly overclock beyond the **predefined ECT
maximum**
- Add new functional frequency steps
- Exceed the limits loaded by the bootloader

------------------------------------------------------------------------

## 5. Conclusion

True GPU overclocking on these Exynos devices is currently **impossible
for the public** because:

-   The real DVFS table is locked in the **ECT**, not the kernel
-   The ECT is loaded by the **bootloader** and never touched again
-   The ACPM enforces these original frequency indices
-   Kernel-side patches only change the **UI**, not the hardware
    behavior

The GPU will always fall back to the **highest valid ECT-defined
frequency**---702 MHz in this example.

------------------------------------------------------------------------

If future bootloader or bootrom exploits appear, modifying the ECT might
become possible.
Until then, **GPU frequency limits are effectively hardware‑locked**.



---
- [x] With this commit, the project has come to an end

