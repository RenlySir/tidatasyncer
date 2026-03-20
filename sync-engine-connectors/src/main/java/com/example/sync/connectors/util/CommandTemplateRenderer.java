package com.example.sync.connectors.util;

import java.util.Map;

public final class CommandTemplateRenderer {

    private CommandTemplateRenderer() {
    }

    public static String render(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", safe(entry.getValue()));
        }
        return rendered;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
