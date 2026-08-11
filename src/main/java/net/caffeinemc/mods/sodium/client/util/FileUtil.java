package net.caffeinemc.mods.sodium.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Mirrors the reference FileUtil, adapted to Java 8 (the reference uses
 * Files.writeString, introduced in Java 11).
 */
public class FileUtil {
    public static void writeTextRobustly(String text, Path path) throws IOException {
        // Use a temporary location next to the config's final destination
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");

        // Write the file to our temporary location
        Files.write(tempPath, text.getBytes(StandardCharsets.UTF_8));

        // Atomically replace the old config file (if it exists) with the temporary file
        try {
            Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Filesystem does not support atomic moves; fall back to a plain replace
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
