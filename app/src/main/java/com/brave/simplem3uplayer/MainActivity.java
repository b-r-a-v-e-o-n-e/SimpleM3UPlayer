package com.brave.simplem3uplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextClock;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PICK_M3U = 1001;
    private static final int ORANGE = Color.rgb(255, 133, 39);
    private static final int PANEL = Color.rgb(17, 22, 29);
    private static final int PANEL_LIGHT = Color.rgb(27, 34, 45);
    private static final int MUTED = Color.rgb(169, 178, 190);
    private static final int DIVIDER = Color.rgb(54, 62, 75);
    private static final long LOAD_TIMEOUT_MS = 3200L;
    private static final long REBUFFER_TIMEOUT_MS = 12000L;
    private static final long MAX_RETRY_DELAY_MS = 15000L;
    private static final String PREFS = "player_prefs";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Channel> channels = new ArrayList<>();
    private final List<TextView> channelRows = new ArrayList<>();
    private SharedPreferences prefs;
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout mainFrame, videoFrame;
    private LinearLayout sidebar, channelList, controlBar;
    private ScrollView channelScroll;
    private TextView stateLabel, playlistButton, retryAttemptsValue, retryDelayValue, fullButton;
    private ProgressBar spinner;
    private Button previous, next, toggleList, fullscreenButton, retryButton;
    private Switch autoSwitch, retrySwitch, fullscreenSwitch;
    private View settingsOverlay;

    private int index = -1;
    private int failureCount = 0;
    private int retryCount = 0;
    private long generation = 0;
    private boolean ready = false;
    private boolean showList = true;
    private boolean fullscreen = false;
    private boolean listBeforeFullscreen = true;
    private boolean autoSkip = false;
    private boolean retryOnFailure = true;
    private int maxRetryAttempts = 3;
    private int retryDelaySeconds = 3;
    private Runnable timeout;
    private Runnable rebufferTimeout;
    private Runnable pendingRetry;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showList = prefs.getBoolean("show_channels", true);
        fullscreen = prefs.getBoolean("fullscreen", false);
        autoSkip = prefs.getBoolean("auto_skip", false);
        retryOnFailure = prefs.getBoolean("retry_failure", true);
        maxRetryAttempts = prefs.getInt("retry_attempts", 3);
        retryDelaySeconds = prefs.getInt("retry_delay", 3);
        listBeforeFullscreen = showList;
        if (fullscreen) showList = false;
        buildUi();
        applyFullscreen();
        restorePlaylist();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable outline(int color, int border, int radius) {
        GradientDrawable d = bg(color, radius); d.setStroke(dp(1), border); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private TextView bold(String value, int size, int color) {
        TextView t = text(value,size,color); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private Button action(String caption) {
        Button b = new Button(this); b.setText(caption); b.setAllCaps(false);
        b.setTextSize(13); b.setTextColor(Color.WHITE); b.setBackground(outline(Color.rgb(8, 12, 18), ORANGE, 11));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(6), 0, dp(6), 0);
        return b;
    }
    private void buildUi() {
        mainFrame = new FrameLayout(this); mainFrame.setBackgroundColor(Color.BLACK);
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL);
        mainFrame.addView(screen, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14),0,dp(14),0); top.setBackgroundColor(Color.rgb(4, 9, 16));
        TextView logo = bold("▣", 29, ORANGE);
        top.addView(logo, new LinearLayout.LayoutParams(dp(42),-1));
        TextView title = bold("Simple M3U TV", 23, Color.WHITE);
        top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        TextView settings = text("⚙  Settings",15,Color.WHITE);
        settings.setGravity(Gravity.CENTER); settings.setPadding(dp(10),0,dp(10),0);
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings,new LinearLayout.LayoutParams(-2,-1));
        TextView sep = text("│",20,DIVIDER); top.addView(sep);
        fullButton = text("⛶  Fullscreen",15,Color.WHITE);
        fullButton.setGravity(Gravity.CENTER);
        fullButton.setPadding(dp(10),0,dp(10),0);
        fullButton.setOnClickListener(v -> toggleFullscreen());
        top.addView(fullButton,new LinearLayout.LayoutParams(-2,-1));
        TextClock clock = new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE); clock.setTextSize(16); clock.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        clock.setPadding(dp(12),0,0,0); top.addView(clock);
        screen.addView(top,new LinearLayout.LayoutParams(-1,dp(57)));

        LinearLayout center = new LinearLayout(this); center.setOrientation(LinearLayout.HORIZONTAL);
        videoFrame = new FrameLayout(this); videoFrame.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this); playerView.setUseController(false); playerView.setKeepContentOnPlayerReset(true);
        videoFrame.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        spinner = new ProgressBar(this);
        videoFrame.addView(spinner,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
        spinner.setVisibility(View.GONE);
        videoFrame.setOnClickListener(v -> {
            if (settingsOverlay != null) dismissSettings();
        });
        center.addView(videoFrame,new LinearLayout.LayoutParams(0,-1,1));

        sidebar = new LinearLayout(this); sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(PANEL);
        playlistButton = bold("Change playlist",12,ORANGE);
        playlistButton.setPadding(dp(12),0,dp(6),0);
        playlistButton.setBackgroundColor(PANEL_LIGHT);
        playlistButton.setOnClickListener(v -> pickPlaylist());
        sidebar.addView(playlistButton,new LinearLayout.LayoutParams(-1,dp(42)));
        View divider = new View(this); divider.setBackgroundColor(DIVIDER);
        sidebar.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        channelScroll = new ScrollView(this); channelScroll.setFillViewport(true);
        channelList = new LinearLayout(this); channelList.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelList);
        sidebar.addView(channelScroll,new LinearLayout.LayoutParams(-1,0,1));
        center.addView(sidebar,new LinearLayout.LayoutParams(dp(255),-1));
        screen.addView(center,new LinearLayout.LayoutParams(-1,0,1));

        controlBar = new LinearLayout(this); controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL); controlBar.setPadding(dp(12),dp(7),dp(12),dp(7));
        controlBar.setBackgroundColor(Color.rgb(10, 15, 23));
        previous = action("◀  Previous");
        toggleList = action("☷  Channels");
        next = action("Next  ▶");
        fullscreenButton = action("⛶  Fullscreen");
        retryButton = action("↻  Retry");
        addControl(previous, 1.0f);
        addControl(toggleList, 1.1f);
        addControl(next, 1.0f);
        LinearLayout status = new LinearLayout(this); status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(12),0,dp(4),0);
        TextView dot = bold("●",23,ORANGE); status.addView(dot,new LinearLayout.LayoutParams(dp(26),-1));
        stateLabel = text("Select an M3U playlist",12,Color.WHITE);
        stateLabel.setMaxLines(2); stateLabel.setEllipsize(TextUtils.TruncateAt.END);
        status.addView(stateLabel,new LinearLayout.LayoutParams(0,-1,1));
        controlBar.addView(status,new LinearLayout.LayoutParams(0,-1,1.8f));
        addControl(fullscreenButton, 1.15f);
        addControl(retryButton, 1.0f);
        screen.addView(controlBar,new LinearLayout.LayoutParams(-1,dp(64)));

        previous.setOnClickListener(v -> move(-1));
        next.setOnClickListener(v -> move(1));
        toggleList.setOnClickListener(v -> setListVisible(!showList));
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        retryButton.setOnClickListener(v -> { if(index >= 0) selectChannel(index,true); });
        setContentView(mainFrame);
        updateSidebarVisibility(); updateButtons();
    }
    private void addControl(Button b, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,dp(43),weight);
        p.setMargins(dp(4),0,dp(4),0); controlBar.addView(b,p);
    }
    private void showSettings() {
        if (settingsOverlay != null) return;
        FrameLayout shade = new FrameLayout(this); shade.setBackgroundColor(0x98000000);
        shade.setOnClickListener(v -> dismissSettings());
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        scroll.setBackground(outline(PANEL, DIVIDER, 14));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(380),-1,Gravity.LEFT | Gravity.CENTER_VERTICAL);
        sp.setMargins(dp(20),dp(12),0,dp(12));
        shade.addView(scroll,sp);
        scroll.setOnClickListener(v -> {});
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(12),dp(20),dp(16)); scroll.addView(content);
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(bold("⚙  Settings",23,Color.WHITE),new LinearLayout.LayoutParams(0,dp(44),1));
        TextView close = bold("×",28,Color.WHITE); close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismissSettings()); heading.addView(close,new LinearLayout.LayoutParams(dp(36),dp(44)));
        content.addView(heading); addRule(content);
        autoSwitch = createSwitch("Auto Next Channel", autoSkip, content);
        addHint(content,"Automatically switch to the next channel after retry attempts are exhausted.");
        autoSwitch.setOnCheckedChangeListener((button, enabled) -> {
            autoSkip = enabled; prefs.edit().putBoolean("auto_skip",enabled).apply();
        });
        retrySwitch = createSwitch("Retry on Failure", retryOnFailure, content);
        addHint(content,"Reconnect to the current channel after a stream error or stall.");
        retrySwitch.setOnCheckedChangeListener((button, enabled) -> {
            retryOnFailure = enabled; prefs.edit().putBoolean("retry_failure",enabled).apply();
        });
        retryAttemptsValue = settingCounter(content,"Retry Attempts", maxRetryAttempts, false,
                () -> { maxRetryAttempts = Math.max(1, maxRetryAttempts - 1); prefs.edit().putInt("retry_attempts",maxRetryAttempts).apply(); updateCounters(); },
                () -> { maxRetryAttempts = Math.min(10, maxRetryAttempts + 1); prefs.edit().putInt("retry_attempts",maxRetryAttempts).apply(); updateCounters(); });
        retryDelayValue = settingCounter(content,"Retry Delay", retryDelaySeconds, true,
                () -> { retryDelaySeconds = Math.max(1,retryDelaySeconds - 1); prefs.edit().putInt("retry_delay",retryDelaySeconds).apply(); updateCounters(); },
                () -> { retryDelaySeconds = Math.min(15,retryDelaySeconds + 1); prefs.edit().putInt("retry_delay",retryDelaySeconds).apply(); updateCounters(); });
        addRule(content);
        fullscreenSwitch = createSwitch("Fullscreen Mode", fullscreen, content);
        addHint(content,"Hide Android system bars and optionally the channel list.");
        fullscreenSwitch.setOnCheckedChangeListener((button,enabled) -> { if (fullscreen != enabled) toggleFullscreen(); });
        addRule(content);
        TextView about = text("About  ·  Simple M3U TV v1.4.0\nAndroid 6.0+  ·  Powered by Media3",13,MUTED);
        about.setPadding(0,dp(14),0,dp(14)); content.addView(about);
        mainFrame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        settingsOverlay = shade;
    }
    private Switch createSwitch(String name, boolean selected, LinearLayout parent) {
        Switch sw = new Switch(this); sw.setText(name); sw.setTextColor(Color.WHITE);
        sw.setTextSize(16); sw.setChecked(selected); sw.setPadding(0,dp(10),0,dp(4));
        if (android.os.Build.VERSION.SDK_INT >= 21) sw.setThumbTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        parent.addView(sw,new LinearLayout.LayoutParams(-1,dp(49))); return sw;
    }
    private void addHint(LinearLayout parent,String message) {
        TextView hint = text(message,12,MUTED); hint.setPadding(0,0,dp(12),dp(10));
        parent.addView(hint);
    }
    private void addRule(LinearLayout parent) {
        View line = new View(this); line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(1)); lp.setMargins(0,dp(10),0,dp(10)); parent.addView(line,lp);
    }
    private TextView settingCounter(LinearLayout parent,String label,int number,boolean seconds,Runnable minus,Runnable plus) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label,15,Color.WHITE); line.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));
        Button down = action("‹"); line.addView(down,new LinearLayout.LayoutParams(dp(39),dp(39)));
        TextView value = bold(number + (seconds ? "s" : ""),17,Color.WHITE); value.setGravity(Gravity.CENTER);
        line.addView(value,new LinearLayout.LayoutParams(dp(50),dp(43)));
        Button up = action("›"); line.addView(up,new LinearLayout.LayoutParams(dp(39),dp(39)));
        down.setOnClickListener(v -> minus.run()); up.setOnClickListener(v -> plus.run());
        parent.addView(line); return value;
    }
    private void updateCounters() {
        if (retryAttemptsValue != null) retryAttemptsValue.setText(String.valueOf(maxRetryAttempts));
        if (retryDelayValue != null) retryDelayValue.setText(retryDelaySeconds + "s");
    }
    private void dismissSettings() {
        if (settingsOverlay != null) { mainFrame.removeView(settingsOverlay); settingsOverlay = null; }
    }
    private void setListVisible(boolean enabled) {
        showList = enabled;
        prefs.edit().putBoolean("show_channels",showList).apply(); updateSidebarVisibility();
    }
    private void updateSidebarVisibility() { if (sidebar != null) sidebar.setVisibility(showList ? View.VISIBLE : View.GONE); }
    private void toggleFullscreen() {
        if (!fullscreen) { listBeforeFullscreen = showList; fullscreen = true; showList = false; }
        else { fullscreen = false; showList = listBeforeFullscreen; }
        prefs.edit().putBoolean("fullscreen",fullscreen).apply();
        if (fullscreenSwitch != null) fullscreenSwitch.setChecked(fullscreen);
        updateSidebarVisibility(); applyFullscreen();
    }
    private void applyFullscreen() {
        if (fullscreen) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused) applyFullscreen(); }

    private void pickPlaylist() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_M3U);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_M3U || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        if (loadPlaylist(uri)) {
            prefs.edit().putString("playlist_uri", uri.toString()).putInt("channel_index", 0).apply();
            retryCount = 0;
            selectChannel(0, true);
        }
    }

    private boolean loadPlaylist(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Cannot open the selected file");
            List<Channel> result = M3uParser.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
            if (result.isEmpty()) throw new IllegalStateException("No supported HTTP channels found");
            ++generation;
            cancelTimeouts();
            releasePlayer();
            channels.clear();
            channels.addAll(result);
            index = -1;
            retryCount = 0;
            failureCount = 0;
            playlistButton.setText("Change playlist (" + channels.size() + " channels)");
            populateChannels();
            updateButtons();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "M3U: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void restorePlaylist() {
        String value = prefs.getString("playlist_uri", null);
        if (value != null && loadPlaylist(Uri.parse(value))) {
            int saved = prefs.getInt("channel_index", 0);
            selectChannel(Math.min(Math.max(saved, 0), channels.size() - 1), true);
        }
    }

    private void populateChannels() {
        channelList.removeAllViews();
        channelRows.clear();
        for (int i = 0; i < channels.size(); i++) {
            final int at = i;
            TextView t = text((i + 1) + ". " + channels.get(i).name, 12, Color.rgb(215, 215, 215));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(12), 0, dp(8), 0);
            channelList.addView(t, new LinearLayout.LayoutParams(-1, dp(39)));
            t.setOnClickListener(v -> {
                retryCount = 0;
                selectChannel(at, true);
            });
            channelRows.add(t);
        }
        highlightSelected();
    }

    private void highlightSelected() {
        for (int i = 0; i < channelRows.size(); i++) {
            TextView t = channelRows.get(i);
            boolean selected = i == index;
            t.setBackgroundColor(selected ? ORANGE : PANEL);
            t.setTextColor(selected ? Color.BLACK : Color.rgb(215, 215, 215));
            t.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (index >= 0 && index < channelRows.size()) {
            View target = channelRows.get(index);
            channelScroll.post(() -> {
                int y = target.getTop();
                int scrollY = channelScroll.getScrollY();
                int viewport = channelScroll.getHeight();
                if (y < scrollY || y + target.getHeight() > scrollY + viewport) {
                    channelScroll.smoothScrollTo(0, Math.max(0, y - viewport / 3));
                }
            });
        }
    }

    private void updateButtons() {
        boolean available = !channels.isEmpty();
        previous.setEnabled(available);
        next.setEnabled(available);
        retryButton.setEnabled(available);
    }

    private void move(int delta) {
        if (channels.isEmpty()) return;
        retryCount = 0;
        selectChannel((Math.max(index, 0) + delta + channels.size()) % channels.size(), true);
    }

    private void selectChannel(int newIndex, boolean manual) {
        if (newIndex < 0 || newIndex >= channels.size()) return;
        if (manual) {
            failureCount = 0;
            retryCount = 0;
        }
        index = newIndex;
        startPlaybackForCurrentIndex();
    }

    private void startPlaybackForCurrentIndex() {
        if (index < 0 || index >= channels.size()) return;
        ready = false;
        ++generation;
        final long serial = generation;
        cancelTimeouts();
        releasePlayer();
        prefs.edit().putInt("channel_index", index).apply();
        Channel ch = channels.get(index);
        spinner.setVisibility(View.VISIBLE);
        stateLabel.setText(retryCount > 0 ? ch.name + " · reconnecting (attempt " + (retryCount + 1) + ")" : ch.name + " · loading...");
        highlightSelected();
        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(3000)
                    .setReadTimeoutMs(3000)
                    .setAllowCrossProtocolRedirects(true);
            if (ch.userAgent != null && !ch.userAgent.isEmpty()) {
                http.setUserAgent(ch.userAgent);
            }
            if (ch.referer != null && !ch.referer.isEmpty()) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Referer", ch.referer);
                http.setDefaultRequestProperties(headers);
            }
            DefaultDataSource.Factory ds = new DefaultDataSource.Factory(this, http);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(ds))
                    .build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (serial != generation) return;
                    if (state == Player.STATE_READY) {
                        ready = true;
                        retryCount = 0;
                        failureCount = 0;
                        cancelTimeouts();
                        spinner.setVisibility(View.GONE);
                        stateLabel.setText(ch.name);
                    } else if (state == Player.STATE_BUFFERING && ready) {
                        spinner.setVisibility(View.VISIBLE);
                        stateLabel.setText(ch.name + " · buffering...");
                        scheduleRebufferTimeout(serial);
                    } else if (state == Player.STATE_ENDED) {
                        handleFailure(serial, "Stream ended");
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    handleFailure(serial, "Stream error");
                }
            });

            MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(ch.url));
            String path = Uri.parse(ch.url).getPath();
            if (path != null && path.toLowerCase(Locale.ROOT).endsWith(".m3u8")) {
                item.setMimeType(MimeTypes.APPLICATION_M3U8);
            }
            player.setMediaItem(item.build());
            player.prepare();
            player.play();
            timeout = () -> {
                if (serial == generation && !ready) handleFailure(serial, "Connection timed out");
            };
            handler.postDelayed(timeout, LOAD_TIMEOUT_MS);
        } catch (Exception error) {
            handleFailure(serial, "Cannot open stream");
        }
    }

    private void scheduleRebufferTimeout(long serial) {
        cancelRebufferTimeout();
        rebufferTimeout = () -> {
            if (serial == generation) {
                handleFailure(serial, "Stream stalled");
            }
        };
        handler.postDelayed(rebufferTimeout, REBUFFER_TIMEOUT_MS);
    }

    private void handleFailure(long serial, String reason) {
        if (serial != generation || channels.isEmpty()) return;
        ++generation; cancelTimeouts(); spinner.setVisibility(View.GONE); releasePlayer();
        if (retryOnFailure && (!autoSkip || retryCount < maxRetryAttempts)) {
            retryCount = Math.min(1000, retryCount + 1);
            long delay = Math.min(MAX_RETRY_DELAY_MS, retryDelaySeconds * 1000L * (1L << Math.min(retryCount - 1, 3)));
            stateLabel.setText("Reconnecting in " + (delay / 1000) + "s (attempt " + retryCount + ")");
            final long current = generation;
            pendingRetry = () -> { if (current == generation && index >= 0) startPlaybackForCurrentIndex(); };
            handler.postDelayed(pendingRetry,delay);
            return;
        }
        if (autoSkip) {
            retryCount = 0;
            if (++failureCount >= channels.size()) {
                stateLabel.setText("No playable channels · tap Retry");
                Toast.makeText(this,"No playable channels found",Toast.LENGTH_LONG).show();
                return;
            }
            stateLabel.setText("Switching to next channel...");
            handler.post(() -> selectChannel((index + 1) % channels.size(), false));
        } else stateLabel.setText(reason + " · tap Retry");
    }

    private void cancelRebufferTimeout() {
        if (rebufferTimeout != null) handler.removeCallbacks(rebufferTimeout);
        rebufferTimeout = null;
    }

    private void cancelTimeouts() {
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        cancelRebufferTimeout();
        if (pendingRetry != null) handler.removeCallbacks(pendingRetry);
        pendingRetry = null;
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (settingsOverlay != null) { dismissSettings(); return; }
        if (showList) {
            setListVisible(false);
            return;
        }
        if (fullscreen) {
            toggleFullscreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        ++generation;
        cancelTimeouts();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
