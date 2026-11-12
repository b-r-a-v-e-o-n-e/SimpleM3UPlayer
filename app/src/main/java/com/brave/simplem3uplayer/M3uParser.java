package com.brave.simplem3uplayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class M3uParser {
    private static final Pattern TVG_NAME = Pattern.compile("tvg-name=\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);
    private M3uParser() {}

    public static List<Channel> parse(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        List<Channel> channels = new ArrayList<>();
        String pendingName = null, userAgent = null, referer = null;
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                pendingName = extractName(line);
                userAgent = null;
                referer = null;
            } else if (line.regionMatches(true, 0, "#EXTVLCOPT:", 0, 11)) {
                String option = line.substring(11);
                int equals = option.indexOf('=');
                if (equals > 0) {
                    String key = option.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                    String value = option.substring(equals + 1).trim();
                    if (key.equals("http-user-agent")) userAgent = value;
                    if (key.equals("http-referrer") || key.equals("http-referer")) referer = value;
                }
            } else if (!line.startsWith("#") && isSupportedUrl(line)) {
                String name = pendingName == null || pendingName.isEmpty() ? "Channel " + (channels.size() + 1) : pendingName;
                channels.add(new Channel(name, line, userAgent, referer));
                pendingName = null;
                userAgent = null;
                referer = null;
            }
        }
        return channels;
    }

    private static String extractName(String extinf) {
        // A few imported playlists accidentally include the entire browser UA
        // in EXTINF metadata. Prefer the human name after " - ".
        int dash = extinf.lastIndexOf(" - ");
        if (dash >= 0 && extinf.substring(dash + 3).trim().length() > 0) {
            return extinf.substring(dash + 3).trim();
        }
        int comma = extinf.lastIndexOf(',');
        if (comma >= 0 && comma + 1 < extinf.length()) {
            String s = extinf.substring(comma + 1).trim();
            if (!s.isEmpty()) return s;
        }
        Matcher m = TVG_NAME.matcher(extinf);
        return m.find() ? m.group(1).trim() : null;
    }

    private static boolean isSupportedUrl(String line) {
        String s = line.toLowerCase(Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://") ||
               s.startsWith("file://") || s.startsWith("content://");
        // RTSP/RTMP/UDP aren't supported by the bundled Media3 modules.
    }
}
