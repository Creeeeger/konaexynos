package xzr.konabess;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import xzr.konabess.utils.AssetsUtil;

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
    public static String dts_path;
    public static ArrayList<dtb> dtbs;

    public static void cleanEnv(Context context) throws IOException {
        File dir = context.getFilesDir();
        deleteRecursive(dir);
    }

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

    public static void setupEnv(Context context) throws IOException {
        for (String s : fileList) {
            // Export the file
            File destination = new File(context.getFilesDir(), s);
            AssetsUtil.exportFiles(context, s, destination.getAbsolutePath());

            // Set permissions
            if (!destination.setExecutable(true, false) ||  // Executable by all
                    !destination.setReadable(true, false) ||    // Readable by all
                    !destination.setWritable(true, false)) {    // Writable by all
                throw new IOException("Failed to set permissions for: " + destination.getAbsolutePath());
            }

            // Final validation
            if (!destination.canExecute()) {
                throw new IOException("File is not executable: " + destination.getAbsolutePath());
            }
        }
    }

    public static void reboot() throws IOException {
        // Execute the reboot command using 'su'
        Process process = new ProcessBuilder("su", "-c", "svc power reboot")
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Consume output (optional: log output if needed)
            }
        }

        // Wait for the process to complete
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

    public static void getDtImage(Context context) throws IOException {
        // Define file paths
        String internalPath = context.getFilesDir().getAbsolutePath() + "/dtb.img";
        String externalPath = "/storage/emulated/0/dtb.img";

        // Prepare shell commands
        String[] commands = {
                "dd if=/dev/block/by-name/dtb of=" + internalPath,
                "chmod 777 " + internalPath,
                "cp -f " + internalPath + " " + externalPath
        };

        // Execute commands in shell
        Process process = null;
        try {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                // Write commands
                for (String command : commands) {
                    writer.write(command + "\n");
                }
                writer.write("exit\n");
                writer.flush();

                // Read process output to avoid hanging
                while (reader.readLine() != null) {
                    // Consume output (optional: log or debug output if necessary)
                }

                // Wait for process to complete
                if (process.waitFor() != 0) {
                    throw new IOException("Shell command execution failed.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new IOException("Process interrupted", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        // Validate created file
        File target = new File(internalPath);
        if (!target.exists() || !target.canRead()) {
            if (target.exists()) target.delete(); // Clean up if file is invalid
            throw new IOException("Failed to create or read dtb.img");
        }
    }

    public static void dtbImage2dts(Context context) throws IOException {
        unpackBootImage(context);
        dtb2dts(context);
    }

    public static void unpackBootImage(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File extractBinary = new File(filesDir, "extract_dtb");

        // Ensure extract_dtb binary exists and is executable
        if (!extractBinary.exists() || !extractBinary.canExecute()) {
            throw new IOException("extract_dtb binary is missing or not executable");
        }

        // Execute the commands using ProcessBuilder
        ProcessBuilder processBuilder = new ProcessBuilder(
                "su", "-c", String.format(
                "cd %s && " + // Navigate to the working directory
                        "export LD_LIBRARY_PATH=%s:$LD_LIBRARY_PATH && " + // Set the library path
                        "./extract_dtb dtb.img && " + // Execute the binary
                        "[ -d dtb ] || mkdir dtb && " + // Create the folder if it doesn't exist
                        "mv dtb/* . || echo 'Move failed' && " + // Move the extracted files
                        "rm -rf dtb", // Clean up
                filesDir, filesDir
        )
        );
        processBuilder.redirectErrorStream(true);

        // Start process and capture output
        Process process = processBuilder.start();
        StringBuilder log = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n"); // Capture logs
            }
        }

        // Wait for process to complete and check exit code
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

        // Validate output file(s)
        File extractedFile = new File(filesDir, "01_dtbdump_samsung,armv8.dtb");
        if (!extractedFile.exists()) {
            throw new IOException("Failed to extract DTB. Logs: " + log);
        }
    }

    private static void dtb2dts(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();

        // Validate dtc binary
        File dtcBinary = new File(filesDir, "dtc");
        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("dtc binary is missing or not executable: " + dtcBinary.getAbsolutePath());
        }

        // Validate input DTB file
        File inputFile = new File(filesDir, "01_dtbdump_samsung,armv8.dtb");
        if (!inputFile.exists()) {
            throw new IOException("Input DTB file does not exist: " + inputFile.getAbsolutePath());
        }

        // Prepare output file
        File outputFile = new File(filesDir, "0.dts");

        // Construct command to convert DTB to DTS
        String command = String.format(
                "cd %s && ./dtc -I dtb -O dts %s -o %s && rm -f %s && chmod 777 %s",
                filesDir,
                inputFile.getName(),
                outputFile.getName(),
                inputFile.getName(),
                outputFile.getName()
        );

        // Execute the command with root permissions
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Capture logs
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Wait for process completion
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

        // Validate output DTS file
        if (!outputFile.exists() || !outputFile.canRead()) {
            throw new IOException("DTS conversion failed. Log: " + log);
        }
    }

    public static void checkDevice(Context context) throws IOException {
        dtbs = new ArrayList<>();

        // Array of supported chip types for easy extensibility
        String[] chipTypes = {"exynos9820", "exynos9825"};
        ChipInfo.type[] chipInfoTypes = {ChipInfo.type.exynos9820, ChipInfo.type.exynos9825};

        // Iterate through chip types and check each one
        for (int i = 0; i < chipTypes.length; i++) {
            if (checkChip(context, chipTypes[i])) {
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = chipInfoTypes[i];
                dtbs.add(dtb);
                break; // Exit loop once a matching chip is found
            }
        }

        // Throw exception if no valid chip is detected
        if (dtbs.isEmpty()) {
            throw new IOException("No supported chip detected.");
        }
    }

    private static boolean checkChip(Context context, String chip) throws IOException {
        // Construct the command to check for the chip string in the DTS file
        String command = String.format(
                "grep '%s' %s/0.dts",
                chip, context.getFilesDir().getAbsolutePath()
        );

        // Execute the command with root privileges
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        boolean result;

        // Use try-with-resources for automatic resource management
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            result = reader.readLine() != null; // If output exists, chip is found
        }

        // Wait for process to complete and check exit code
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 && !result) {
                throw new IOException("Command failed with exit code: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted state
            throw new IOException("Process interrupted", e);
        } finally {
            process.destroy(); // Ensure process is destroyed
        }

        return result;
    }

    public static int getDtbIndex() throws IOException {
        // Iterate through command-line parameters and look for the target prefix
        for (String line : getCmdline()) {
            if (line.contains("androidboot.dtbo_idx=")) {
                // Extract and return the index value
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    try {
                        return Integer.parseInt(parts[1].trim()); // Parse index as integer
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid dtbo_idx value: " + parts[1], e);
                    }
                }
            }
        }
        // Return -1 if no matching parameter is found
        return -1;
    }

    private static List<String> getCmdline() throws IOException {
        // Execute the command with root privileges
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", "cat /proc/cmdline");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture output
        List<String> cmdlineArgs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cmdlineArgs.addAll(Arrays.asList(line.split(" ")));
            }
        }

        // Wait for process completion
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

    public static void writeDtbImage(Context context) throws IOException {
        // Define the paths for the input and output
        String inputPath = new File(context.getFilesDir(), "dtb_new.img").getAbsolutePath();
        String outputPath = "/dev/block/by-name/dtb";

        // Validate that the input file exists before proceeding
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input DTB image not found: " + inputPath);
        }

        // Build the shell command
        String command = String.format("dd if=%s of=%s", inputPath, outputPath);

        // Execute the command with root privileges
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture logs
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Wait for process completion
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to write DTB image. Exit code: " + exitCode + "\nLogs: " + log);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Process was interrupted", e);
        } finally {
            process.destroy();
        }
    }

    // Select the target DTB and update ChipInfo
    public static void chooseTarget(dtb dtb, AppCompatActivity activity) {
        // Set the path for the DTS file
        dts_path = new File(activity.getFilesDir(), "0.dts").getAbsolutePath();

        // Update the chip type
        ChipInfo.which = dtb.type;
    }

    // Converts DTS to DTB and repacks it into a boot image
    public static void dts2bootImage(Context context) throws IOException {
        // Convert DTS to DTB
        dts2dtb(context);

        // Repack DTB into a boot image
        dtb2bootImage(context);
    }

    private static void dts2dtb(Context context) throws IOException {
        // Define file paths
        String filesDir = context.getFilesDir().getAbsolutePath();
        File dtsFile = new File(filesDir, "0.dts");
        File outputFile = new File(filesDir, "01_dtbdump_samsung,armv8.dtb");
        File dtcBinary = new File(filesDir, "dtc");

        // Validate required files
        if (!dtsFile.exists()) {
            throw new IOException("Input DTS file is missing: " + dtsFile.getAbsolutePath());
        }
        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("DTC binary is missing or not executable: " + dtcBinary.getAbsolutePath());
        }

        // Build the shell command
        String command = String.format(
                "cd %s && ./dtc -I dts -O dtb 0.dts -o 01_dtbdump_samsung,armv8.dtb",
                filesDir
        );

        // Execute the command with root privileges
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture logs
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Wait for process completion
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

        // Validate output file
        if (!outputFile.exists()) {
            throw new IOException("Output DTB file not created. Logs: " + log);
        }
    }

    private static void dtb2bootImage(Context context) throws IOException {
        // Get the files directory path
        String filesDir = context.getFilesDir().getAbsolutePath();

        // Validate the required input files
        File kernelFile = new File(filesDir, "00_kernel");
        File dtbFile = new File(filesDir, "01_dtbdump_samsung,armv8.dtb");
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

        // Build the shell command
        String command = String.format(
                "cd %s && export LD_LIBRARY_PATH=%s:$LD_LIBRARY_PATH && ./repack_dtb 00_kernel 01_dtbdump_samsung,armv8.dtb dtb_new.img",
                filesDir, filesDir
        );

        // Execute the command with root privileges
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Capture logs
        StringBuilder log = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }

        // Wait for process completion
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

        // Validate output file
        if (!outputFile.exists()) {
            throw new IOException("Output file not created. Logs: " + log);
        }
    }

    static class dtb {
        int id;
        ChipInfo.type type;
    }
}