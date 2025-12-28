package xzr.konabess;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import xzr.konabess.utils.AssetsUtil;

/**
 * Coordinates privileged image extraction, device-tree conversion, target detection, repacking,
 * and flashing.
 *
 * <p>The public static fields describe the active editing session and are populated as the workflow
 * advances.
 */
public class KonaBessCore {
    private static final String[] fileList = {
            "dtc",
            "extract_dtb",
            "repack_dtb",
            "libz.so",
            "libz.so.1",
            "libz.so.1.3.1",
            "libzstd.so",
            "libzstd.so.1",
            "libzstd.so.1.5.6",
            "libandroid-support.so"
    };

    /** Absolute path of the decompiled DTS currently open in the editor. */
    public static String dts_path;

    /** Filename of the DTB selected from the extraction output. */
    public static String fileNameDtbFile = "";

    /** Block-device path copied at the start of the current session. */
    public static String devPath;
    /** Local image filename corresponding to {@link #devPath}. */
    public static String fileNameImg;

    /** Chip targets detected in the decompiled DTS. */
    public static ArrayList<dtb> dtbs;

    /**
     * Recursively deletes the app's internal files directory before a new editing session.
     *
     * @param context context that provides the internal files directory
     * @throws IOException if any entry cannot be deleted
     */
    public static void cleanEnv(Context context) throws IOException {
        File dir = context.getFilesDir();

        deleteRecursive(dir);
    }

