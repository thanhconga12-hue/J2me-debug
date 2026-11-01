package ru.playsoftware.j2meloader.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import ru.playsoftware.j2meloader.config.Config;

/** Fingerprint helpers for the isolated, instrumented MIDlet DEX cache. */
public final class NstFpsBytecodeCache {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private NstFpsBytecodeCache() {
    }

    public static String fingerprint(File sourceJar) throws IOException {
        if (sourceJar == null || !sourceJar.isFile()) {
            throw new IOException("MIDlet source JAR does not exist: " + sourceJar);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = new FileInputStream(sourceJar)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder(Config.MIDLET_BYTECODE_VERSION.length() + 65);
            result.append(Config.MIDLET_BYTECODE_VERSION).append(':');
            for (byte value : hash) {
                int unsigned = value & 0xff;
                result.append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0f]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is not available", error);
        }
    }

    public static boolean isCurrent(File appDir, String expectedFingerprint) throws IOException {
        File stamp = new File(appDir, Config.MIDLET_BYTECODE_STAMP);
        if (!stamp.isFile()) {
            return false;
        }
        String actual = new String(FileUtils.getBytes(stamp), StandardCharsets.US_ASCII).trim();
        return expectedFingerprint.equals(actual);
    }

    /** Publish the stamp only after a DEX archive has been generated successfully. */
    public static void writeFingerprint(File appDir, String fingerprint) throws IOException {
        File stamp = new File(appDir, Config.MIDLET_BYTECODE_STAMP);
        File temporary = new File(appDir, Config.MIDLET_BYTECODE_STAMP + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(fingerprint.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        }
        if (stamp.exists() && !stamp.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Cannot replace MIDlet bytecode fingerprint: " + stamp);
        }
        if (!temporary.renameTo(stamp)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Cannot publish MIDlet bytecode fingerprint: " + stamp);
        }
    }
}
