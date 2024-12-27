package xzr.konabess;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
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
        Process process = new ProcessBuilder("su").start();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        outputStreamWriter.write("rm -rf " + context.getFilesDir().getAbsolutePath() + "/*\nexit" + "\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        bufferedReader.close();
        process.destroy();
    }

    public static void setupEnv(Context context) throws IOException {
        for (String s : fileList) {
            AssetsUtil.exportFiles(context, s, context.getFilesDir().getAbsolutePath() + "/" + s);
            File file = new File(context.getFilesDir().getAbsolutePath() + "/" + s);
            file.setExecutable(true);
            file.setReadable(true);   // Ensure readability
            file.setWritable(true);   // Ensure writ-ability

            if (!file.canExecute())
                throw new IOException();
        }
    }

    public static void reboot() throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("svc power reboot\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
    }

    public static void getDtImage(Context context) throws IOException {
        getRealDtImage(context);
    }

    private static void getRealDtImage(Context context) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("dd if=/dev/block/by-name/dtb" + " of=" + context.getFilesDir().getAbsolutePath() + "/dtb.img\n");
        outputStreamWriter.write("chmod 777 " + context.getFilesDir().getAbsolutePath() + "/dtb.img\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();

        File target = new File(context.getFilesDir().getAbsolutePath() + "/dtb.img");
        if (!target.exists() || !target.canRead()) {
            target.delete();
            throw new IOException();
        }
    }

    public static void dtbImage2dts(Context context) throws IOException {
        unpackBootImage(context);
        dtb2dts(context);
    }

    public static void unpackBootImage(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();
        File extractBinary = new File(filesDir, "extract_dtb");

        // Ensure extract_dtb exists and is executable
        if (!extractBinary.exists() || !extractBinary.canExecute()) {
            throw new IOException("extract_dtb binary is missing or not executable");
        }

        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        outputStreamWriter.write("cd " + filesDir + "\n");
        outputStreamWriter.write("export LD_LIBRARY_PATH=" + context.getFilesDir().getAbsolutePath() + ":$LD_LIBRARY_PATH\n");
        outputStreamWriter.write("./extract_dtb dtb.img\n");

        // Check if the extracted folder exists
        outputStreamWriter.write("[ -d dtb ] || mkdir dtb\n");
        outputStreamWriter.write("mv dtb/* . || echo 'Move failed'\n");
        outputStreamWriter.write("rm -rf dtb\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();

        StringBuilder log = new StringBuilder();
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            log.append(s).append("\n");
        }

        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();

        // Check if output file(s) exist
        File extractedFile = new File(filesDir, "01_dtbdump_samsung,armv8.dtb");
        if (!extractedFile.exists()) {
            System.out.println("error: " + log);
            throw new IOException("Failed to extract DTB: " + log);
        }
    }

    private static void dtb2dts(Context context) throws IOException {
        String filesDir = context.getFilesDir().getAbsolutePath();

        // Ensure dtc binary exists and is executable
        File dtcBinary = new File(filesDir, "dtc");
        if (!dtcBinary.exists() || !dtcBinary.canExecute()) {
            throw new IOException("dtc binary is missing or not executable");
        }

        // Check input file existence
        File inputFile = new File(filesDir, "01_dtbdump_samsung,armv8.dtb");
        if (!inputFile.exists()) {
            throw new IOException("Input DTB file does not exist: " + inputFile.getAbsolutePath());
        }

        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        outputStreamWriter.write("cd " + filesDir + "\n");
        outputStreamWriter.write("./dtc -I dtb -O dts 01_dtbdump_samsung,armv8.dtb -o 0.dts\n");
        outputStreamWriter.write("rm -f 01_dtbdump_samsung,armv8.dtb\n");
        outputStreamWriter.write("chmod 777 0.dts\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();

        StringBuilder log = new StringBuilder();
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            log.append(s).append("\n");
        }

        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();

        // Verify output file
        File outputFile = new File(filesDir, "0.dts");
        if (!outputFile.exists() || !outputFile.canRead()) {
            System.out.println("error: " + log);
            throw new IOException("DTS conversion failed: " + log.toString());
        }
    }

    public static void checkDevice(Context context) throws IOException {
        dtbs = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            if (checkChip(context, "exynos9820")) {
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = ChipInfo.type.exynos9820;
                dtbs.add(dtb);
            } else if (checkChip(context, "exynos9825")) {
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = ChipInfo.type.exynos9825;
                dtbs.add(dtb);
            }
        }
    }

    private static boolean checkChip(Context context, String chip) throws IOException {
        boolean result = false;
        Process process = new ProcessBuilder("su").start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("cat " + context.getFilesDir().getAbsolutePath() + "/0.dts | grep '" + chip + "'\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        String s = bufferedReader.readLine();
        if (s != null)
            result = true;
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
        return result;
    }

    public static int getDtbIndex() throws IOException {
        int ret = -1;
        for (String line : getCmdline()) {
            if (line.startsWith("androidboot.dtbo_idx")) {
                ret = 1;
                break;
            }
        }
        return ret;
    }

    private static List<String> getCmdline() throws IOException {
        Process process = new ProcessBuilder("su").start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("cat /proc/cmdline\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        String s = bufferedReader.readLine();
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
        return s != null ? Arrays.asList(s.split(" ")) : new ArrayList<>();
    }

    public static void writeDtbImage(Context context) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("dd if=" + context.getFilesDir().getAbsolutePath() + "/dtb_new.img of=/dev/block/by-name/dtb\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
    }

    public static void backupDtbImage(Context context) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("cp -f " + context.getFilesDir().getAbsolutePath() + "/dtb.img " + "/storage/emulated/0/dtb.img\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
    }

    public static void chooseTarget(dtb dtb, AppCompatActivity activity) {
        dts_path = activity.getFilesDir().getAbsolutePath() + "/0.dts";
        ChipInfo.which = dtb.type;
    }

    public static void dts2bootImage(Context context) throws IOException {
        dts2dtb(context);
        dtb2bootImage(context);
    }

    private static void dts2dtb(Context context) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(process.getInputStream())));
        outputStreamWriter.write("cd " + context.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("./dtc -I dts -O dtb 0.dts -o 01_dtbdump_samsung,armv8.dtb\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        StringBuilder log = new StringBuilder();
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            log.append(s).append("\n");
        }
        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();
        if (!new File(context.getFilesDir().getAbsolutePath() + "/01_dtbdump_samsung,armv8.dtb").exists())
            throw new IOException(log.toString());
    }

    private static void dtb2bootImage(Context context) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(process.getInputStream())));
        outputStreamWriter.write("cd " + context.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("export LD_LIBRARY_PATH=" + context.getFilesDir().getAbsolutePath() + ":$LD_LIBRARY_PATH\n");
        outputStreamWriter.write("./repack_dtb 00_kernel 01_dtbdump_samsung,armv8.dtb dtb_new.img\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        StringBuilder log = new StringBuilder();
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            log.append(s).append("\n");
        }
        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();
        if (!new File(context.getFilesDir().getAbsolutePath() + "/dtb_new.img").exists())
            throw new IOException(log.toString());
    }

    static class dtb {
        int id;
        ChipInfo.type type;
    }
}