    /**
     * Deletes a filesystem tree depth-first.
     *
     * @param file file or directory to remove
     * @throws IOException if the final deletion of any entry fails
     */
    private static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();

            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }

        if (!file.delete()) {
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }

    /**
     * Copies the bundled native tools and libraries into internal storage and makes them accessible
     * to the root shell.
     *
     * @param context context used to access assets and internal storage
     * @throws IOException if an asset cannot be copied, permission changes fail, or a tool remains
     *     non-executable
     */
    public static void setupEnv(Context context) throws IOException {
        for (String s : fileList) {
            File destination = new File(context.getFilesDir(), s);

            AssetsUtil.exportFiles(context, s, destination.getAbsolutePath());

            if (!destination.setExecutable(true, false) ||
                    !destination.setReadable(true, false) ||
                    !destination.setWritable(true, false)) {
                throw new IOException("Failed to set permissions for: " + destination.getAbsolutePath());
            }

            if (!destination.canExecute()) {
                throw new IOException("File is not executable: " + destination.getAbsolutePath());
            }
        }
    }

    /**
     * Requests a device reboot through {@code su -c "svc power reboot"}.
     *
     * @throws IOException if the command cannot start, exits unsuccessfully, or is interrupted
     */
    public static void reboot() throws IOException {
        Process process = new ProcessBuilder("su", "-c", "svc power reboot")
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to reboot. Exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Reboot process interrupted.", e);
        } finally {
            process.destroy();
        }
    }

    /**
     * Copies the active boot, DTB, or DTBO partition into app storage and shared storage.
     *
     * <p>Exynos 9810 uses the boot partition. Other recognized devices prefer {@code dtb} and fall
     * back to {@code dtbo}. The selected block path and local filename are stored in
     * {@link #devPath} and {@link #fileNameImg}.
     *
     * @param context context used to locate internal storage
     * @throws IOException if the device cannot be identified, no source partition exists, the root
     *     copy fails, or the copied image is empty
     */
    public static void getDtImage(Context context) throws IOException {
        String internalBase = context.getFilesDir().getAbsolutePath();
        String externalBase = "/storage/emulated/0";

        File dtb = new File("/dev/block/by-name/dtb");
        File dtbo = new File("/dev/block/by-name/dtbo");
        File boot = new File("/dev/block/by-name/boot");

        // Exynos 9810 embeds its device tree in boot; the other supported chips use dtb/dtbo.
        if (isExynos9810()) {
            if (boot.exists()) {
                devPath = boot.getAbsolutePath();
                fileNameImg = "boot.img";
            } else {
                throw new IOException("Neither /dev/block/by-name/boot exists");
            }
        } else {
            if (dtb.exists()) {
                devPath = dtb.getAbsolutePath();
                fileNameImg = "dtb.img";
            } else if (dtbo.exists()) {
                devPath = dtbo.getAbsolutePath();
                fileNameImg = "dtbo.img";
            } else {
                throw new IOException("Neither /dev/block/by-name/dtb nor /dev/block/by-name/dtbo exists");
            }
        }

        String internalPath = internalBase + "/" + fileNameImg;
        String externalPath = externalBase + "/" + fileNameImg;

        Process process = null;
        try {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                writer.write("dd if=" + devPath + " of=" + internalPath + "\n");
                writer.write("chmod 777 " + internalPath + "\n");
                writer.write("cp -f " + internalPath + " " + externalPath + "\n");
                writer.write("exit\n");
                writer.flush();

                // Drain stdout before waitFor() so a full pipe cannot block the root shell.
                while (reader.readLine() != null) {  }
            }

            if (process.waitFor() != 0) {
                throw new IOException("su/dd failed with exit code " + process.exitValue());
            }

            File target = new File(internalPath);
            if (!target.exists() || !target.canRead() || target.length() <= 0L) {
                if (target.exists()) {
                    target.delete();
                }
                throw new IOException("Created " + fileNameImg + " is empty or unreadable");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to create " + fileNameImg, e);
        } finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * Detects Exynos 9810 markers in CPU information or the kernel command line.
     *
     * @return {@code true} when a known marker is present
     * @throws IOException if either procfs file cannot be read
     */
    private static boolean isExynos9810() throws IOException {
        if (fileContainsAny("/proc/cpuinfo", "exynos9810", "exynos 9810", "samsungexynos9810")) {
            return true;
        }
        return fileContainsAny("/proc/cmdline", "exynos9810");
    }

    /**
     * Performs a case-insensitive substring search over a text file.
     *
     * @param path file to scan
     * @param needles candidate substrings
     * @return {@code true} after the first match
     * @throws IOException if the file cannot be read
     */
    private static boolean fileContainsAny(String path, String... needles) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String haystack = line.toLowerCase();
                for (String needle : needles) {
                    if (haystack.contains(needle.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Extracts a DTB from the copied image and decompiles it to {@code 0.dts}.
     *
     * @param context context used to locate tools and working files
     * @throws IOException if extraction or decompilation fails
     */
    public static void dtbImage2dts(Context context) throws IOException {
        fileNameDtbFile = unpackBootImage(context);

        dtb2dts(context, fileNameDtbFile);
    }

    /**
     * Runs {@code extract_dtb} against {@link #fileNameImg} and selects an extracted DTB.
     *
     * <p>Extraction results are moved into the app files directory. Candidates named
     * {@code 01_dtbdump*.dtb} are sorted by filename, and the first candidate is returned.
     *
     * @param context context used to locate the working directory
     * @return filename of the selected extracted DTB
     * @throws IOException if the extractor is unavailable, the process fails, or no candidate is
     *     produced
     */
    public static String unpackBootImage(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();

        File extractBinary = new File(filesDir, "extract_dtb");

        if (!extractBinary.exists() || !extractBinary.canExecute()) {
            throw new IOException("extract_dtb binary is missing or not executable");
        }

        // Build a shell command to:
        // 1. cd into filesDir
        // 2. export LD_LIBRARY_PATH to include filesDir for dependent shared libs
        // 3. run extract_dtb on dtb.img or dtbo.img
        // 4. create dtb directory if not present
        // 5. move extracted blobs into filesDir
        // 6. clean up the dtb directory
        String shellCmd = String.format(
                "cd %s && " +
                        "export LD_LIBRARY_PATH=%s:$LD_LIBRARY_PATH && " +
                        "./extract_dtb %s && " +
                        "[ -d dtb ] || mkdir -p dtb && " +
                        "mv dtb/* . || echo 'Move failed' && " +
                        "rm -rf dtb",
                filesDir, filesDir, fileNameImg
        );
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", shellCmd)
                .redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder log = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Process failed with exit code " + exitCode + ": " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }

        File[] candidates = new File(filesDir).listFiles((dir, name) -> name.startsWith("01_dtbdump") && name.endsWith(".dtb"));

        if (candidates == null || candidates.length == 0) {
            throw new IOException("No DTB files extracted. Logs:\n" + log);
        }

        Arrays.sort(candidates, Comparator.comparing(File::getName));
        File first = candidates[0];

        return first.getName();
    }

    /**
     * Uses {@code dtc} to decompile one extracted DTB into {@code 0.dts}.
     *
     * <p>The source DTB is removed after successful conversion.
     *
     * @param context context used to locate the working directory
     * @param fileName extracted DTB filename
     * @throws IOException if inputs are missing, {@code dtc} fails, or the DTS is not readable
     */
    private static void dtb2dts(Context context, String fileName) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();

        File dtcBinary = new File(filesDir, "dtc");
        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("dtc binary is missing or not executable: " + dtcBinary.getAbsolutePath());
        }

        File inputFile = new File(filesDir, fileName);
        if (!inputFile.exists()) {
            throw new IOException("Input DTB file does not exist: " + inputFile.getAbsolutePath());
        }

        File outputFile = new File(filesDir, "0.dts");

        // Build the shell command to:
        // 1. cd into filesDir
        // 2. run dtc converting DTB->DTS
        // 3. remove the original DTB to save space
        // 4. set broad permissions on the generated DTS
        String command = String.format(
                "cd %s && ./dtc -I dtb -O dts %s -o %s && rm -f %s && chmod 777 %s",
                filesDir,
                inputFile.getName(),
                outputFile.getName(),
                inputFile.getName(),
                outputFile.getName()
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder log = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("DTB to DTS conversion failed with exit code " + exitCode + ": " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }

        if (!outputFile.exists() || !outputFile.canRead()) {
            throw new IOException("DTS conversion failed. Log: " + log);
        }
    }

    /**
     * Searches {@code 0.dts} for the first recognized Exynos identifier.
     *
     * <p>The detected target is stored as the only entry in {@link #dtbs}.
     *
     * @param context context used to locate {@code 0.dts}
     * @throws IOException if a search command fails or no supported identifier is detected
     */
    public static void checkDevice(Context context) throws IOException {
        dtbs = new ArrayList<>();

        String[] chipTypes = {"exynos9820", "exynos9825", "exynos990", "exynos9810"};
        ChipInfo.type[] chipInfoTypes = {ChipInfo.type.exynos9820, ChipInfo.type.exynos9825, ChipInfo.type.exynos990, ChipInfo.type.exynos9810};

        for (int i = 0; i < chipTypes.length; i++) {
            if (checkChip(context, chipTypes[i])) {
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = chipInfoTypes[i];
                dtbs.add(dtb);
                break;
            }
        }

        if (dtbs.isEmpty()) {
            throw new IOException("No supported chip detected.");
        }
    }

    /**
     * Runs {@code grep} for a chip identifier in {@code 0.dts}.
     *
     * @param context context used to locate {@code 0.dts}
     * @param chip identifier to search for
     * @return {@code true} when grep writes a matching line
     * @throws IOException when the root command cannot start, is interrupted, or exits nonzero
     *     without output
     */
    private static boolean checkChip(Context context, String chip) throws IOException {
        String command = String.format(
                "grep '%s' %s/0.dts",
                chip, context.getFilesDir().getAbsolutePath()
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        boolean result;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            result = reader.readLine() != null;
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 && !result) {
                throw new IOException("Command failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process interrupted", e);
        } finally {
            process.destroy();
        }

        return result;
    }

    /**
     * Reads {@code androidboot.dtbo_idx} from the kernel command line.
     *
     * @return parsed DTBO index, or {@code -1} when the argument is absent
     * @throws IOException if the command line cannot be read or the value is not an integer
     */
    public static int getDtbIndex() throws IOException {
        for (String line : getCmdline()) {
            if (line.contains("androidboot.dtbo_idx=")) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    try {
                        return Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid dtbo_idx value: " + parts[1], e);
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Reads {@code /proc/cmdline} through the root shell and splits it on spaces.
     *
     * @return kernel command-line arguments
     * @throws IOException if the command fails or is interrupted
     */
    private static List<String> getCmdline() throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", "cat /proc/cmdline").redirectErrorStream(true);
        Process process = processBuilder.start();

        List<String> cmdlineArgs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                cmdlineArgs.addAll(Arrays.asList(line.split(" ")));
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to read /proc/cmdline with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }

        return cmdlineArgs;
    }

    /**
     * Flashes {@code dtb_new.img} to the block partition represented by {@link #fileNameImg}.
     *
     * <p>The destination name is obtained by removing the {@code .img} suffix, producing paths such
     * as {@code /dev/block/by-name/dtb}, {@code dtbo}, or {@code boot}.
     *
     * @param context context used to locate the generated image
     * @throws IOException if the image is missing or the root {@code dd} command fails
     */
    public static void writeDtbImage(Context context) throws IOException {
        String inputPath = new File(context.getFilesDir(), "dtb_new.img").getAbsolutePath();

        String partitionName = fileNameImg.replaceFirst("\\.img$", "");
        String outputPath = "/dev/block/by-name/" + partitionName;

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input DTB image not found: " + inputPath);
        }

        String command = String.format("dd if=%s of=%s", inputPath, outputPath);
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to write DTB/dtbo image. Exit code: " + exitCode + "\nLogs: " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }
    }

    /**
     * Activates a detected target for the GPU editor.
     *
     * <p>This sets the editable DTS path to {@code 0.dts} and updates
     * {@link ChipInfo#which}.
     *
     * @param dtb detected target
     * @param activity activity used to locate app storage
     */
    public static void chooseTarget(dtb dtb, AppCompatActivity activity) {
        dts_path = new File(activity.getFilesDir(), "0.dts").getAbsolutePath();

        ChipInfo.which = dtb.type;
    }

    /**
     * Compiles the edited DTS and repacks the selected DTB into {@code dtb_new.img}.
     *
     * @param context context used to locate tools and working files
     * @throws IOException if compilation or repacking fails
     */
    public static void dts2bootImage(Context context) throws IOException {
        dts2dtb(context);

        dtb2bootImage(context);
    }

    /**
     * Compiles {@code 0.dts} back to the selected extracted DTB filename.
     *
     * @param context context used to locate the compiler and working files
     * @throws IOException if inputs are missing, {@code dtc} fails, or no output is produced
     */
    private static void dts2dtb(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File dtsFile = new File(filesDir, "0.dts");
        File outputFile = new File(filesDir, fileNameDtbFile);
        File dtcBinary = new File(filesDir, "dtc");

        if (!dtsFile.exists()) {
            throw new IOException("Input DTS file is missing: " + dtsFile.getAbsolutePath());
        }

        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("DTC binary is missing or not executable: " + dtcBinary.getAbsolutePath());
        }

        String command = String.format(
                "cd %s && ./dtc -I dts -O dtb 0.dts -o %s",
                filesDir, fileNameDtbFile
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Command execution failed with exit code " + exitCode + ": " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }

        if (!outputFile.exists()) {
            throw new IOException("Output DTB file not created. Logs: " + log);
        }
    }

    /**
     * Runs {@code repack_dtb} with {@code 00_kernel} and the compiled DTB.
     *
     * @param context context used to locate the repacker and working files
     * @throws IOException if required files are missing, the process fails, or
     *     {@code dtb_new.img} is not produced
     */
    private static void dtb2bootImage(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File kernelFile = new File(filesDir, "00_kernel");
        File dtbFile = new File(filesDir, fileNameDtbFile);
        File outputFile = new File(filesDir, "dtb_new.img");
        File repackDtbBinary = new File(filesDir, "repack_dtb");

        if (!kernelFile.exists()) {
            throw new IOException("Kernel file missing: " + kernelFile.getAbsolutePath());
        }

        if (!dtbFile.exists()) {
            throw new IOException("DTB file missing: " + dtbFile.getAbsolutePath());
        }

        if (!repackDtbBinary.exists() || !repackDtbBinary.canExecute()) {
            throw new IOException("Repack binary missing or not executable: " + repackDtbBinary.getAbsolutePath());
        }

        String command = String.format(
                "cd %s && export LD_LIBRARY_PATH=%s:$LD_LIBRARY_PATH && ./repack_dtb 00_kernel %s dtb_new.img",
                filesDir, filesDir, fileNameDtbFile
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Command execution failed with exit code " + exitCode + ": " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }

        if (!outputFile.exists()) {
            throw new IOException("Output file not created. Logs: " + log);
        }
    }

    /** Detected device-tree target and its corresponding chip model. */
    static class dtb {
        /** Detection-order index. */
        int id;
        /** Chip model associated with the target. */
        ChipInfo.type type;
    }
}
