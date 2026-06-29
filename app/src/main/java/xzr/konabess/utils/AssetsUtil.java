package xzr.konabess.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Copies bundled native tools and libraries from APK assets to writable storage. */
public class AssetsUtil {
    /**
     * Recursively copies an asset path to a filesystem path.
     *
     * <p>A non-empty result from {@link android.content.res.AssetManager#list(String)} is treated as
     * a directory; an empty result is opened as a file.
     *
     * @param context context that owns the APK assets
     * @param src path relative to the APK asset root
     * @param out destination file or directory path
     * @throws IOException if an asset cannot be listed, opened, copied, or created
     */
    public static void exportFiles(Context context, String src, String out) throws IOException {
        AssetManager assetManager = context.getAssets();
        File outFile = new File(out);

        String[] fileNames = assetManager.list(src);

        if (fileNames != null && fileNames.length > 0) {
            if (!outFile.exists() && !outFile.mkdirs()) {
                throw new IOException("Failed to create directory: " + out);
            }

            for (String fileName : fileNames) {
                exportFiles(context, src + "/" + fileName, out + "/" + fileName);
            }
        } else {
            try (InputStream is = assetManager.open(src);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}
