package com.winlator.core;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class FileUtils {
    public static byte[] read(Context context, String assetFile) {
        try (InputStream inStream = context.getAssets().open(assetFile)) {
            return StreamUtils.copyToByteArray(inStream);
        }
        catch (IOException e) {
            return null;
        }
    }

    public static byte[] read(File file) {
        try (InputStream inStream = new BufferedInputStream(new FileInputStream(file))) {
            return StreamUtils.copyToByteArray(inStream);
        }
        catch (IOException e) {
            return null;
        }
    }

    public static String readString(Context context, String assetFile) {
        return new String(read(context, assetFile), StandardCharsets.UTF_8);
    }

    public static String readString(File file) {
        return new String(read(file), StandardCharsets.UTF_8);
    }

    public static String readString(Context context, Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
        catch (IOException e) {
            return null;
        }
    }

    public static boolean write(File file, byte[] data) {
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(data, 0, data.length);
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean writeString(File file, String data) {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(data);
            bw.flush();
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void symlink(File linkTarget, File linkFile) {
        symlink(linkTarget.getAbsolutePath(), linkFile.getAbsolutePath());
    }

    public static void symlink(String linkTarget, String linkFile) {
        try {
            (new File(linkFile)).delete();
            Os.symlink(linkTarget, linkFile);
        }
        catch (ErrnoException e) {
            Log.e("FileUtils", "Failed to symlink: " + e);
        }
    }

    public static boolean isSymlink(File file) {
        return Files.isSymbolicLink(file.toPath());
    }

    public static boolean delete(File targetFile) {
        if (targetFile == null) return false;
        if (targetFile.isDirectory()) {
            if (!isSymlink(targetFile)) if (!clear(targetFile)) return false;
        }
        return targetFile.delete();
    }

    public static boolean clear(File targetFile) {
        if (targetFile == null) return false;
        if (targetFile.isDirectory()) {
            File[] files = targetFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!delete(file)) return false;
                }
            }
        }
        return true;
    }

    public static boolean isEmpty(File targetFile) {
        if (targetFile == null) return true;
        if (targetFile.isDirectory()) {
            String[] files = targetFile.list();
            return files == null || files.length == 0;
        }
        else return targetFile.length() == 0;
    }

    public static boolean copy(File srcFile, File dstFile) {
        return copy(srcFile, dstFile, null);
    }

    public static boolean copy(File srcFile, File dstFile, Callback<File> callback) {
        if (isSymlink(srcFile)) return true;
        if (srcFile.isDirectory()) {
            if (!dstFile.exists() && !dstFile.mkdirs()) return false;
            if (callback != null) callback.call(dstFile);

            String[] filenames = srcFile.list();
            if (filenames != null) {
                for (String filename : filenames) {
                    if (!copy(new File(srcFile, filename), new File(dstFile, filename), callback)) {
                        return false;
                    }
                }
            }
        }
        else {
            File parent = dstFile.getParentFile();
            if (!srcFile.exists() || (parent != null && !parent.exists() && !parent.mkdirs())) return false;

            try {
                FileChannel inChannel = (new FileInputStream(srcFile)).getChannel();
                FileChannel outChannel = (new FileOutputStream(dstFile)).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
                inChannel.close();
                outChannel.close();

                if (callback != null) callback.call(dstFile);
                return dstFile.exists();
            }
            catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    public static void copy(Context context, String assetFile, File dstFile) {
        if (isDirectory(context, assetFile)) {
            if (!dstFile.isDirectory()) dstFile.mkdirs();
            try {
                String[] filenames = context.getAssets().list(assetFile);
                for (String filename : filenames) {
                    String relativePath = StringUtils.addEndSlash(assetFile)+filename;
                    if (isDirectory(context, relativePath)) {
                        copy(context, relativePath, new File(dstFile, filename));
                    }
                    else copy(context, relativePath, dstFile);
                }
            }
            catch (IOException e) {
                Log.e("FileUtils", "Failed to copy directory: " + e);
            }
        }
        else {
            if (dstFile.isDirectory()) dstFile = new File(dstFile, FileUtils.getName(assetFile));
            File parent = dstFile.getParentFile();
            if (!parent.isDirectory()) parent.mkdirs();
            try (InputStream inStream = context.getAssets().open(assetFile);
                 BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(dstFile), StreamUtils.BUFFER_SIZE)) {
                StreamUtils.copy(inStream, outStream);
            }
            catch (IOException e) {
                Log.e("FileUtils", "Failed to copy file: " + e);
            }
        }
    }

    public static ArrayList<String> readLines(File file) {
        ArrayList<String> lines = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    public static String getName(String path) {
        if (path == null) return "";
        path = StringUtils.removeEndSlash(path);
        int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(index + 1);
    }

    public static String getBasename(String path) {
        return getName(path).replaceFirst("\\.[^\\.]+$", "");
    }

    public static String getDirname(String path) {
        if (path == null) return "";
        path = StringUtils.removeEndSlash(path);
        int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(0, index);
    }

    public static void chmod(File file, int mode) {
        Log.d("FileUtils", "Attempting to chmod " + file.getAbsolutePath());
        try {
            Os.chmod(file.getAbsolutePath(), mode);
            Log.d("FileUtils", "Successfully chmod-ed " + file.getAbsolutePath());
        }
        catch (ErrnoException e) {
            Log.e("FileUtils", "Failed to chmod " + file.getAbsolutePath() + ": " + e);
        }
    }

    public static File createTempFile(File parent, String prefix) {
        File tempFile = null;
        boolean exists = true;
        while (exists) {
            tempFile = new File(parent, prefix+"-"+ UUID.randomUUID().toString().replace("-", "")+".tmp");
            exists = tempFile.exists();
        }
        return tempFile;
    }

    public static String getFilePathFromUri(Uri uri) {
        String path = null;
        if (uri.getAuthority().equals("com.android.externalstorage.documents")) {
            String[] parts = uri.getLastPathSegment().split(":");
            if (parts[0].equalsIgnoreCase("primary")) path = Environment.getExternalStorageDirectory() + "/" + parts[1];
        }
        return path;
    }

    public static boolean contentEquals(File origin, File target) {
        if (origin.length() != target.length()) return false;

        try (InputStream inStream1 = new BufferedInputStream(new FileInputStream(origin));
             InputStream inStream2 = new BufferedInputStream(new FileInputStream(target))) {
            int data;
            while ((data = inStream1.read()) != -1) {
                if (data != inStream2.read()) return false;
            }
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public static void getSizeAsync(File file, Callback<Long> callback) {
        Executors.newSingleThreadExecutor().execute(() -> getSize(file, callback));
    }

    private static void getSize(File file, Callback<Long> callback) {
        if (file == null) return;
        if (file.isFile()) {
            callback.call(file.length());
            return;
        }

        Stack<File> stack = new Stack<>();
        stack.push(file);

        while (!stack.isEmpty()) {
            File current = stack.pop();
            File[] files = current.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isDirectory()) {
                    stack.push(f);
                }
                else {
                    long length = f.length();
                    if (length > 0) callback.call(length);
                }
            }
        }
    }

    public static long getSize(AssetManager assetManager, String assetFile) {
        try (InputStream inStream = assetManager.open(assetFile)) {
            return inStream.available();
        }
        catch (IOException e) {
            return 0;
        }
    }

    public static long getInternalStorageSize() {
        File dataDir = Environment.getDataDirectory();
        StatFs stat = new StatFs(dataDir.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return totalBlocks * blockSize;
    }

    public static boolean isDirectory(Context context, String assetFile) {
        try {
            String[] files = context.getAssets().list(assetFile);
            return files != null && files.length > 0;
        }
        catch (IOException e) {
            return false;
        }
    }

    public static String toRelativePath(String basePath, String fullPath) {
        return StringUtils.removeEndSlash((fullPath.startsWith("/") ? "/" : "")+(new File(basePath).toURI().relativize(new File(fullPath).toURI()).getPath()));
    }

    public static int readInt(String path) {
        int result = 0;
        try {
            try (RandomAccessFile reader = new RandomAccessFile(path, "r")) {
                String line = reader.readLine();
                result = !line.isEmpty() ? Integer.parseInt(line) : 0;
            }
        }
        catch (Exception e) {
            Log.e("FileUtils", "Failed to read int: " + e);
        }
        return result;
    }

    public static String readSymlink(File file) {
        try {
            return Files.readSymbolicLink(file.toPath()).toString();
        }
        catch (IOException e) {
            return "";
        }
    }

    public static boolean extractZipFromAssets(Context context, String assetName, File destinationDir) {
        try (InputStream inputStream = context.getAssets().open(assetName);
             ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File outFile = new File(destinationDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outFile.isDirectory()) outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.isDirectory()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    chmod(outFile, 0771);
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean extractZipFromInputStream(Context context, InputStream inStream, File destinationDir) {
        try (ZipInputStream zis = new ZipInputStream(inStream)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File outFile = new File(destinationDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!outFile.isDirectory()) outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.isDirectory()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    chmod(outFile, 0771);
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String readZipManifestNameFromAssets(Context context, String assetName) {
        try (InputStream inputStream = context.getAssets().open(assetName);
             ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
                    byte[] bytes = StreamUtils.copyToByteArray(zis);
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(new String(bytes));
                        String name = json.optString("name", json.optString("libraryName", "")).trim();
                        if (!name.isEmpty()) {
                            return name;
                        }
                    } catch (org.json.JSONException ignored) {
                    }
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static boolean writeToBinaryFile(String filename, int position, int data) {
        try (RandomAccessFile file = new RandomAccessFile(filename, "rw")) {
            file.seek(position);
            file.write(data);
            return true;
        } catch (IOException e) {
            Log.e("FileUtils", "Failed to write data " + data + " at " + position + " to " + filename);
            return false;
        }
    }

    public static String readZipManifestNameFromInputStream(InputStream inStream) {
        try (ZipInputStream zis = new ZipInputStream(inStream)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
                    byte[] bytes = StreamUtils.copyToByteArray(zis);
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(new String(bytes));
                        String name = json.optString("name", json.optString("libraryName", "")).trim();
                        if (!name.isEmpty()) {
                            return name;
                        }
                    } catch (org.json.JSONException ignored) {
                    }
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    // --- High-level helpers for installed adrenotools drivers -------------------------------------------------
    public static File getAdrenoInstalledRoot(Context context) {
        return new File(context.getFilesDir(), "installed_components/adrenotools_driver");
    }

    public static List<String> listInstalledAdrenoDrivers(Context context) {
        ArrayList<String> results = new ArrayList<>();
        File root = getAdrenoInstalledRoot(context);
        try {
            if (root.isDirectory()) {
                File[] dirs = root.listFiles((f) -> f.isDirectory());
                if (dirs != null) {
                    for (File d : dirs) results.add(d.getName());
                }
            }
        } catch (Exception e) {
            Log.e("FileUtils", "Failed to list Adreno drivers: " + e);
        }
        return results;
    }

    public static boolean deleteInstalledAdrenoDriver(Context context, String identifier) {
        try {
            File dir = new File(getAdrenoInstalledRoot(context), identifier);
            if (dir.exists()) return delete(dir);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Install an adrenotools driver from an InputStream. Returns the identifier (manifest name) on success, or null on failure.
     */
    public static String installAdrenoDriverFromInputStream(Context context, InputStream inStream) {
        File tempFile = null;
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir == null) cacheDir = context.getFilesDir();
            tempFile = File.createTempFile("adreno_", ".zip", cacheDir);

            // Stream incoming InputStream to temp file to avoid buffering whole file in memory
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                // No explicit fos.flush() needed; close() will flush
            }

            // Read manifest name (identifier) from temp file
            String identifier;
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                identifier = readZipManifestNameFromInputStream(fis);
            }
            if (identifier == null || identifier.isEmpty()) return null;

            // Validate and sanitize identifier to prevent path traversal
            identifier = identifier.trim();
            if (identifier.contains("..") || identifier.contains("/") || identifier.contains("\\")) {
                Log.e("FileUtils", "installAdrenoDriverFromInputStream: invalid identifier (path traversal): " + identifier);
                return null;
            }
            // Allow only letters, digits, hyphen and underscore
            if (!identifier.matches("[A-Za-z0-9_-]+")) {
                Log.e("FileUtils", "installAdrenoDriverFromInputStream: invalid identifier (characters not allowed): " + identifier);
                return null;
            }

            File root = getAdrenoInstalledRoot(context);
            File compDir = new File(root, identifier);
            if (compDir.exists()) delete(compDir);
            if (!compDir.exists() && !compDir.mkdirs()) return null;

            // Extract from temp file
            boolean ok;
            try (FileInputStream fis2 = new FileInputStream(tempFile)) {
                ok = extractZipFromInputStream(context, fis2, compDir);
            }
            return ok ? identifier : null;
        } catch (Exception e) {
            Log.e("FileUtils", "Failed to install Adreno driver from stream: " + e);
            return null;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Convenience: install directly from a Uri (returns identifier on success, null on failure)
     */
    public static String installAdrenoDriverFromUri(Context context, android.net.Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return installAdrenoDriverFromInputStream(context, in);
        } catch (Exception e) {
            Log.e("FileUtils", "Failed to install Adreno driver from uri: " + e);
            return null;
        }
    }
}
