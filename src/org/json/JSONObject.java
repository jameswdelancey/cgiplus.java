package org.json;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSONObject implementation used for building JSON output
 * without depending on external libraries. Supports storing strings,
 * numbers, booleans, maps and nested JSONObjects.
 */
public class JSONObject {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public JSONObject put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return toJsonObject(values);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JSONObject) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return toJsonObject(map);
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            Iterator<?> iterator = iterable.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(toJson(iterator.next()));
                first = false;
            }
            sb.append(']');
            return sb.toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    private String toJsonObject(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(quote(String.valueOf(entry.getKey())));
            sb.append(':');
            sb.append(toJson(entry.getValue()));
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private String quote(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
