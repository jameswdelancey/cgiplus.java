package app;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QueryUtil {
    private QueryUtil(){}

    public static Map<String,String> parse(String raw) {
        Map<String,String> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i >= 0 ? pair.substring(0, i) : pair;
            String v = i >= 0 ? pair.substring(i+1) : "";
            try {
                k = URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            m.put(k, v);
        }
        return m;
    }
    public static String enc(String s) {
        try { return URLEncoder.encode(s, StandardCharsets.UTF_8); } catch(Exception e){ return s; }
    }
}
