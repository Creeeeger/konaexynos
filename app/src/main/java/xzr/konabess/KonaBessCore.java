package xzr.konabess;

import android.content.Context;
import android.os.Environment;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import xzr.konabess.utils.AssetsUtil;

public class KonaBessCore {
    public static String dts_path;
    private static int dtb_num;
    public static void cleanEnv(Context context) throws IOException {
        Process process = new ProcessBuilder("sh").start();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        outputStreamWriter.write("rm -rf " + context.getFilesDir().getAbsolutePath() + "/*\nexit" + "\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        bufferedReader.close();
        process.destroy();
    }

    private static final String[] fileList = {"dtc", "magiskboot"};

    public static void setupEnv(Context context) throws IOException {
        for (String s : fileList) {
            AssetsUtil.exportFiles(context, s, context.getFilesDir().getAbsolutePath() + "/" + s);
            File file = new File(context.getFilesDir().getAbsolutePath() + "/" + s);
            file.setExecutable(true);
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
        outputStreamWriter.write("dd if=/dev/block/bootdevice/by-name/boot" + " of=" + context.getFilesDir().getAbsolutePath() + "/dtb.img\n");
        outputStreamWriter.write("chmod 644 " + context.getFilesDir().getAbsolutePath() + "/dtb.img\n");
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
        dtb_num = dtb_split(context);
        for (int i = 0; i < dtb_num; i++) {
            dtb2dts(context, i);
        }
    }
    private static void unpackBootImage(Context context) throws IOException {
        Process process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(process.getInputStream())));
        outputStreamWriter.write("cd " + context.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("./magiskboot unpack dtb.img\n");
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

        File dtb_file = new File(context.getFilesDir().getAbsolutePath() + "/dtb");

        throw new IOException();
    }
    private static void dtb2dts(Context context, int index) throws IOException {
        Process process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(process.getInputStream())));
        outputStreamWriter.write("cd " + context.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("./dtc -I dtb -O dts " + index + ".dtb -o " + index + ".dts\n");
        outputStreamWriter.write("rm -f " + index + ".dtb\n");
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
        if (!new File(context.getFilesDir().getAbsolutePath() + "/" + index + ".dts").exists())
            throw new IOException(log.toString());
    }
    public static ArrayList<dtb> dtbs;

