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
import android.view.KeyEvent;
import android.content.res.Configuration;
import android.app.UiModeManager;
import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.widget.HorizontalScrollView;
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
    private static final int ACCENT = Color.rgb(44, 168, 255);
    private static final int PANEL = Color.rgb(12, 24, 40);
    private static final int PANEL_LIGHT = Color.rgb(20, 38, 62);
    private static final int MUTED = Color.rgb(174, 194, 220);
    private static final int DIVIDER = Color.rgb(36, 64, 98);
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
    private LinearLayout sidebar, channelList, controlBar, buttonsRow, topBar;
    private ScrollView channelScroll;
    private TextView stateLabel, playlistButton, retryAttemptsValue, retryDelayValue, fullButton;
    private ProgressBar spinner;
    private Button previous, next, toggleList, fullscreenButton, retryButton;
    private Switch autoSwitch, retrySwitch, fullscreenSwitch;
    private View settingsOverlay;
    private View settingsTrigger;
    private View topFullscreenTrigger;
    private boolean compactUi;
    private boolean televisionUi;

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
    private Runnable hideControlsRunnable;
    private boolean controlsVisible = true;
    private static final long AUTO_HIDE_MS = 3000L;

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
        UiModeManager mode = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        televisionUi = mode != null && mode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        compactUi = getResources().getConfiguration().screenWidthDp < 820;
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
        b.setTextSize(13); b.setTextColor(Color.WHITE); b.setBackground(outline(Color.rgb(8, 18, 30), ACCENT, 8));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(8), dp(4), dp(8), dp(4));
        b.setSingleLine(true); b.setEllipsize(TextUtils.TruncateAt.END);
        focusStyle(b, Color.rgb(8, 12, 18), ACCENT);
        return b;
    }
    private void focusStyle(View v, int regular, int focused) {
        GradientDrawable normal = outline(regular, ACCENT, 8);
        GradientDrawable highlight = outline(focused, Color.WHITE, 8);
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused}, highlight);
        state.addState(new int[]{android.R.attr.state_pressed}, highlight);
        state.addState(new int[]{}, normal);
        v.setBackground(state);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        if (v instanceof TextView) {
            TextView text = (TextView)v;
            text.setOnFocusChangeListener((view, hasFocus) -> text.setTextColor(Color.WHITE));
        }
    }
    private void focusHeader(TextView v) {
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(6), dp(4), dp(6), dp(4));
        focusStyle(v, Color.rgb(6, 14, 26), ACCENT);
    }
    private void pingControls() {
        showControlsTemporarily();
    }

    private void showControlsTemporarily() {
        controlsVisible = true;
        if (topBar != null) topBar.setVisibility(View.VISIBLE);
        if (controlBar != null) controlBar.setVisibility(View.VISIBLE);
        if (!fullscreen && sidebar != null) sidebar.setVisibility(showList ? View.VISIBLE : View.GONE);
        scheduleAutoHide();
    }

    private void scheduleAutoHide() {
        if (hideControlsRunnable != null) handler.removeCallbacks(hideControlsRunnable);
        hideControlsRunnable = this::hideControlsIfIdle;
        handler.postDelayed(hideControlsRunnable, AUTO_HIDE_MS);
    }

    private void hideControlsIfIdle() {
        controlsVisible = false;
        if (settingsOverlay != null) return;
        if (topBar != null) topBar.setVisibility(View.GONE);
        if (controlBar != null) controlBar.setVisibility(View.GONE);
        if (fullscreen && sidebar != null) sidebar.setVisibility(View.GONE);
    }

    private void toggleControlsVisibility() {
        if (controlsVisible) {
            if (hideControlsRunnable != null) handler.removeCallbacks(hideControlsRunnable);
            hideControlsIfIdle();
        } else {
            showControlsTemporarily();
        }
    }

    private void status(TextView dot) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(2), dp(8), dp(2));
        bar.addView(dot, new LinearLayout.LayoutParams(dp(24), -1));
        stateLabel = text("Select an M3U playlist", compactUi ? 12 : 13, Color.WHITE);
        stateLabel.setMaxLines(2);
        stateLabel.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(stateLabel, new LinearLayout.LayoutParams(0, -1, 1));
        if (compactUi) controlBar.addView(bar, new LinearLayout.LayoutParams(-1, dp(32)));
        else controlBar.addView(bar, new LinearLayout.LayoutParams(0, -1, 1.5f));
    }
    private void buildUi() {
        mainFrame = new FrameLayout(this); mainFrame.setBackgroundColor(Color.BLACK);
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL);
        mainFrame.addView(screen, new FrameLayout.LayoutParams(-1,-1));

        topBar = new LinearLayout(this); topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(3), dp(8), dp(3)); topBar.setBackgroundColor(Color.rgb(6, 14, 26));
        TextView logo = bold("▣", compactUi ? 19 : 27, ACCENT);
        logo.setGravity(Gravity.CENTER);
        topBar.addView(logo, new LinearLayout.LayoutParams(dp(compactUi ? 25 : 42),-1));
        TextView title = bold(compactUi ? "M3U TV" : "Simple M3U TV",compactUi ? 16 : 23,Color.WHITE);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        topBar.addView(title, new LinearLayout.LayoutParams(0,-1,1));
        TextView settings = bold("⚙", compactUi ? 22 : 18, Color.WHITE);
        focusHeader(settings);
        settings.setContentDescription("Settings");
        settings.setOnClickListener(v -> { pingControls(); showSettings(); });
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(compactUi ? 44 : 46), dp(compactUi ? 40 : 46)); settingsLp.setMargins(dp(6),0,dp(6),0); topBar.addView(settings, settingsLp);
        settingsTrigger = settings;
        TextView full = bold("⛶", compactUi ? 22 : 18, Color.WHITE);
        focusHeader(full);
        full.setContentDescription("Toggle fullscreen");
        full.setOnClickListener(v -> { pingControls(); toggleFullscreen(); });
        LinearLayout.LayoutParams fullLp = new LinearLayout.LayoutParams(dp(compactUi ? 44 : 46), dp(compactUi ? 40 : 46)); fullLp.setMargins(dp(6),0,dp(6),0); topBar.addView(full, fullLp);
        topFullscreenTrigger = full;
        if (!compactUi) {
            TextClock clock = new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
            clock.setTextColor(Color.WHITE); clock.setTextSize(16);
            clock.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            clock.setPadding(dp(10),0,0,0); topBar.addView(clock);
        }
        screen.addView(topBar, new LinearLayout.LayoutParams(-1,dp(compactUi ? 48 : 64)));

        LinearLayout center = new LinearLayout(this); center.setOrientation(LinearLayout.HORIZONTAL);
        videoFrame = new FrameLayout(this); videoFrame.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this); playerView.setUseController(false); playerView.setKeepContentOnPlayerReset(true);
        playerView.setFocusable(false);
        playerView.setOnClickListener(v -> { if (settingsOverlay != null) dismissSettings(); else toggleControlsVisibility(); });
        videoFrame.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        spinner = new ProgressBar(this);
        videoFrame.addView(spinner,new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
        spinner.setVisibility(View.GONE);
        videoFrame.setOnClickListener(v -> { if (settingsOverlay != null) dismissSettings(); else toggleControlsVisibility(); });
        center.addView(videoFrame,new LinearLayout.LayoutParams(0,-1,1));

        sidebar = new LinearLayout(this); sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(PANEL);
        playlistButton = bold("Change playlist",compactUi ? 13 : 14,ACCENT);
        playlistButton.setPadding(dp(10),dp(4),dp(6),dp(4));
        playlistButton.setGravity(Gravity.CENTER_VERTICAL);
        playlistButton.setSingleLine(true); playlistButton.setEllipsize(TextUtils.TruncateAt.END);
        focusStyle(playlistButton,PANEL_LIGHT,ACCENT);
        playlistButton.setOnClickListener(v -> pickPlaylist());
        sidebar.addView(playlistButton,new LinearLayout.LayoutParams(-1,dp(48)));
        View divider = new View(this); divider.setBackgroundColor(DIVIDER);
        sidebar.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        channelScroll = new ScrollView(this); channelScroll.setFillViewport(true);
        channelScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        channelList = new LinearLayout(this); channelList.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelList);
        sidebar.addView(channelScroll,new LinearLayout.LayoutParams(-1,0,1));
        int sidebarWidth = compactUi ? Math.max(dp(150), getResources().getDisplayMetrics().widthPixels * 44 / 100) : dp(275);
        center.addView(sidebar,new LinearLayout.LayoutParams(sidebarWidth,-1));
        screen.addView(center,new LinearLayout.LayoutParams(-1,0,1));

        controlBar = new LinearLayout(this);
        controlBar.setOrientation(compactUi ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(6),dp(4),dp(6),dp(4));
        controlBar.setBackgroundColor(Color.rgb(7, 16, 28));
        previous = action("◀");
        toggleList = action("☷");
        next = action("▶");
        fullscreenButton = action("⛶");
        retryButton = action("↻");
        previous.setContentDescription("Previous channel");
        toggleList.setContentDescription("Show or hide channels");
        next.setContentDescription("Next channel");
        fullscreenButton.setContentDescription("Toggle fullscreen");
        retryButton.setContentDescription("Retry stream");
        TextView dot = bold("●",20,ACCENT);
        if (compactUi) status(dot);
        if (compactUi) {
            HorizontalScrollView hscroll = new HorizontalScrollView(this);
            hscroll.setHorizontalScrollBarEnabled(false); hscroll.setFillViewport(true);
            buttonsRow = new LinearLayout(this);
            buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonsRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            hscroll.addView(buttonsRow,new android.widget.FrameLayout.LayoutParams(-2,dp(49)));
            controlBar.addView(hscroll,new LinearLayout.LayoutParams(-1,dp(50)));
        } else {
            buttonsRow = controlBar;
        }
        addControl(previous, 1f);
        addControl(toggleList, 1.1f);
        addControl(next, 1f);
        if (!compactUi) status(dot);
        addControl(fullscreenButton, 1.1f);
        addControl(retryButton, 0.9f);
        screen.addView(controlBar,new LinearLayout.LayoutParams(-1,dp(compactUi ? 91 : 72)));
        controlBar.bringToFront();

        previous.setOnClickListener(v -> { pingControls(); move(-1); });
        next.setOnClickListener(v -> { pingControls(); move(1); });
        toggleList.setOnClickListener(v -> { pingControls(); setListVisible(!showList); if(showList && !channelRows.isEmpty()) channelRows.get(Math.max(0,index)).requestFocus(); });
        fullscreenButton.setOnClickListener(v -> { pingControls(); toggleFullscreen(); });
        retryButton.setOnClickListener(v -> { pingControls(); if(index >= 0) selectChannel(index,true); });
        setContentView(mainFrame);
        updateSidebarVisibility(); updateButtons();
        showControlsTemporarily();
        if (televisionUi) toggleList.requestFocus();
    }
    private void addControl(Button b, float weight) {
        LinearLayout.LayoutParams p;
        if (compactUi) {
            p = new LinearLayout.LayoutParams(dp(54),dp(46));
        } else {
            p = new LinearLayout.LayoutParams(dp(72),dp(54));
        }
        p.setMargins(dp(3),0,dp(3),0);
        buttonsRow.addView(b,p);
    }
    private void showSettings() {
        if (settingsOverlay != null) return;
        FrameLayout shade = new FrameLayout(this); shade.setBackgroundColor(0x98000000);
        shade.setOnClickListener(v -> { dismissSettings(); showControlsTemporarily(); });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        scroll.setBackground(outline(PANEL, DIVIDER, 14));
        int available = getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(dp(380), Math.max(dp(220), available - dp(24)));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(popupWidth,-1,Gravity.LEFT | Gravity.CENTER_VERTICAL);
        sp.setMargins(dp(12),dp(8),0,dp(8));
        shade.addView(scroll,sp);
        scroll.setOnClickListener(v -> {});
        scroll.setFocusable(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(12),dp(20),dp(16)); scroll.addView(content);
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(bold("⚙  Settings",23,Color.WHITE),new LinearLayout.LayoutParams(0,dp(44),1));
        TextView close = bold("×",28,Color.WHITE); close.setGravity(Gravity.CENTER);
        focusHeader(close);
        close.setOnClickListener(v -> { dismissSettings(); settingsTrigger.requestFocus(); }); heading.addView(close,new LinearLayout.LayoutParams(dp(36),dp(44)));
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
        TextView about = text("About  ·  Simple M3U TV v1.6.0\nAndroid 6.0+  ·  Powered by Media3",13,MUTED);
        about.setPadding(0,dp(14),0,dp(14)); content.addView(about);
        mainFrame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        settingsOverlay = shade;
        if (televisionUi) close.requestFocus();
    }
    private Switch createSwitch(String name, boolean selected, LinearLayout parent) {
        Switch sw = new Switch(this); sw.setText(name); sw.setTextColor(Color.WHITE);
        sw.setTextSize(16); sw.setChecked(selected); sw.setPadding(0,dp(10),0,dp(4));
        if (android.os.Build.VERSION.SDK_INT >= 21) sw.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        sw.setFocusable(true); sw.setMinimumHeight(dp(52));
        parent.addView(sw,new LinearLayout.LayoutParams(-1,-2)); return sw;
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
        TextView name = text(label,15,Color.WHITE); line.addView(name,new LinearLayout.LayoutParams(0,dp(56),1));
        Button down = action("‹"); line.addView(down,new LinearLayout.LayoutParams(dp(39),dp(39)));
        TextView value = bold(number + (seconds ? "s" : ""),17,Color.WHITE); value.setGravity(Gravity.CENTER);
        line.addView(value,new LinearLayout.LayoutParams(dp(46),dp(52)));
        Button up = action("›"); line.addView(up,new LinearLayout.LayoutParams(dp(39),dp(39)));
        down.setOnClickListener(v -> minus.run()); up.setOnClickListener(v -> plus.run());
        parent.addView(line,new LinearLayout.LayoutParams(-1,dp(58))); return value;
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
        pingControls();
    }
    private void updateSidebarVisibility() {
        if (sidebar == null) return;
        boolean visible = showList && !fullscreen;
        sidebar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    private void toggleFullscreen() {
        if (!fullscreen) { listBeforeFullscreen = showList; fullscreen = true; showList = false; }
        else { fullscreen = false; showList = listBeforeFullscreen; }
        prefs.edit().putBoolean("fullscreen",fullscreen).apply();
        if (fullscreenSwitch != null) fullscreenSwitch.setChecked(fullscreen);
        controlsVisible = true; updateSidebarVisibility(); applyFullscreen(); showControlsTemporarily();
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
            t.setFocusable(true);
            t.setFocusableInTouchMode(false);
            t.setOnFocusChangeListener((v, focused) -> highlightSelected());
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(12), 0, dp(8), 0);
            channelList.addView(t, new LinearLayout.LayoutParams(-1, dp(52)));
            t.setOnClickListener(v -> {
                pingControls();
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
            boolean focused = t.hasFocus();
            t.setBackground(outline(selected ? ACCENT : (focused ? PANEL_LIGHT : PANEL), focused ? ACCENT : (selected ? ACCENT : PANEL), 3));
            t.setTextColor(selected ? Color.BLACK : (focused ? Color.WHITE : Color.rgb(215, 215, 215)));
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) pingControls();
        if (televisionUi && event.getAction() == KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_MEDIA_NEXT || key == KeyEvent.KEYCODE_CHANNEL_UP) {
                move(1); return true;
            }
            if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS || key == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                move(-1); return true;
            }
            if (key == KeyEvent.KEYCODE_MENU && settingsOverlay == null) {
                showSettings(); return true;
            }
            if ((key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_LEFT)
                    && settingsOverlay == null && showList && channelRows.size() > 0
                    && (getCurrentFocus() == null || getCurrentFocus() == playerView)) {
                channelRows.get(Math.max(0,index)).requestFocus(); return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (settingsOverlay != null) { dismissSettings(); showControlsTemporarily(); return; }
        if (showList) {
            setListVisible(false);
            if (televisionUi) toggleList.requestFocus();
            return;
        }
        if (fullscreen) {
            toggleFullscreen();
            return;
        }
        if (televisionUi) { toggleList.requestFocus(); return; }
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
