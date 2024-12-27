package xzr.konabess.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetsUtil {
    public static void exportFiles(Context context, String src, String out) throws IOException {
        AssetManager assetManager = context.getAssets();
        File outFile = new File(out);

        // Check if source is a directory by listing its contents
        String[] fileNames = assetManager.list(src);

        if (fileNames != null && fileNames.length > 0) {
            // Create the output directory if it doesn't exist
            if (!outFile.exists() && !outFile.mkdirs()) {
                throw new IOException("Failed to create directory: " + out);
            }

            // Recursively process each file or subdirectory
            for (String fileName : fileNames) {
                exportFiles(context, src + "/" + fileName, out + "/" + fileName);
            }
        } else {
            // Handle the case when it's a file
            try (InputStream is = assetManager.open(src);
                 FileOutputStream fos = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192]; // Use a larger buffer size for performance
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}