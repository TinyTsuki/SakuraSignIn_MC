package xin.vanilla.sakura.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtils {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 替换路径保留符
     */
    public static String replacePathChar(String name) {
        if (StringUtils.isNullOrEmptyEx(name)) return "_";

        // 替换非法字符为 _
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");

        // 删除控制字符（ASCII < 32）
        sanitized = sanitized.replaceAll("\\p{Cntrl}", "_");

        if (sanitized.equals(".") || sanitized.equals("..")) {
            sanitized = "_";
        }

        // 避免空文件名
        if (sanitized.isEmpty()) {
            sanitized = "_";
        }

        return sanitized;
    }

    public static boolean isFileContentEqual(File file1, File file2) {
        if (!file1.exists() || !file2.exists()) return false;
        if (file1.length() != file2.length()) return false;

        String hash1;
        String hash2;
        try {
            hash1 = computeFileHash(file1);
            hash2 = computeFileHash(file2);
        } catch (IOException | NoSuchAlgorithmException e) {
            LOGGER.error("Failed to compute file hash", e);
            return false;
        }
        return hash1.equals(hash2);
    }

    public static String computeFileHashOrElse(File file, String defaultValue) {
        try {
            return computeFileHash(file);
        } catch (IOException | NoSuchAlgorithmException e) {
            LOGGER.error("Failed to compute file hash", e);
            return defaultValue;
        }
    }

    public static String computeFileHash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