    public static void checkDevice(Context context) throws IOException {
        dtbs = new ArrayList<>();
        for (int i = 0; i < dtb_num; i++) {
            if (checkChip(context, i, "exynos9820")) {
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = ChipInfo.type.exynos9820;
                dtbs.add(dtb);
            } else if (checkChip(context, i, "exynos9825")) {
                dtb dtb = new dtb();
                dtb.id = i;
                dtb.type = ChipInfo.type.exynos9825;
                dtbs.add(dtb);
            }
        }
    }
    public static int getDtbIndex() throws IOException {
        int ret = -1;
        for (String line : getCmdline()) {
            if (line.startsWith("androidboot.dtb_idx")) {
                try {
                    for (int i = line.length() - 1; i >= 0; i--) {
                        ret = Integer.parseInt(line.substring(i));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return ret;
    }
    public static void writeDtbImage(Context context) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("dd if=" + context.getFilesDir().getAbsolutePath() + "/dtb_new" + ".img of=/dev/block/bootdevice/by-name/dtb" + "\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
    }

    public static void backupDtbImage(Context context) throws IOException {
        Process process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("cp -f " + context.getFilesDir().getAbsolutePath() + "/dtb.img " + Environment.getExternalStorageDirectory().getAbsolutePath() + "/dtb.img\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        while (bufferedReader.readLine() != null) {
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
    }

    static class dtb {
        int id;
        ChipInfo.type type;
    }

    public static void chooseTarget(dtb dtb, AppCompatActivity activity) {
        dts_path = activity.getFilesDir().getAbsolutePath() + "/" + dtb.id + ".dts";
        ChipInfo.which = dtb.type;
    }

    private static boolean checkChip(Context context, int index, String chip) throws IOException {
        boolean result = false;
        Process process = new ProcessBuilder("sh").start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("cat " + context.getFilesDir().getAbsolutePath() + "/" + index + ".dts | grep model | grep '" + chip + "'\n");
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
    private static int toUnsignedByte(byte in) {
        return (int) in & 0xFF;
    }

    public static int dtb_split(Context context) throws IOException {
        File dtb = null;
        dtb = new File(context.getFilesDir().getAbsolutePath() + "/dtb");

        byte[] dtb_bytes = new byte[(int) dtb.length()];
        FileInputStream fileInputStream = new FileInputStream(dtb);
        if (fileInputStream.read(dtb_bytes) != dtb.length())
            throw new IOException();
        fileInputStream.close();

        int i = 0;
        ArrayList<Integer> cut = new ArrayList<>();
        while (i + 8 < dtb.length()) {
            if (dtb_bytes[i] == (byte) 0xD0 && dtb_bytes[i + 1] == (byte) 0x0D
                    && dtb_bytes[i + 2] == (byte) 0xFE && dtb_bytes[i + 3] == (byte) 0xED) {
                cut.add(i);
                int size = (int) (toUnsignedByte(dtb_bytes[i + 4]) * Math.pow(256, 3)
                        + toUnsignedByte(dtb_bytes[i + 5]) * Math.pow(256, 2)
                        + toUnsignedByte(dtb_bytes[i + 6]) * Math.pow(256, 1)
                        + toUnsignedByte(dtb_bytes[i + 7]));
                i += size > 0 ? size : 1;
                continue;
            }
            i++;
        }

        for (i = 0; i < cut.size(); i++) {
            File out = new File(context.getFilesDir().getAbsolutePath() + "/" + i + ".dtb");
            FileOutputStream fileOutputStream = new FileOutputStream(out);
            int end = (int) dtb.length();
            try {
                end = cut.get(i + 1);
            } catch (Exception ignored) {
            }

            fileOutputStream.write(dtb_bytes, cut.get(i), end - cut.get(i));
            fileInputStream.close();
        }

        if (!dtb.delete())
            throw new IOException();

        return cut.size();
    }

    public static void dts2bootImage(Context context) throws IOException {
        for (int i = 0; i < dtb_num; i++) {
            dts2dtb(context, i);
        }
        linkDtbs(context);
        dtb2bootImage(context);
    }
    private static void dts2dtb(Context context, int index) throws IOException {
        Process process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(process.getInputStream())));
        outputStreamWriter.write("cd " + context.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("./dtc -I dts -O dtb " + index + ".dts -o " + index + ".dtb\n");
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
        if (!new File(context.getFilesDir().getAbsolutePath() + "/" + index + ".dtb").exists())
            throw new IOException(log.toString());
    }

    public static void linkDtbs(Context context) throws IOException {
        File out;
        out = new File(context.getFilesDir().getAbsolutePath() + "/dtb");

        FileOutputStream fileOutputStream = new FileOutputStream(out);
        for (int i = 0; i < dtb_num; i++) {
            File input = new File(context.getFilesDir().getAbsolutePath() + "/" + i + ".dtb");
            FileInputStream fileInputStream = new FileInputStream(input);
            byte[] b = new byte[(int) input.length()];
            if (fileInputStream.read(b) != input.length())
                throw new IOException();
            fileOutputStream.write(b);
            fileInputStream.close();
        }
        fileOutputStream.close();
    }

    private static void dtb2bootImage(Context context) throws IOException {
        Process process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader((new InputStreamReader(process.getInputStream())));
        outputStreamWriter.write("cd " + context.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("./magiskboot repack boot.img boot_new.img\n");
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
        if (!new File(context.getFilesDir().getAbsolutePath() + "/dtb.img").exists())
            throw new IOException(log.toString());
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
}