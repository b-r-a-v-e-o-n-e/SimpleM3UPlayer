package com.brave.simplem3uplayer;
import java.io.FileReader;
import java.util.List;
public class ParserTest {
    public static void main(String[] args) throws Exception {
        List<Channel> channels = M3uParser.parse(new FileReader(args[0]));
        if (channels.size() != 92) throw new AssertionError("Expected 92, got " + channels.size());
        if (channels.stream().noneMatch(c -> c.name.equals("Bloomberg TV Bulgaria"))) throw new AssertionError("EXTINF name parsing failed");
        if (channels.stream().filter(c -> c.userAgent != null).count() != 18) throw new AssertionError("User-Agent parsing failed");
        System.out.println("OK: " + channels.size() + " channels; " + channels.stream().filter(c -> c.userAgent != null).count() + " User-Agent overrides");
    }
}
