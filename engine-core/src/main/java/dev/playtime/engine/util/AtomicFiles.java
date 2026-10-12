package dev.playtime.engine.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes files via a temporary sibling and a move so a crash never leaves half a save. */
public final class AtomicFiles {
    private AtomicFiles() {}

    public static void writeString(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            if (Files.exists(target)) {
                Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Reads {@code target}, falling back to the {@code .bak} copy if the main file is missing. */
    public static String readStringOrBackup(Path target) throws IOException {
        if (Files.exists(target)) {
            return Files.readString(target, StandardCharsets.UTF_8);
        }
        Path bak = target.resolveSibling(target.getFileName() + ".bak");
        if (Files.exists(bak)) {
            return Files.readString(bak, StandardCharsets.UTF_8);
        }
        return null;
    }
}
