package com.gagent.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class CiLogCompactor {

    private CiLogCompactor() {
    }

    static String extractLogText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (isZipArchive(bytes)) {
            return extractZipLogText(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String compactForDiagnosis(String log, int maxChars) {
        if (log == null || log.isBlank()) {
            return "";
        }
        if (log.length() <= maxChars) {
            return log;
        }

        List<String> errorLines = new ArrayList<>();
        for (String line : log.split("\n")) {
            if (isErrorLine(line)) {
                errorLines.add(line);
            }
        }

        StringBuilder compact = new StringBuilder();
        compact.append("...[earlier log output truncated]...\n\n");

        if (!errorLines.isEmpty()) {
            compact.append("=== Extracted error lines ===\n");
            for (String line : errorLines) {
                compact.append(line).append('\n');
            }
            compact.append('\n');
        }

        int remaining = maxChars - compact.length();
        if (remaining > 0) {
            compact.append("=== Log tail (most recent output) ===\n");
            String tail = log.substring(Math.max(0, log.length() - remaining));
            compact.append(tail);
        }

        if (compact.length() > maxChars) {
            return compact.substring(compact.length() - maxChars);
        }
        return compact.toString();
    }

    static boolean isErrorLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String lower = line.toLowerCase();
        return line.contains("[ERROR]")
                || line.contains("BUILD FAILURE")
                || line.contains("<<< FAILURE")
                || line.contains("<<< ERROR")
                || line.contains("NullPointerException")
                || line.contains("AssertionError")
                || lower.contains("exception:")
                || lower.contains("caused by:")
                || line.contains("Tests run:")
                || line.contains("Failures:")
                || line.contains("Errors:")
                || (lower.contains(" failed") && !lower.contains("0 failed"));
    }

    private static boolean isZipArchive(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private static String extractZipLogText(byte[] bytes) {
        StringBuilder extracted = new StringBuilder();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase();
                if (!name.endsWith(".txt") && !name.endsWith(".log")) {
                    continue;
                }
                byte[] content = zipInputStream.readAllBytes();
                if (extracted.length() > 0) {
                    extracted.append("\n\n=== ").append(entry.getName()).append(" ===\n");
                }
                extracted.append(new String(content, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (extracted.isEmpty()) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return extracted.toString();
    }
}
