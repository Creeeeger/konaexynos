package xzr.konabess.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for exporting files and directories from the APK's assets to the device's filesystem.
 */
public class AssetsUtil {
    /**
     * Recursively exports files and directories from the assets folder to a destination path.
     *
     * @param context Android context, required to access the AssetManager.
     * @param src     Path within the assets folder (relative, e.g., "mydir" or "mydir/file.txt").
     * @param out     Absolute output path on the device storage.
     * @throws IOException If file operations fail.
     */
    public static void exportFiles(Context context, String src, String out) throws IOException {
        // Get the AssetManager to access assets bundled with the APK
        AssetManager assetManager = context.getAssets();
        File outFile = new File(out);

        // List all entries (files and folders) under the asset path 'src'
        String[] fileNames = assetManager.list(src);

        if (fileNames != null && fileNames.length > 0) {
            // 'src' is a directory (contains files or subdirectories)

            // Make sure the output directory exists; create it if necessary
            if (!outFile.exists() && !outFile.mkdirs()) {
                throw new IOException("Failed to create directory: " + out);
            }

            // Recursively process each entry in the directory
            for (String fileName : fileNames) {
                // Recurse: build new src and out paths for each item
                exportFiles(context, src + "/" + fileName, out + "/" + fileName);
            }
        } else {
            // 'src' is a file, not a directory

            // Open an input stream to the asset file
            try (InputStream is = assetManager.open(src);
                 FileOutputStream fos = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192]; // 8KB buffer for efficient file copy
                int bytesRead;
                // Read from input stream and write to output stream until EOF
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}