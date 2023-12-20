# KonaExynos

### Support list
* Exynos 9820
* Exynos 9825
* For a new device open an issue and i will add it as soon as possible

### What is this?

- A simple app that can custom GPU frequency tables without recompiling the kernel

### How it works?

- By unpacking the DTB image, converting the dtb file to a dts file and editing the table, and finally repacking and flashing it.

### How to use?

- Install app on a exynos 9820/25 platform
- give su rights 
- press edit gpu freq table 
- select your chipset 
- edit the table
- press save gpu freq table
- press repack and flash image (if u want to get safe press backup old image and follow the screen instructions)
- reboot and see if it works

### How much improvement can I get?

- nobody knows it (yet)
- I will test it on a s10 5g

### Prebuilt binaries

- [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)
- [extract_dtb](https://github.com/PabloCastellano/extract-dtb)
- repack_dtb - Self crafted binary for attaching both dtb parts together and converting them to an image

### What doesn't
- idk smasnungs kernel which prevents it from working (mostly)
- in the logs I found a pre defined table which freqs possible are
- i use a stock kernel but the overclock frequencies which i could achieve with a custom kernel were list 1:1 in the logs:
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
- basically these freqs work and no other ones
- these freqs are in the sram, so what i do is to create a custom kernel with a custom table, what i did and, but the voltages are linked to the frequencies even if you rewrite them
- so we need to trick out this mechanism --> [The commit](https://github.com/Creeeeger/Galaxy_S10_5G_Kernel/commit/da293bfb95effcfcba1900a4a3fb15a95b471ef9#diff-830b66ed3916a0a50cb5b270b4a2b5d1ace91f93ccac5534b69c041558aba923)
- 
### Issues 
- If there are any issues or whatever open an issue 
- If you want to contribute open pull request 
- for new device open issue with device, chipset name and android version 
