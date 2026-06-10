package com.gagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class CiJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CiJsonParser() {}

    public static String stripMarkdownFences(String json) {
        if (json == null) return "";
        String trimmed = json.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    public static <T> T parse(String json, Class<T> type) {
        try {
            return MAPPER.readValue(stripMarkdownFences(json), type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI JSON response: " + e.getMessage(), e);
        }
    }
}
