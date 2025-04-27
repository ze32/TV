package com.fongmi.android.tv.player;

import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON;
import static androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.player.danmaku.Loader;
import com.fongmi.android.tv.player.danmaku.Parser;
import com.fongmi.android.tv.player.danmaku.Sync;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;
import com.orhanobut.logger.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.peerless2012.ass.media.AssHandler;
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory;
import io.github.peerless2012.ass.media.type.AssRenderType;
import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;

public class Players implements Player.Listener, ParseCallback, DrawHandler.Callback {

    private static final String TAG = Players.class.getSimpleName();

    public static final int SOFT = 0;
    public static final int HARD = 1;

    private final DanmakuContext context;
    private final StringBuilder builder;
    private final Formatter formatter;
    private final Runnable runnable;

    private Map<String, String> headers;
    private MediaSessionCompat session;
    private List<Danmaku> danmakus;
    private DanmakuView danmaku;
    private ExoPlayer exoPlayer;
    private ParseJob parseJob;
    private PlayerView view;
    private List<Sub> subs;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;

    private int decode;
    private int retry;

    public static Players create(Activity activity) {
        Players player = new Players(activity);
        Server.get().setPlayer(player);
        return player;
    }

    private Players(Activity activity) {
        decode = Setting.getDecode();
        builder = new StringBuilder();
        runnable = ErrorEvent::timeout;
        context = DanmakuContext.create();
        formatter = new Formatter(builder, Locale.getDefault());
        createSession(activity);
    }

