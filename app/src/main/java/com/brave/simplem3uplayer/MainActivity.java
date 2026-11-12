package com.brave.simplem3uplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.widget.EditText;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final int READ_FILE_PERMISSION = 1002;
    private static final int ACCENT = Color.rgb(72, 152, 235);
    private static final int ACTIVE = Color.rgb(255, 163, 0);
    private static final int BACKGROUND = Color.rgb(10, 14, 23); // #0a0e17
    private static final int SURFACE = Color.rgb(13, 13, 13); // #0d0d0d
    private static final int PANEL = SURFACE;
    private static final int PANEL_LIGHT = Color.rgb(28, 28, 28);
    private static final int MUTED = Color.rgb(174, 194, 220);
    private static final int DIVIDER = Color.rgb(52, 52, 52);
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
    private LinearLayout statusBar;
    private ProgressBar spinner;
    private Button previous, next, toggleList, fullscreenButton, retryButton, settingsButton, openButton;
    private Switch autoSwitch, retrySwitch, fullscreenSwitch, statusSwitch;
    private View settingsOverlay;
    private View settingsTrigger;
    private View topFullscreenTrigger;
    private AlertDialog fileDialog;
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
    private boolean statusEnabled = true;
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
        statusEnabled = prefs.getBoolean("status_enabled", true);
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
        GradientDrawable d = bg(color, radius); d.setStroke(2, border); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private TextView bold(String value, int size, int color) {
        TextView t = text(value,size,color); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private Button action(String caption) {
        Button b = new Button(this);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextSize(22);
        b.setIncludeFontPadding(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setSingleLine(true);
        focusStyle(b, SURFACE, ACCENT);
        return b;
    }
    private void focusStyle(View v, int regular, int focused) {
        GradientDrawable normal = outline(regular, ACCENT, 6);
        GradientDrawable highlight = outline(focused, Color.WHITE, 6);
        StateListDrawable state = new StateListDrawable();
        state.addState(new int[]{android.R.attr.state_focused}, highlight);
        state.addState(new int[]{android.R.attr.state_pressed}, highlight);
        state.addState(new int[]{}, normal);
        v.setBackground(state);
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        if (v instanceof TextView) ((TextView)v).setTextColor(Color.WHITE);
    }
    private void focusHeader(TextView v) {
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, 0, 0, 0);
        focusStyle(v, SURFACE, ACCENT);
    }
    private void pingControls() {
        showControlsTemporarily();
    }

    private void showControlsTemporarily() {
        controlsVisible = true;
        if (controlBar != null) controlBar.setVisibility(View.VISIBLE);
        updateSidebarVisibility();
        scheduleAutoHide();
    }

    private void scheduleAutoHide() {
        if (hideControlsRunnable != null) handler.removeCallbacks(hideControlsRunnable);
        hideControlsRunnable = this::hideControlsIfIdle;
        handler.postDelayed(hideControlsRunnable, AUTO_HIDE_MS);
    }

    private void hideControlsIfIdle() {
        if (settingsOverlay != null || fileDialog != null) return;
        controlsVisible = false;
        if (controlBar != null) controlBar.setVisibility(View.GONE);
        if (televisionUi && videoFrame != null) videoFrame.requestFocus();
    }

    private void toggleControlsVisibility() {
        if (controlsVisible) {
            if (hideControlsRunnable != null) handler.removeCallbacks(hideControlsRunnable);
            hideControlsIfIdle();
        } else showControlsTemporarily();
    }

    private void status(TextView dot) {
        statusBar = new LinearLayout(this);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(dp(10), dp(2), dp(10), dp(2));
        statusBar.setBackgroundColor(0xB8000000);
        dot.setGravity(Gravity.CENTER);
        statusBar.addView(dot, new LinearLayout.LayoutParams(dp(24), dp(30)));
        stateLabel = text("Select an M3U playlist", 13, Color.WHITE);
        stateLabel.setMaxLines(2);
        stateLabel.setEllipsize(TextUtils.TruncateAt.END);
        statusBar.addView(stateLabel, new LinearLayout.LayoutParams(0, -1, 1));
        FrameLayout.LayoutParams st = new FrameLayout.LayoutParams(-1,dp(36), Gravity.BOTTOM);
        videoFrame.addView(statusBar,st);
        updateStatusVisibility();
    }

    private void updateStatusVisibility() {
        if (statusBar != null) {
            statusBar.setVisibility(statusEnabled ? View.VISIBLE : View.GONE);
        }
    }
    private void buildUi() {
        mainFrame = new FrameLayout(this);
        mainFrame.setBackgroundColor(BACKGROUND);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        mainFrame.addView(screen, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.HORIZONTAL);
        videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(BACKGROUND);
        videoFrame.setFocusable(televisionUi);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setFocusable(false);
        videoFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this);
        videoFrame.addView(spinner, new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));
        spinner.setVisibility(View.GONE);
        status(bold("●",18,ACTIVE));
        center.addView(videoFrame, new LinearLayout.LayoutParams(0, -1, 1));

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(SURFACE);
        playlistButton = bold("Channels",compactUi ? 13 : 15,ACTIVE);
        playlistButton.setGravity(Gravity.CENTER_VERTICAL);
        playlistButton.setPadding(dp(12),dp(2),dp(8),dp(2));
        playlistButton.setSingleLine(true);
        playlistButton.setEllipsize(TextUtils.TruncateAt.END);
        playlistButton.setTextColor(ACTIVE);
        playlistButton.setFocusable(false);
        sidebar.addView(playlistButton,new LinearLayout.LayoutParams(-1,dp(48)));
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        sidebar.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelList);
        sidebar.addView(channelScroll,new LinearLayout.LayoutParams(-1,0,1));
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        int sidebarWidth = compactUi ? Math.max(dp(145), widthPx * 38 / 100) : dp(275);
        center.addView(sidebar,new LinearLayout.LayoutParams(sidebarWidth,-1));
        screen.addView(center,new LinearLayout.LayoutParams(-1,0,1));

        controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(6),dp(3),dp(6),dp(3));
        controlBar.setBackgroundColor(BACKGROUND);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        scroll.addView(buttonsRow, new android.widget.FrameLayout.LayoutParams(-2,dp(56)));
        controlBar.addView(scroll,new LinearLayout.LayoutParams(-1,dp(56)));

        previous = action("◀");
        toggleList = action("☷");
        next = action("▶");
        openButton = action("▤");
        settingsButton = action("⚙");
        fullscreenButton = action("⛶");
        retryButton = action("↻");
        previous.setContentDescription("Previous channel");
        toggleList.setContentDescription("Show or hide channels");
        next.setContentDescription("Next channel");
        openButton.setContentDescription("Open M3U playlist");
        settingsButton.setContentDescription("Settings");
        fullscreenButton.setContentDescription("Toggle fullscreen");
        retryButton.setContentDescription("Retry stream");
        addControl(previous); addControl(toggleList); addControl(next);
        addControl(openButton); addControl(settingsButton); addControl(fullscreenButton); addControl(retryButton);
        screen.addView(controlBar,new LinearLayout.LayoutParams(-1,dp(62)));
        settingsTrigger = settingsButton;
        topFullscreenTrigger = fullscreenButton;

        previous.setOnClickListener(v -> { pingControls(); move(-1); });
        next.setOnClickListener(v -> { pingControls(); move(1); });
        toggleList.setOnClickListener(v -> {
            pingControls(); setListVisible(!showList);
            if(showList && !channelRows.isEmpty()) channelRows.get(Math.max(0,index)).requestFocus();
        });
        openButton.setOnClickListener(v -> { pingControls(); pickPlaylist(); });
        settingsButton.setOnClickListener(v -> { pingControls(); showSettings(); });
        fullscreenButton.setOnClickListener(v -> { pingControls(); toggleFullscreen(); });
        retryButton.setOnClickListener(v -> { pingControls(); if(index >= 0) selectChannel(index,true); });

        setContentView(mainFrame);
        updateSidebarVisibility(); updateButtons();
        showControlsTemporarily();
        if (televisionUi) toggleList.requestFocus();
    }

    private void addControl(Button b) {
        // Fixed square icons. The horizontal scroll keeps a SINGLE row on small displays.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50),dp(50));
        lp.setMargins(dp(4),0,dp(4),0);
        buttonsRow.addView(b,lp);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && fileDialog == null && settingsOverlay == null
                && videoFrame != null) {
            Rect r = new Rect();
            if (videoFrame.getGlobalVisibleRect(r) && r.contains((int)event.getRawX(),(int)event.getRawY())) {
                toggleControlsVisibility();
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
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
        statusSwitch = createSwitch("Status Bar", statusEnabled, content);
        addHint(content,"Show or hide the bottom playback status overlay.");
        statusSwitch.setOnCheckedChangeListener((button, enabled) -> {
            statusEnabled = enabled;
            prefs.edit().putBoolean("status_enabled", enabled).apply();
            updateStatusVisibility();
        });
        addRule(content);
        TextView about = text("About  ·  Simple M3U TV v1.7.1\nAndroid 6.0+  ·  Powered by Media3",13,MUTED);
        about.setPadding(0,dp(14),0,dp(14)); content.addView(about);
        mainFrame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        settingsOverlay = shade;
        if (hideControlsRunnable != null) handler.removeCallbacks(hideControlsRunnable);
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
        showControlsTemporarily();
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
        // TV ROMs (especially Android 7) often ship a broken or absent DocumentsUI.
        // Use our D-pad-friendly browser instead of relying on ACTION_OPEN_DOCUMENT.
        if (Build.VERSION.SDK_INT <= 25 || (televisionUi && Build.VERSION.SDK_INT <= 28)) {
            openLegacyFileBrowser();
        } else {
            openSystemPicker();
        }
    }

    private void openSystemPicker() {
        try {
            Intent intent = new Intent(Build.VERSION.SDK_INT >= 19
                    ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_M3U);
        } catch (Exception error) {
            // Useful fallback when the firmware lacks a documents provider.
            if (Build.VERSION.SDK_INT <= 32) openLegacyFileBrowser();
            else Toast.makeText(this,"No compatible file manager available",Toast.LENGTH_LONG).show();
        }
    }

    private void openLegacyFileBrowser() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 32
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},READ_FILE_PERMISSION);
            return;
        }
        showStorageRoots();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode == READ_FILE_PERMISSION) {
            if(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) showStorageRoots();
            else new AlertDialog.Builder(this).setTitle("Storage access required")
                    .setMessage("Allow storage access to browse playlists on this Android TV box. You can also try the system file picker.")
                    .setPositiveButton("System picker",(d,w) -> openSystemPicker())
                    .setNegativeButton("Cancel",null).show();
        }
    }

    private void showStorageRoots() {
        LinkedHashMap<String,File> roots = new LinkedHashMap<>();
        File primary = Environment.getExternalStorageDirectory();
        if(primary != null) roots.put("Internal storage",primary);
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if(downloads != null) roots.put("Downloads",downloads);
        File storage = new File("/storage");
        File[] list = storage.listFiles();
        if(list != null) for(File root:list) {
            if(root.isDirectory() && !root.getName().equals("self") && !root.getName().equals("emulated"))
                roots.put("Storage: " + root.getName(),root);
        }
        File[] externals = getExternalFilesDirs(null);
        if(externals != null) for(File external: externals) if(external != null) {
            File parent = external;
            for(int n=0;n<4 && parent.getParentFile()!=null;n++) parent=parent.getParentFile();
            if(parent.isDirectory() && parent.canRead()) roots.put("External: " + parent.getName(),parent);
        }
        List<String> labels = new ArrayList<>(roots.keySet());
        labels.add("Use Android system file picker");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select M3U playlist — storage")
                .setItems(labels.toArray(new String[0]),(d,which)-> {
                    if(which == roots.size()) openSystemPicker();
                    else browseDirectory(new ArrayList<>(roots.values()).get(which));
                })
                .setNegativeButton("Cancel",(d,which)->showControlsTemporarily()).create();
        showBrowserDialog(dialog);
    }

    private boolean isPlaylistFile(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return f.isFile() && (n.endsWith(".m3u") || n.endsWith(".m3u8"));
    }

    private void browseDirectory(File dir) {
        if(dir == null || !dir.isDirectory() || !dir.canRead()) {
            Toast.makeText(this,"Folder is not readable",Toast.LENGTH_LONG).show();
            showStorageRoots();
            return;
        }
        File[] all = dir.listFiles();
        if(all == null) all = new File[0];
        List<File> entries = new ArrayList<>();
        for(File file:all) if(file.isDirectory() || isPlaylistFile(file)) entries.add(file);
        entries.sort((a,b)-> {
            if(a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        List<String> labels = new ArrayList<>();
        labels.add("..  Parent folder / Storage roots");
        for(File f:entries) labels.add((f.isDirectory()?"[Folder]  ":"[M3U]  ")+f.getName());
        if(entries.isEmpty()) labels.add("No M3U files in this folder");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(dir.getAbsolutePath())
                .setItems(labels.toArray(new String[0]),(d,which)-> {
                    if(which == 0) {
                        File parent=dir.getParentFile();
                        if(parent != null && parent.canRead()) browseDirectory(parent);
                        else showStorageRoots();
                    } else if(which-1 < entries.size()) {
                        File f=entries.get(which-1);
                        if(f.isDirectory()) browseDirectory(f);
                        else openPlaylistUri(Uri.fromFile(f));
                    } else browseDirectory(dir);
                })
                .setNegativeButton("Storage roots",(d,w)->showStorageRoots())
                .setNeutralButton("System picker",(d,w)->openSystemPicker()).create();
        showBrowserDialog(dialog);
    }

    private void showBrowserDialog(AlertDialog dialog) {
        if(fileDialog != null) fileDialog.dismiss();
        fileDialog=dialog;
        dialog.setOnDismissListener(d -> {
            if (fileDialog == dialog) {fileDialog=null;showControlsTemporarily();}
        });
        dialog.show();
    }

    private void openPlaylistUri(Uri uri) {
        if(loadPlaylist(uri)) {
            prefs.edit().putString("playlist_uri",uri.toString()).putInt("channel_index",0).apply();
            retryCount=0;
            selectChannel(0,true);
        }
        showControlsTemporarily();
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
        openPlaylistUri(uri);
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
            playlistButton.setText("Channels (" + channels.size() + ")");
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
            t.setBackground(outline(selected ? ACTIVE : (focused ? PANEL_LIGHT : PANEL), focused ? ACCENT : (selected ? ACTIVE : PANEL), 2));
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
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!controlsVisible && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                showControlsTemporarily(); return true;
            }
            pingControls();
        }
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
