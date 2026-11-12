package com.brave.simplem3uplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final int REQUEST_PLAYLIST = 1001;
    private static final long LOAD_TIMEOUT_MS = 3200;
    private static final String PREFS = "player_prefs";
    private static final String KEY_URI = "playlist_uri";
    private static final String KEY_CHANNEL = "channel_index";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Channel> channels = new ArrayList<>();
    private SharedPreferences prefs;
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar progress;
    private TextView title, status;
    private Button prevButton, nextButton, channelsButton;
    private int currentIndex = -1;
    private int unsuccessfulChannels = 0;
    private long playGeneration = 0;
    private Runnable watchdog;
    private boolean hasRendered = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createUi();
        restorePlaylist();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        return b;
    }

    private void createUi() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOrientation(LinearLayout.VERTICAL);
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(false);
        root.addView(playerView, new LinearLayout.LayoutParams(-1, 0, 1f));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        spinLp.gravity = Gravity.CENTER;
        root.addView(progress, spinLp);

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER);
        title.setText("M3U TV");
        root.addView(title);
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setText("Choose an M3U playlist");
        root.addView(status);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        prevButton = button("◀ Prev");
        channelsButton = button("Channels");
        nextButton = button("Next ▶");
        Button open = button("M3U");
        for (Button b : new Button[] { prevButton, channelsButton, nextButton, open }) {
            controls.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(controls);
        setContentView(root);

        prevButton.setOnClickListener(v -> selectRelative(-1));
        nextButton.setOnClickListener(v -> selectRelative(1));
        channelsButton.setOnClickListener(v -> showChannels());
        open.setOnClickListener(v -> openPlaylistPicker());
        updateButtons();
    }

    private void openPlaylistPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PLAYLIST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PLAYLIST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) {}
        if (loadPlaylist(uri)) {
            prefs.edit().putString(KEY_URI, uri.toString()).putInt(KEY_CHANNEL, 0).apply();
            selectChannel(0, true);
        }
    }

    private boolean loadPlaylist(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IllegalStateException("File could not be opened");
            List<Channel> parsed = M3uParser.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (parsed.isEmpty()) throw new IllegalStateException("No supported HTTP/HTTPS channels");
            cancelWatchdog();
            releasePlayer();
            channels.clear();
            channels.addAll(parsed);
            currentIndex = -1;
            updateButtons();
            status.setText(channels.size() + " channels loaded");
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Playlist: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void restorePlaylist() {
        String uri = prefs.getString(KEY_URI, null);
        if (uri == null) return;
        if (loadPlaylist(Uri.parse(uri))) {
            int saved = prefs.getInt(KEY_CHANNEL, 0);
            selectChannel(Math.max(0, Math.min(saved, channels.size() - 1)), true);
        } else {
            // Keep the saved URI: external storage might be temporarily unavailable.
            status.setText("Saved playlist unavailable. Select M3U to reload.");
        }
    }

    private void updateButtons() {
        boolean enabled = !channels.isEmpty();
        prevButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        channelsButton.setEnabled(enabled);
    }

    private void showChannels() {
        if (channels.isEmpty()) return;
        String[] items = new String[channels.size()];
        for (int i = 0; i < items.length; i++) items[i] = channels.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("Channels (" + items.length + ")")
                .setSingleChoiceItems(items, currentIndex, (dialog, selected) -> {
                    dialog.dismiss();
                    selectChannel(selected, true);
                })
                .setNegativeButton("Close", null).show();
    }

    private void selectRelative(int delta) {
        if (channels.isEmpty()) return;
        int base = currentIndex < 0 ? 0 : currentIndex;
        selectChannel((base + delta + channels.size()) % channels.size(), true);
    }

    private void selectChannel(int index, boolean manual) {
        if (channels.isEmpty()) return;
        if (manual) unsuccessfulChannels = 0;
        if (index < 0 || index >= channels.size()) return;
        currentIndex = index;
        Channel channel = channels.get(index);
        prefs.edit().putInt(KEY_CHANNEL, index).apply();
        playGeneration++;
        final long generation = playGeneration;
        cancelWatchdog();
        releasePlayer();
        hasRendered = false;
        title.setText(channel.name);
        status.setText((index + 1) + " / " + channels.size() + " · Loading…");
        progress.setVisibility(View.VISIBLE);

        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(3000)
                    .setReadTimeoutMs(3000)
                    .setAllowCrossProtocolRedirects(true);
            if (channel.userAgent != null && !channel.userAgent.isEmpty()) http.setUserAgent(channel.userAgent);
            if (channel.referer != null && !channel.referer.isEmpty()) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Referer", channel.referer);
                http.setDefaultRequestProperties(headers);
            }
            DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this, http);
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                    .build();
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (generation != playGeneration) return;
                    if (state == Player.STATE_READY) {
                        hasRendered = true;
                        unsuccessfulChannels = 0;
                        cancelWatchdog();
                        progress.setVisibility(View.GONE);
                        status.setText((currentIndex + 1) + " / " + channels.size() + " · Playing");
                    } else if (state == Player.STATE_ENDED) {
                        automaticNext(generation);
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    automaticNext(generation);
                }
            });
            MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(channel.url));
            String path = Uri.parse(channel.url).getPath();
            if (path != null && path.toLowerCase(Locale.ROOT).endsWith(".m3u8")) {
                item.setMimeType(MimeTypes.APPLICATION_M3U8);
            }
            player.setMediaItem(item.build());
            player.prepare();
            player.play();
            watchdog = () -> {
                if (generation == playGeneration && !hasRendered) automaticNext(generation);
            };
            handler.postDelayed(watchdog, LOAD_TIMEOUT_MS);
        } catch (Exception e) {
            automaticNext(generation);
        }
    }

    private void automaticNext(long generation) {
        if (generation != playGeneration || channels.isEmpty()) return;
        // Invalidate callbacks before posting the change, preventing duplicate skips.
        playGeneration++;
        cancelWatchdog();
        unsuccessfulChannels++;
        if (unsuccessfulChannels >= channels.size()) {
            releasePlayer();
            progress.setVisibility(View.GONE);
            status.setText("No channel started. Select one or reload playlist.");
            Toast.makeText(this, "All channels failed or timed out", Toast.LENGTH_LONG).show();
            return;
        }
        int next = (currentIndex + 1) % channels.size();
        handler.post(() -> selectChannel(next, false));
    }

    private void cancelWatchdog() {
        if (watchdog != null) handler.removeCallbacks(watchdog);
        watchdog = null;
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    @Override protected void onDestroy() {
        playGeneration++;
        cancelWatchdog();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
