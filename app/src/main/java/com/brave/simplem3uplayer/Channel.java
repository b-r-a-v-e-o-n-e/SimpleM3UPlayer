package com.brave.simplem3uplayer;

public final class Channel {
    public final String name;
    public final String url;
    public final String userAgent;
    public final String referer;

    public Channel(String name, String url, String userAgent, String referer) {
        this.name = name;
        this.url = url;
        this.userAgent = userAgent;
        this.referer = referer;
    }

    @Override public String toString() { return name; }
}
