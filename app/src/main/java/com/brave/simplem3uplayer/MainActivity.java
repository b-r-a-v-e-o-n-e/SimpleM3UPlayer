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
    private static final int PANEL = Color.rgb(19, 22, 26);
    private static final int DIVIDER = Color.rgb(40, 43, 47);
    private static final long LOAD_TIMEOUT_MS = 3200L;
    private static final long REBUFFER_TIMEOUT_MS = 8000L;
    private static final long RETRY_DELAY_MS = 1500L;
    private static final long MAX_RETRY_DELAY_MS = 15000L;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String PREFS = "player_prefs";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Channel> channels = new ArrayList<>();
    private final List<TextView> channelRows = new ArrayList<>();

    private SharedPreferences prefs;
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout videoFrame;
    private LinearLayout base, sidebar, channelList, controlBar;
    private ScrollView channelScroll;
    private TextView stateLabel, playlistButton, autoLabel;
    private ProgressBar spinner;
    private Button previous, next, toggleList, fullscreenButton, retryButton;
    private Switch autoSwitch;

    private int index = -1;
    private int failureCount = 0;
    private int retryCount = 0;
    private long generation = 0;
    private boolean ready = false;
    private boolean showList = true;
    private boolean fullscreen = false;
    private boolean listBeforeFullscreen = true;
    private boolean autoSkip = false;
    private Runnable timeout;
    private Runnable rebufferTimeout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        showList = prefs.getBoolean("show_channels", true);
        fullscreen = prefs.getBoolean("fullscreen", false);
        autoSkip = prefs.getBoolean("auto_skip", false);
        listBeforeFullscreen = showList;
        if (fullscreen) showList = false;
        buildUi();
        applyFullscreen();
        restorePlaylist();
    }

    private int dp(int size) {
        return (int) (size * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String content, int size, int color) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button action(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.BLACK);
        b.setBackground(bg(ORANGE, 5));
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(39));
        p.leftMargin = dp(5);
        return p;
    }

    private void buildUi() {
        base = new LinearLayout(this);
        base.setOrientation(LinearLayout.HORIZONTAL);
        base.setBackgroundColor(Color.BLACK);

        videoFrame = new FrameLayout(this);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        videoFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(8), dp(5), dp(8), dp(5));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM);

        previous = action("◀");
        toggleList = action("☰");
        next = action("▶");
        fullscreenButton = action("⛶");
        retryButton = action("↻");
        stateLabel = text("Select an M3U playlist", 12, Color.WHITE);
        stateLabel.setSingleLine(true);
        stateLabel.setEllipsize(TextUtils.TruncateAt.END);

        controlBar.addView(stateLabel, new LinearLayout.LayoutParams(0, -1, 1));
        controlBar.addView(retryButton, buttonParams());
        controlBar.addView(previous, buttonParams());
        controlBar.addView(toggleList, buttonParams());
        controlBar.addView(next, buttonParams());
        controlBar.addView(fullscreenButton, buttonParams());
        videoFrame.addView(controlBar, barLp);

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
        videoFrame.addView(spinner, spinLp);
        spinner.setVisibility(View.GONE);
        base.addView(videoFrame, new LinearLayout.LayoutParams(0, -1, 1));

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setBackgroundColor(PANEL);
        playlistButton = text("Change playlist", 12, ORANGE);
        playlistButton.setPadding(dp(12), dp(4), dp(6), dp(4));
        playlistButton.setBackgroundColor(Color.rgb(22, 25, 29));
        sidebar.addView(playlistButton, new LinearLayout.LayoutParams(-1, dp(45)));
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        sidebar.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelList);
        sidebar.addView(channelScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout options = new LinearLayout(this);
        options.setPadding(dp(8), dp(4), dp(8), dp(8));
        options.setOrientation(LinearLayout.VERTICAL);
        options.setBackgroundColor(Color.rgb(25, 28, 32));
        autoSwitch = new Switch(this);
        autoSwitch.setText("Auto Next Channel");
        autoSwitch.setTextSize(12);
        autoSwitch.setTextColor(Color.WHITE);
        autoSwitch.setChecked(autoSkip);
        options.addView(autoSwitch, new LinearLayout.LayoutParams(-1, dp(45)));
        autoLabel = text(autoSkip ? "On · skip after 3 failed retries" : "Off · keep reconnecting this channel", 11, Color.LTGRAY);
        options.addView(autoLabel);
        sidebar.addView(options);
        base.addView(sidebar, new LinearLayout.LayoutParams(dp(258), -1));
        setContentView(base);

        previous.setOnClickListener(v -> move(-1));
        next.setOnClickListener(v -> move(1));
        toggleList.setOnClickListener(v -> setListVisible(!showList));
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        retryButton.setOnClickListener(v -> {
            if (index >= 0) {
                retryCount = 0;
                selectChannel(index, true);
            }
        });
        playlistButton.setOnClickListener(v -> pickPlaylist());
        autoSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            autoSkip = checked;
            prefs.edit().putBoolean("auto_skip", checked).apply();
            autoLabel.setText(checked ? "On · skip after 3 failed retries" : "Off · keep reconnecting this channel");
        });
        videoFrame.setOnClickListener(v -> controlBar.setVisibility(controlBar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        updateSidebarVisibility();
        updateButtons();
    }

    private void setListVisible(boolean enabled) {
        showList = enabled;
        prefs.edit().putBoolean("show_channels", showList).apply();
        updateSidebarVisibility();
    }

    private void updateSidebarVisibility() {
        if (sidebar == null) return;
        sidebar.setVisibility(showList ? View.VISIBLE : View.GONE);
    }

    private void toggleFullscreen() {
        if (!fullscreen) {
            listBeforeFullscreen = showList;
            fullscreen = true;
            showList = false;
        } else {
            fullscreen = false;
            showList = listBeforeFullscreen;
        }
        prefs.edit().putBoolean("fullscreen", fullscreen).apply();
        updateSidebarVisibility();
        applyFullscreen();
    }

    private void applyFullscreen() {
        if (fullscreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyFullscreen();
    }

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
            channelList.addView(t, new LinearLayout.LayoutParams(-1, dp(37)));
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
        ++generation;
        cancelTimeouts();
        spinner.setVisibility(View.GONE);
        releasePlayer();

        // Reconnect to the SAME channel first. With Auto Next disabled, retry
        // indefinitely with a bounded backoff instead of demanding manual Retry.
        // With Auto Next enabled, exhaust a small retry budget before skipping.
        if (!autoSkip || retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount = Math.min(retryCount + 1, 1000);
            int retryNumber = retryCount;
            long delay = Math.min(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS * (1L << Math.min(retryNumber - 1, 4)));
            stateLabel.setText(reason + " · reconnecting in " + (delay / 1000.0) + "s (attempt " + retryNumber + ")");
            final long retrySerial = generation;
            handler.postDelayed(() -> {
                if (retrySerial == generation && index >= 0 && index < channels.size()) {
                    startPlaybackForCurrentIndex();
                }
            }, delay);
            return;
        }

        retryCount = 0;
        ++failureCount;
        if (failureCount >= channels.size()) {
            stateLabel.setText("All channels failed · tap Retry to try again");
            Toast.makeText(this, "No playable channels found", Toast.LENGTH_LONG).show();
            return;
        }
        int nextIndex = (index + 1) % channels.size();
        stateLabel.setText("Switching to next channel...");
        handler.post(() -> selectChannel(nextIndex, false));
    }

    private void cancelRebufferTimeout() {
        if (rebufferTimeout != null) handler.removeCallbacks(rebufferTimeout);
        rebufferTimeout = null;
    }

    private void cancelTimeouts() {
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        cancelRebufferTimeout();
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
