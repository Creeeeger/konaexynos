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
- press repack and flash image ( if u want to get safe press backup old image and follow the screen instructions)
- reboot and see if it works


### How much improvement can I get?

- nobody knows it (yet)
- I will test it on a s10 5g

### Prebuilt binaries

- [magiskboot](https://github.com/topjohnwu/Magisk)
- [dtc](https://github.com/xzr467706992/dtc-aosp/tree/standalone)
- [extract_dtb](https://github.com/PabloCastellano/extract-dtb)
- [repack_dtb](Self crafted binary for attaching both dtb parts together and converting them to an image)

### What works (Actually everything)
- unpacking 
- backup
- repack 
- flash 
- edit table
- saving


### What doesn't
- saving the table in the correct format 
- modifiying other parts of the dts file according to the table changes 

### Issues 
- If there are any issues or whatever open an issue 
- If you want to contribute open pull request 
- for new device open issue with device, chipset name and android version 