    private void createSession(Activity activity) {
        session = new MediaSessionCompat(activity, "TV");
        session.setCallback(SessionCallback.create(this));
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(App.get(), 0, new Intent(App.get(), activity.getClass()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        MediaControllerCompat.setMediaController(activity, session.getController());
    }

    public void init(PlayerView view) {
        releasePlayer();
        setPlayer(view);
        setMediaItem();
    }

    private void setPlayer(PlayerView view) {
        AssHandler assHandler = new AssHandler(AssRenderType.OPEN_GL);
        AssSubtitleParserFactory subtitleParserFactory = new AssSubtitleParserFactory(assHandler);
        exoPlayer = new ExoPlayer.Builder(App.get()).setLoadControl(ExoUtil.buildLoadControl()).setTrackSelector(ExoUtil.buildTrackSelector()).setRenderersFactory(ExoUtil.buildRenderersFactory(isHard() ? EXTENSION_RENDERER_MODE_ON : EXTENSION_RENDERER_MODE_PREFER)).setMediaSourceFactory(ExoUtil.buildMediaSourceFactory(assHandler, subtitleParserFactory)).build();
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true);
        if (Setting.isLibAss()) assHandler.init(exoPlayer);
        exoPlayer.addAnalyticsListener(new EventLogger());
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(this);
        view.setPlayer(exoPlayer);
        this.view = view;
    }

    public void setDanmakuView(DanmakuView view) {
        (danmaku = view).setCallback(this);
        context.setDanmakuSync(new Sync(this));
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, 2);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, 2);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, 2);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, 2);
        context.setMaximumLines(maxLines).setScrollSpeedFactor(1.2f).setDanmakuTransparency(0.8f);
        context.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(ResUtil.dp2px(8));
    }

    public ExoPlayer get() {
        return exoPlayer;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public List<Danmaku> getDanmakus() {
        return danmakus;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        this.sub = sub;
        setMediaItem();
    }

    public void setFormat(String format) {
        this.format = format;
        setMediaItem();
    }

    public void reset() {
        removeTimeoutCheck();
        retry = 0;
    }

    public void clearMediaItems() {
        if (exoPlayer != null) exoPlayer.clearMediaItems();
    }

    public void clear() {
        danmakus = null;
        headers = null;
        format = null;
        subs = null;
        drm = null;
        url = null;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return exoPlayer == null ? 0 : exoPlayer.getVideoSize().width;
    }

    public int getVideoHeight() {
        return exoPlayer == null ? 0 : exoPlayer.getVideoSize().height;
    }

    public float getSpeed() {
        return exoPlayer == null ? 1.0f : exoPlayer.getPlaybackParameters().speed;
    }

    public long getPosition() {
        return exoPlayer == null ? C.TIME_UNSET : exoPlayer.getCurrentPosition();
    }

    public long getDuration() {
        return exoPlayer == null ? -1 : exoPlayer.getDuration();
    }

    public long getBuffered() {
        return exoPlayer == null ? 0 : exoPlayer.getBufferedPosition();
    }

    public boolean retried() {
        return ++retry > 2;
    }

    public boolean haveTrack(int type) {
        return exoPlayer != null && ExoUtil.haveTrack(exoPlayer.getCurrentTracks(), type);
    }

    public boolean isDanmakuPrepared() {
        return danmaku != null && danmaku.isPrepared();
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public boolean isEnded() {
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_ENDED;
    }

    public boolean isIdle() {
        return exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_IDLE;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        return getDuration() < 60 * 1000 || exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isVod() {
        return getDuration() > 60 * 1000 && !exoPlayer.isCurrentMediaItemLive();
    }

    public boolean isHard() {
        return decode == HARD;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public String getSizeText() {
        return getVideoWidth() == 0 && getVideoHeight() == 0 ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (exoPlayer == null || !exoPlayer.isCommandAvailable(COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        exoPlayer.setPlaybackParameters(exoPlayer.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.25f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? Setting.getSpeed() : 1;
        return setSpeed(speed);
    }

    public void toggleDecode() {
        decode = isHard() ? SOFT : HARD;
        Setting.putDecode(decode);
        init(view);
    }

    public String getPositionTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return stringToTime(time);
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (exoPlayer != null) exoPlayer.seekTo(time);
        if (isDanmakuPrepared()) danmaku.seekTo(time);
        if (isDanmakuPrepared()) danmaku.hide();
    }

    public void seekToDefaultPosition() {
        if (exoPlayer != null) exoPlayer.seekToDefaultPosition();
        prepare();
    }

    public void prepare() {
        if (exoPlayer != null) exoPlayer.prepare();
    }

    public void play() {
        if (exoPlayer != null) exoPlayer.play();
        if (isDanmakuPrepared()) danmaku.resume();
    }

    public void pause() {
        if (exoPlayer != null) exoPlayer.pause();
        if (isDanmakuPrepared()) danmaku.pause();
    }

    public void stop() {
        if (exoPlayer != null) exoPlayer.stop();
        if (isDanmakuPrepared()) danmaku.stop();
        stopParse();
    }

    public void release() {
        stopParse();
        releasePlayer();
        session.release();
        removeTimeoutCheck();
        Server.get().setPlayer(null);
        App.execute(() -> Source.get().stop());
        if (isDanmakuPrepared()) danmaku.release();
    }

    private void releasePlayer() {
        if (exoPlayer != null) exoPlayer.release();
        if (view != null) view.setPlayer(null);
        exoPlayer = null;
    }

    private void removeTimeoutCheck() {
        App.removeCallbacks(runnable);
    }

    public void start(Channel channel, int timeout) {
        if (channel.hasMsg()) {
            ErrorEvent.extract(channel.getMsg());
        } else if (isIllegal(channel.getUrl())) {
            ErrorEvent.url();
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (channel.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(channel.getDrm().getUUID())) {
            ErrorEvent.drm();
        } else {
            setMediaItem(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        if (result.hasMsg()) {
            ErrorEvent.extract(result.getMsg());
        } else if (isIllegal(result.getRealUrl())) {
            ErrorEvent.url();
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            ErrorEvent.drm();
        } else {
            setMediaItem(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        stopParse();
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    private Map<String, String> checkUa(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) if (HttpHeaders.USER_AGENT.equalsIgnoreCase(header.getKey())) return headers;
        headers.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? ExoUtil.getUa() : Setting.getUa());
        return headers;
    }

    private List<Sub> checkSub(List<Sub> subs) {
        if (sub == null || subs.contains(sub)) return subs;
        subs.add(0, sub);
        return subs;
    }

    private void setMediaItem() {
        if (url != null) setMediaItem(headers, url, format, drm, subs, Constant.TIMEOUT_PLAY);
    }

    public void setMediaItem(String url) {
        setMediaItem(new HashMap<>(), url);
    }

    private void setMediaItem(Map<String, String> headers, String url) {
        setMediaItem(headers, url, null, null, new ArrayList<>(), Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(Channel channel, int timeout) {
        setMediaItem(channel.getHeaders(), channel.getUrl(), channel.getFormat(), channel.getDrm(), new ArrayList<>(), timeout);
    }

    private void setMediaItem(Result result, int timeout) {
        setMediaItem(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), timeout);
        setDanmaku(danmakus = result.getDanmaku());
    }

    private void setMediaItem(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout) {
        if (exoPlayer != null) exoPlayer.setMediaItem(ExoUtil.getMediaItem(this.headers = checkUa(headers), UrlUtil.uri(this.url = url), this.format = format, this.drm = drm, checkSub(this.subs = subs), decode));
        App.post(runnable, timeout);
        session.setActive(true);
        PlayerEvent.prepare();
        Logger.t(TAG).d(url);
        prepare();
    }

    private void setDanmaku(List<Danmaku> items) {
        if (items.isEmpty()) danmaku.release();
        else setDanmaku(items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        danmaku.release();
        if (danmakus == null) danmakus = new ArrayList<>();
        if (!item.isEmpty() && !danmakus.contains(item)) danmakus.add(0, item);
        if (!item.isEmpty()) App.execute(() -> danmaku.prepare(new Parser().load(new Loader(item).getDataSource()), context));
        for (int i = 0; i < danmakus.size(); i++) danmakus.get(i).setSelected(danmakus.get(i).getUrl().equals(item.getUrl()) && !danmakus.get(i).isSelected());
    }

    public void setDanmakuSize(float size) {
        context.setScaleTextSize(size);
    }

    public void resetTrack() {
        if (exoPlayer != null) ExoUtil.resetTrack(exoPlayer);
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) setTrack(track);
    }

    private void setTrack(Track item) {
        if (item.isSelected()) {
            ExoUtil.selectTrack(exoPlayer, item.getGroup(), item.getTrack());
        } else {
            ExoUtil.deselectTrack(exoPlayer, item.getGroup(), item.getTrack());
        }
    }

    private void setPlaybackState(int state) {
        long actions = PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        session.setPlaybackState(new PlaybackStateCompat.Builder().setActions(actions).setState(state, getPosition(), getSpeed()).build());
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        if ("data".equals(scheme)) return false;
        if (url.startsWith("json:")) return false;
        if (url.startsWith("parse:")) return false;
        return scheme.isEmpty() || "file".equals(scheme) ? !Path.exists(url) : host.isEmpty();
    }

    private MediaMetadataCompat.Builder putBitmap(MediaMetadataCompat.Builder builder, Drawable drawable) {
        try {
            return builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, ((BitmapDrawable) drawable).getBitmap());
        } catch (Exception ignored) {
            return builder;
        }
    }

    public void setMetadata(String title, String artist, String artUri, Drawable drawable) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        session.setMetadata(putBitmap(builder, drawable).build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            Bundle bundle = new Bundle();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) bundle.putString(entry.getKey(), entry.getValue());
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_TEXT, getUrl());
            intent.putExtra("extra_headers", bundle);
            intent.putExtra("title", title);
            intent.putExtra("name", title);
            intent.setType("text/plain");
            activity.startActivity(Util.getChooser(intent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void choose(Activity activity, CharSequence title) {
        try {
            if (isEmpty()) return;
            List<String> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : getHeaders().entrySet()) list.addAll(Arrays.asList(entry.getKey(), entry.getValue()));
            Uri data = getUrl().startsWith("file://") || getUrl().startsWith("/") ? FileUtil.getShareUri(getUrl()) : Uri.parse(getUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(data, "video/*");
            intent.putExtra("title", title);
            intent.putExtra("return_result", isVod());
            intent.putExtra("headers", list.toArray(new String[0]));
            if (isVod()) intent.putExtra("position", (int) getPosition());
            activity.startActivityForResult(Util.getChooser(intent), 1001);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkData(Intent data) {
        try {
            if (data == null || data.getExtras() == null) return;
            int position = data.getExtras().getInt("position", 0);
            String endBy = data.getExtras().getString("end_by", "");
            if ("playback_completion".equals(endBy)) ActionEvent.next();
            if ("user".equals(endBy)) seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        setMediaItem(headers, url);
    }

    @Override
    public void onParseError() {
        ErrorEvent.parse();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (!events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYER_ERROR)) return;
        switch (player.getPlaybackState()) {
            case Player.STATE_IDLE:
                setPlaybackState(events.contains(Player.EVENT_PLAYER_ERROR) ? PlaybackStateCompat.STATE_ERROR : PlaybackStateCompat.STATE_NONE);
                break;
            case Player.STATE_READY:
                setPlaybackState(player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                break;
            case Player.STATE_BUFFERING:
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                break;
            case Player.STATE_ENDED:
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
        }
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_BUFFERING && isDanmakuPrepared()) danmaku.pause();
        if (state == Player.STATE_READY && isDanmakuPrepared()) prepared();
        PlayerEvent.state(state);
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        PlayerEvent.size();
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        if (tracks.isEmpty()) return;
        setTrack(Track.find(url));
        PlayerEvent.track();
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Logger.t(TAG).e(error.errorCode + "," + url);
        if (retried()) ErrorEvent.extract(error.getErrorCodeName());
        else switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW:
                seekToDefaultPosition();
                break;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                toggleDecode();
                break;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED:
                setFormat(ExoUtil.getMimeType(error.errorCode));
                break;
            default:
                ErrorEvent.extract(error.getErrorCodeName());
                break;
        }
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (isPlaying()) danmaku.start(getPosition());
            else danmaku.pause();
            danmaku.show();
        });
    }

    @Override
    public void updateTimer(DanmakuTimer danmakuTimer) {
    }

    @Override
    public void danmakuShown(BaseDanmaku baseDanmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}
