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

public class KonaBessCore {
    // List of asset filenames to extract and set up in the app's file directory
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

    // Path to the generated DTS file (populated elsewhere)
    public static String dts_path;

    public static String fileNameDtbFile = "";

    public static String devPath;
    public static String fileNameImg;
    // List of detected DTB (Device Tree Blob) objects to choose from
    public static ArrayList<dtb> dtbs;

    /**
     * Cleans the environment by deleting all files in the app's internal storage directory.
     *
     * @param context Application context to locate the files directory.
     * @throws IOException If any file or directory cannot be deleted.
     */
    public static void cleanEnv(Context context) throws IOException {
        // Get the app's internal files directory root
        File dir = context.getFilesDir();
        // Recursively delete everything under the directory
        deleteRecursive(dir);
    }

    /**
     * Recursively deletes the given file or directory.
     *
     * @param file File or directory to delete.
     * @throws IOException If deletion of any file/directory fails.
     */
    private static void deleteRecursive(File file) throws IOException {
        // If this is a directory, process all its children first
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            // Protect against null results
            if (files != null) {
                for (File child : files) {
                    // Recursively delete each child
                    deleteRecursive(child);
                }
            }
        }
        // Attempt to delete the file or now-empty directory
        if (!file.delete()) {
            // Throw an exception if deletion fails
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }

    /**
     * Sets up the environment by extracting assets and configuring permissions.
     *
     * @param context Application context to locate assets and files directory.
     * @throws IOException If asset export or permission setting fails.
     */
    public static void setupEnv(Context context) throws IOException {
        // Iterate over each filename in the asset list
        for (String s : fileList) {
            // Define the destination path within the app's files directory
            File destination = new File(context.getFilesDir(), s);
            // Export the asset from the APK to the destination path
            AssetsUtil.exportFiles(context, s, destination.getAbsolutePath());

            // Attempt to set executable, readable, and writable permissions for all users
            if (!destination.setExecutable(true, false) ||  // executable by all
                    !destination.setReadable(true, false) ||  // readable by all
                    !destination.setWritable(true, false)) {     // writable by all
                // If any permission call fails, throw an exception
                throw new IOException("Failed to set permissions for: " + destination.getAbsolutePath());
            }

            // Final validation: ensure the file is executable
            if (!destination.canExecute()) {
                throw new IOException("File is not executable: " + destination.getAbsolutePath());
            }
        }
    }

    /**
     * Reboots the device by invoking a shell command with root privileges.
     *
     * @throws IOException If the reboot command fails or is interrupted.
     */
    public static void reboot() throws IOException {
        // Build and start the process: su -c "svc power reboot"
        Process process = new ProcessBuilder("su", "-c", "svc power reboot")
                .redirectErrorStream(true)
                .start();

        // Consume and discard any output from the reboot command
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // No-op: just drain the stream to prevent blocking
            }
        }

        // Wait for the process to complete and check its exit code
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // Non-zero exit indicates a failure to reboot
                throw new IOException("Failed to reboot. Exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            // Restore interrupt status and wrap in an IOException
            Thread.currentThread().interrupt();
            throw new IOException("Reboot process interrupted.", e);
        } finally {
            // Ensure process resources are released
            process.destroy();
        }
    }

    /**
     * Extracts the DTB (Device Tree Blob) from the system partition and saves it as dtb.img.
     *
     * @param context Application context to locate file directories.
     * @throws IOException If any shell command fails or the output file is invalid.
     */
    public static void getDtImage(Context context) throws IOException {
        String internalBase = context.getFilesDir().getAbsolutePath();
        String externalBase = "/storage/emulated/0";

        File dtb = new File("/dev/block/by-name/dtb");
        File dtbo = new File("/dev/block/by-name/dtbo");
        File boot = new File("/dev/block/by-name/boot");

        // Selects device path and image name based on chip
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

                // Drain output so the process doesn't block
                while (reader.readLine() != null) { /* drain */ }
            }

            if (process.waitFor() != 0) {
                throw new IOException("su/dd failed with exit code " + process.exitValue());
            }

            File target = new File(internalPath);
            if (!target.exists() || !target.canRead() || target.length() <= 0L) {
                if (target.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                }
                throw new IOException("Created " + fileNameImg + " is empty or unreadable");
            }
            // success
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to create " + fileNameImg, e);
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static boolean isExynos9810() throws IOException {
        if (fileContainsAny("/proc/cpuinfo", "exynos9810", "exynos 9810", "samsungexynos9810")) {
            return true;
        }
        return fileContainsAny("/proc/cmdline", "exynos9810");
    }

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
     * Converts a DTB image to a DTS source file by unpacking the boot image then running dtb2dts.
     *
     * @param context Application context for file operations.
     * @throws IOException If any step in unpacking or conversion fails.
     */
    public static void dtbImage2dts(Context context) throws IOException {
        // Unpack the boot image to extract DTB(s)
        fileNameDtbFile = unpackBootImage(context);
        // Convert extracted DTB(s) to DTS format
        dtb2dts(context, fileNameDtbFile);
    }

    /**
     * Unpacks the boot image to extract DTB files into the app's files directory.
     *
     * @param context Application context to locate files directory and execute binaries.
     * @throws IOException If the extract_dtb binary is missing, not executable, or the unpack process fails.
     */
    public static String unpackBootImage(Context context) throws IOException {
        // Get the absolute path to the app's internal files directory
        String filesDir = context.getFilesDir().getAbsolutePath();
        // Reference to the extract_dtb binary placed in filesDir by setupEnv()
        File extractBinary = new File(filesDir, "extract_dtb");

        // Ensure the extract_dtb binary exists and is executable before running
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

        // Start the root shell process for extraction
        Process process = processBuilder.start();
        StringBuilder log = new StringBuilder();

        // Capture all stdout/stderr output from the process to log for error reporting
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Wait for the process to finish and check its exit status
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Process failed with exit code " + exitCode + ": " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            // Ensure process resources are released
            process.destroy();
        }

        // Pick first file "01_dtbdump*.dtb"
        File[] candidates = new File(filesDir).listFiles((dir, name) -> name.startsWith("01_dtbdump") && name.endsWith(".dtb"));

        if (candidates == null || candidates.length == 0) {
            throw new IOException("No DTB files extracted. Logs:\n" + log);
        }

        Arrays.sort(candidates, Comparator.comparing(File::getName));
        File first = candidates[0];

        return first.getName();
    }

    /**
     * Converts an extracted DTB binary into a DTS source file.
     *
     * @param context Application context for file operations.
     * @throws IOException If dtc binary is missing/not executable, conversion fails, or output is invalid.
     */
    private static void dtb2dts(Context context, String fileName) throws IOException {
        // Base directory where dtc and DTB files reside
        String filesDir = context.getFilesDir().getAbsolutePath();

        // Ensure the dtc binary is present and has execute permission
        File dtcBinary = new File(filesDir, "dtc");
        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("dtc binary is missing or not executable: " + dtcBinary.getAbsolutePath());
        }

        // Input DTB file produced by unpackBootImage
        File inputFile = new File(filesDir, fileName);
        if (!inputFile.exists()) {
            throw new IOException("Input DTB file does not exist: " + inputFile.getAbsolutePath());
        }

        // Output DTS file to generate
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

        // Start the conversion process
        Process process = processBuilder.start();
        StringBuilder log = new StringBuilder();

        // Capture conversion logs for debugging
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Await process completion and verify success
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

        // Confirm the DTS file exists and is readable
        if (!outputFile.exists() || !outputFile.canRead()) {
            throw new IOException("DTS conversion failed. Log: " + log);
        }
    }

    /**
     * Detects the device's chip type by checking for known chip strings in the DTS file.
     * Populates the dtbs list with supported dtb entries.
     *
     * @param context Application context for file access.
     * @throws IOException If no supported chip is found.
     */
    public static void checkDevice(Context context) throws IOException {
        // Initialize the list to collect matching DTB entries
        dtbs = new ArrayList<>();

        // Supported chip identifier strings and corresponding enum types
        String[] chipTypes = {"exynos9820", "exynos9825", "exynos990", "exynos9810"};
        ChipInfo.type[] chipInfoTypes = {ChipInfo.type.exynos9820, ChipInfo.type.exynos9825, ChipInfo.type.exynos990, ChipInfo.type.exynos9810};

        // Iterate through supported types and test each one
        for (int i = 0; i < chipTypes.length; i++) {
            if (checkChip(context, chipTypes[i])) {
                // If found, create a dtb object with ID and type, then add to list
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = chipInfoTypes[i];
                dtbs.add(dtb);
                break; // Stop further checks once a match is detected
            }
        }

        // If no chip matched, throw an error indicating unsupported device
        if (dtbs.isEmpty()) {
            throw new IOException("No supported chip detected.");
        }
    }

    /**
     * Executes a grep command on the DTS file to check for a specific chip identifier.
     *
     * @param context Application context for file paths.
     * @param chip    The chip string to search for in the DTS content.
     * @return true if the chip string is present; false otherwise.
     * @throws IOException If the grep command fails unexpectedly.
     */
    private static boolean checkChip(Context context, String chip) throws IOException {
        // Format the shell command to grep for the chip string in 0.dts
        String command = String.format(
                "grep '%s' %s/0.dts",
                chip, context.getFilesDir().getAbsolutePath()
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        boolean result;
        // Read the first line of output; presence indicates a match
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            result = reader.readLine() != null;
        }

        // Wait for process termination and ensure no unexpected errors occurred
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 && !result) {
                throw new IOException("Command failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process interrupted", e);
        } finally {
            // Always destroy the process to free resources
            process.destroy();
        }

        return result;
    }

    /**
     * Retrieves the DTB index from the kernel command line parameters.
     *
     * @return The parsed integer value of androidboot.dtbo_idx, or -1 if not present.
     * @throws IOException If the dtbo_idx value is malformed.
     */
    public static int getDtbIndex() throws IOException {
        // Iterate through each parameter token from /proc/cmdline
        for (String line : getCmdline()) {
            // Look for the dtbo_idx prefix in the token
            if (line.contains("androidboot.dtbo_idx=")) {
                // Split into key and value
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    try {
                        // Parse and return the integer value after trimming whitespace
                        return Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        // Wrap parsing errors in an IOException with context
                        throw new IOException("Invalid dtbo_idx value: " + parts[1], e);
                    }
                }
            }
        }
        // Return -1 to indicate that the parameter was not found
        return -1;
    }

    /**
     * Reads and splits the kernel command line from /proc/cmdline.
     *
     * @return A list of individual command-line tokens.
     * @throws IOException If reading /proc/cmdline fails.
     */
    private static List<String> getCmdline() throws IOException {
        // Prepare the shell command to read /proc/cmdline as root
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", "cat /proc/cmdline").redirectErrorStream(true);
        Process process = processBuilder.start();

        // Collect tokens from the output
        List<String> cmdlineArgs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            // Split the single line into separate arguments by spaces
            while ((line = reader.readLine()) != null) {
                cmdlineArgs.addAll(Arrays.asList(line.split(" ")));
            }
        }

        // Wait for the process to complete and check its exit status
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to read /proc/cmdline with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy(); // Release resources
        }

        return cmdlineArgs;
    }

    /**
     * Writes a new DTB image to the system partition.
     *
     * @param context Application context for file resolution.
     * @throws IOException If the input file is missing or write operation fails.
     */
    public static void writeDtbImage(Context context) throws IOException {
        // Define the source path in internal storage and the target block device path
        String inputPath = new File(context.getFilesDir(), "dtb_new.img").getAbsolutePath();

        // Strip ".img" from fileNameImg to get block device name (dtb / dtbo)
        String partitionName = fileNameImg.replaceFirst("\\.img$", "");
        String outputPath = "/dev/block/by-name/" + partitionName;

        // Ensure the input DTB image exists before attempting write
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input DTB image not found: " + inputPath);
        }

        // Build and execute the dd command as root
        String command = String.format("dd if=%s of=%s", inputPath, outputPath);
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture any output or error messages
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Wait for completion and validate success
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
     * Sets the selected DTB as the target and updates the global state.
     *
     * @param dtb      The chosen DTB object.
     * @param activity The calling activity for file path resolution.
     */
    public static void chooseTarget(dtb dtb, AppCompatActivity activity) {
        // Store the absolute path to the DTS file for future operations
        dts_path = new File(activity.getFilesDir(), "0.dts").getAbsolutePath();
        // Update the global chip type to match the selected DTB
        ChipInfo.which = dtb.type;
    }

    /**
     * Converts DTS back to DTB and repacks into a new boot image.
     *
     * @param context Application context for file operations.
     * @throws IOException If any conversion or repack step fails.
     */
    public static void dts2bootImage(Context context) throws IOException {
        // First convert DTS source to DTB binary
        dts2dtb(context);
        // Then repack that DTB into a boot image file
        dtb2bootImage(context);
    }

    /**
     * Converts the existing DTS file into a DTB binary.
     *
     * @param context Application context to locate files.
     * @throws IOException If dtc binary is missing or conversion fails.
     */
    private static void dts2dtb(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File dtsFile = new File(filesDir, "0.dts");
        File outputFile = new File(filesDir, fileNameDtbFile);
        File dtcBinary = new File(filesDir, "dtc");

        // Verify the source DTS exists
        if (!dtsFile.exists()) {
            throw new IOException("Input DTS file is missing: " + dtsFile.getAbsolutePath());
        }
        // Verify the dtc tool exists and is executable
        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("DTC binary is missing or not executable: " + dtcBinary.getAbsolutePath());
        }

        // Build the shell command for conversion
        String command = String.format(
                "cd %s && ./dtc -I dts -O dtb 0.dts -o %s",
                filesDir, fileNameDtbFile
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture conversion logs for debugging
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Await completion and verify exit status
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

        // Confirm that the DTB output was created
        if (!outputFile.exists()) {
            throw new IOException("Output DTB file not created. Logs: " + log);
        }
    }

    /**
     * Repackages the DTB binary into a new boot image file.
     *
     * @param context Application context for accessing binaries and files.
     * @throws IOException If any input file is missing or repack fails.
     */
    private static void dtb2bootImage(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File kernelFile = new File(filesDir, "00_kernel");
        File dtbFile = new File(filesDir, fileNameDtbFile);
        File outputFile = new File(filesDir, "dtb_new.img");
        File repackDtbBinary = new File(filesDir, "repack_dtb");

        // Validate presence of the kernel image
        if (!kernelFile.exists()) {
            throw new IOException("Kernel file missing: " + kernelFile.getAbsolutePath());
        }
        // Validate presence of the DTB binary
        if (!dtbFile.exists()) {
            throw new IOException("DTB file missing: " + dtbFile.getAbsolutePath());
        }
        // Validate the repack tool is available and executable
        if (!repackDtbBinary.exists() || !repackDtbBinary.canExecute()) {
            throw new IOException("Repack binary missing or not executable: " + repackDtbBinary.getAbsolutePath());
        }

        // Construct the repack shell command
        String command = String.format(
                "cd %s && export LD_LIBRARY_PATH=%s:$LD_LIBRARY_PATH && ./repack_dtb 00_kernel %s dtb_new.img",
                filesDir, filesDir, fileNameDtbFile
        );

        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture repack logs
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Await repack completion and validate success
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

        // Ensure the new boot image was created
        if (!outputFile.exists()) {
            throw new IOException("Output file not created. Logs: " + log);
        }
    }

    /**
     * Simple data structure representing a Device Tree Blob candidate.
     */
    static class dtb {
        int id;                 // Unique identifier index for this DTB
        ChipInfo.type type;     // Corresponding chip type enum
    }
}
