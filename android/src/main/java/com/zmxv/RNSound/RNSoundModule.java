package com.zmxv.RNSound;

import android.content.Context;
import android.net.Uri;
import android.media.AudioManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.*;
import com.google.android.exoplayer2.upstream.*;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class RNSoundModule extends ReactContextBaseJavaModule implements AudioManager.OnAudioFocusChangeListener {
  ReactApplicationContext context;
  final static Object NULL = null;

  Map<Integer, SimpleExoPlayer> playerPool = new HashMap<>();
  Map<Integer, ExoPlayerEventListener> playerEventListenerPool = new HashMap<>();

  String category;
  Boolean mixWithOthers = true;

  Integer focusedPlayerKey;
  boolean wasPlayingBeforeFocusChange;

  /**
   * Create RNSoundModule instance without settings
   *
   * @param context
   */
  public RNSoundModule(ReactApplicationContext context) {
    super(context);

    this.context = context;
    this.category = null;
  }

  @Override
  public String getName() {
    return "RNSound";
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("IsAndroid", true);

    return constants;
  }

  /**
   * Prepare ExoPlayer instance for audio playback, category of playback in AudioManager
   * and set EventListene for ExoPlayer
   *
   * @param fileName
   * @param key React Native key for this player instance
   * @param options
   * @param callback
   */
  @ReactMethod
  public void prepare(final String fileName, final Integer key, final ReadableMap options, final Callback callback) {
    final SimpleExoPlayer player = createMediaPlayer(fileName);

    // Check if the player was created
    if (player == null) {
      WritableMap e = Arguments.createMap();
      e.putInt("code", -1);
      e.putString("message", "resource not found");

      return;
    }

    final RNSoundModule module = this;

    // Set category of playback in AudioManager
    if (module.category != null) {
      Integer category = null;

      switch (module.category) {
        case "Playback":
          category = AudioManager.STREAM_MUSIC;
          break;
        case "Ambient":
          category = AudioManager.STREAM_NOTIFICATION;
          break;
        case "System":
          category = AudioManager.STREAM_SYSTEM;
          break;
        default:
          Log.e("RNSoundModule", String.format("Unrecognised category %s", module.category));
          break;
      }

      if (category != null) {
        player.setAudioStreamType(category);
      }
    }

    // Set event listener to player
    ExoPlayerEventListener eventListener = new ExoPlayerEventListener(module, key, player, callback);
    this.playerEventListenerPool.put(key, eventListener);

    player.addListener(eventListener);

    // Set update progress handler
    this.updateProgress(key, new Handler());
  }

  /**
   * Create new ExoPlayer instance with local or remote HTTP file
   *
   * @param fileName
   * @return Player instance
   */
  protected SimpleExoPlayer createMediaPlayer(final String fileName) {
    // Create a default TrackSelector
    Handler mainHandler = new Handler();
    BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
    TrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);

    TrackSelector trackSelector = new DefaultTrackSelector(trackSelectionFactory);

    // Create player
    SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(this.context, trackSelector);

    // Load media depending on type of input
    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this.context, Util.getUserAgent(this.context, "Audioplayer"), null);
    ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
    MediaSource source = null;

    // Load media: HTTP URL
    if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
      Uri uri = Uri.parse(fileName);
      source = new ExtractorMediaSource(uri, dataSourceFactory, extractorsFactory, null, null);
    }

    // Load media: File
    File file = new File(fileName);
    if (file.exists()) {
      Uri uri = Uri.fromFile(file);
      source = new ExtractorMediaSource(uri, dataSourceFactory, extractorsFactory, null, null);
    }

    // Set source to audio player
    if (source != null) {
      player.prepare(source);

      return player;
    }
    else {
      return null;
    }
  }

  /**
   * Remove all player except of one player by key
   *
   * @param exceptOfKey
   */
  private void releaseAllPlayers(Integer exceptOfKey) {
    if (this.playerPool.size() > 1) {
      List<Integer> playersToRelease = new ArrayList<Integer>();

      // Get keys of all players to release
      for (Map.Entry<Integer, SimpleExoPlayer> entry : this.playerPool.entrySet()) {
          if (entry.getKey() != exceptOfKey) {
            playersToRelease.add(entry.getKey());
          }
      }

      // Release player and remove from pools
      for (Integer key : playersToRelease) {
        this.release(key);
      }
    }
  }

  /**
   * Player play audio
   *
   * @param key
   * @param callback
   */
  @ReactMethod
  public void play(final Integer key, final Callback callback) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      if (callback != null) {
        callback.invoke(false);
      }
      
      return;
    }

    // Is player already playing
    if (player.getPlayWhenReady()) {
      return;
    }


    // Request audio focus in Android system (playback without any other audio playbacks on system)
    if (!this.mixWithOthers) {
      AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
      audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

      this.focusedPlayerKey = key;

      // Release all players, except of this one
      this.releaseAllPlayers(key);
    }

    // Set player to playing
    player.setPlayWhenReady(true);

    if (callback != null) {
      callback.invoke(true);
    }

    // Set update progress handler
    this.updateProgress(key, new Handler());

    // Send event to event emitter
    WritableMap data = Arguments.createMap();
    data.putBoolean("isPlaying", true);
    Long positionInMs = player.getCurrentPosition();
    data.putDouble("currentTime", Math.floor(positionInMs * .001));
    this.sendEvent("RNSound-playing", key, data);
  }

  /**
   * Player pause audio
   *
   * @param key
   * @param callback
   */
  @ReactMethod
  public void pause(final Integer key, final Callback callback) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      if (callback != null) {
        callback.invoke(false);
      }

      return;
    }

    // Set player to pause
    player.setPlayWhenReady(false);

    if (callback != null) {
      callback.invoke(true);
    }

    // Send event to event emitter
    WritableMap data = Arguments.createMap();
    data.putBoolean("isPlaying", false);
    Long positionInMs = player.getCurrentPosition();
    data.putDouble("currentTime", Math.floor(positionInMs * .001));
    this.sendEvent("RNSound-playing", key, data);
  }

  /**
   * Player stop audio
   * @param key
   * @param callback
   */
  @ReactMethod
  public void stop(final Integer key, final Callback callback) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      if (callback != null) {
        callback.invoke(false);
      }

      return;
    }

    // Pause player, if it is playing
    if (player.getPlayWhenReady()) {
      player.setPlayWhenReady(false);
    }

    // Seek to begin of playback time
    player.seekTo(0);
    
    // Release audio focus in Android system
    if (!this.mixWithOthers && key == this.focusedPlayerKey) {
      AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
      audioManager.abandonAudioFocus(this);
    }

    if (callback != null) {
      callback.invoke(true);
    }

    // Send event to event emitter
    WritableMap data = Arguments.createMap();
    data.putBoolean("isPlaying", false);
    Long positionInMs = player.getCurrentPosition();
    data.putDouble("currentTime", Math.floor(positionInMs * .001));
    this.sendEvent("RNSound-playing", key, data);
  }

  /**
   * Release ExoPlayer instance by player key
   *
   * @param key
   */
  @ReactMethod
  public void release(final Integer key) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      return;
    }

    // Pause player, if it is playing
    if (player.getPlayWhenReady()) {
      player.setPlayWhenReady(false);
    }

    // Release player
    player.release();
    this.playerPool.remove(key);
    this.playerEventListenerPool.remove(key);

    // Release audio focus in Android system
    if (!this.mixWithOthers && key == this.focusedPlayerKey) {
      AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
      audioManager.abandonAudioFocus(this);
    }
  }

  /**
   * Set volume of audio playback
   *
   * @param key
   * @param volume
   */
  @ReactMethod
  public void setVolume(final Integer key, final Float volume) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      return;
    }

    player.setVolume(volume);
  }

  /**
   * Get volume of system audio (STREAM_MUSIC)
   *
   * @param callback
   */
  @ReactMethod
  public void getSystemVolume(final Callback callback) {
    if (callback == null) {
      return;
    }

    try {
      AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);

      callback.invoke(NULL, (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }
    catch (Exception error) {
      WritableMap e = Arguments.createMap();
      e.putInt("code", -1);
      e.putString("message", error.getMessage());

      callback.invoke(e);
    }
  }

  /**
   * Get volume of system audio (STREAM_MUSIC)
   *
   * @param value
   */
  @ReactMethod
  public void setSystemVolume(final Float value) {
    AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);

    int volume = Math.round(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * value);
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
  }

  /**
   * Set if audio playback should be looping
   *
   * @param key
   * @param shouldLooping
   */
  @ReactMethod
  public void setLooping(final Integer key, final Boolean shouldLooping) {
    // Get player eventListener from pool
    ExoPlayerEventListener eventListener = this.playerEventListenerPool.get(key);

    if (eventListener == null) {
      return;
    }

    // Set looping
    eventListener.setLooping(shouldLooping);
  }

  /**
   * Set speed of audio playback
   *
   * @param key
   * @param speed
   */
  @ReactMethod
  public void setSpeed(final Integer key, final Float speed) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      return;
    }

    // Set playback speed
    player.setPlaybackParameters(new PlaybackParameters(speed, 1));
  }

  /**
   * Set current time of audio playback position
   * @param key
   * @param sec Seconds from start
   */
  @ReactMethod
  public void setCurrentTime(final Integer key, final Float sec) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      return;
    }

    // Seek to position
    player.seekTo((long) Math.floor(sec * 1000));
  }

  /**
   * Get current time of audio playback position
   *
   * @param key
   * @param callback
   */
  @ReactMethod
  public void getCurrentTime(final Integer key, final Callback callback) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      callback.invoke(-1, false);

      return;
    }

    // Return position in seconds and isPlaying status
    Long positionInMs = player.getCurrentPosition();
    callback.invoke((positionInMs * .001), player.getPlayWhenReady());
  }

  /**
   * Set speaker of Android phone on or off for audio playback
   * @param key
   * @param speaker
   */
  @ReactMethod
  public void setSpeakerphoneOn(final Integer key, final Boolean speaker) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      return;
    }

    // Set playback to speakers
    player.setAudioStreamType(AudioManager.STREAM_MUSIC); // Speakers possible for STREAM_MUSIC only
    AudioManager audioManager = (AudioManager) this.context.getSystemService(this.context.AUDIO_SERVICE);
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    audioManager.setSpeakerphoneOn(speaker);
  }

  /**
   * Set category and mixWithOthers to module, but use only for new players in prepare()
   *
   * @param category
   * @param mixWithOthers
   */
  @ReactMethod
  public void setCategory(final String category, final Boolean mixWithOthers) {
    this.category = category;
    this.mixWithOthers = mixWithOthers;
  }

  /**
   * On change of audio focus in Android system via AudioManager.OnAudioFocusChangeListener
   * pause or play audio
   *
   * @param focusChange
   */
  @Override
  public void onAudioFocusChange(int focusChange) {
    if (!this.mixWithOthers) {
      // Get player from pool
      SimpleExoPlayer player = this.playerPool.get(this.focusedPlayerKey);

      if (player == null) {
        return;
      }

      // Change playback state based on focus change of audio playback
      if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
        this.wasPlayingBeforeFocusChange = player.getPlayWhenReady();

        if (this.wasPlayingBeforeFocusChange) {
          this.pause(this.focusedPlayerKey, null);
        }
      }
      else { // Regain focus
        if (this.wasPlayingBeforeFocusChange) {
          this.play(this.focusedPlayerKey, null);
          this.wasPlayingBeforeFocusChange = false;
        }
      }
    }
  }

  private void updateProgress(final Integer key, final Handler handler) {
    // Get player from pool
    SimpleExoPlayer player = this.playerPool.get(key);

    if (player == null) {
      return;
    }

    // Player have to playback audio
    if (!player.getPlayWhenReady()) {
      return;
    }

    // Get playback time
    long positionInMs = player.getCurrentPosition();
    double positionS = (positionInMs * .001);

    // Send progress event to event emitter
    WritableMap data = Arguments.createMap();
    data.putDouble("progress", (positionInMs * .001));
    this.sendEvent("RNSound-progress", key, data);

    // Calculate delay to next 10 seconds point and postDelayed
    int delay = (int) Math.round(10000 - (positionInMs % 10000));

    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        updateProgress(key, handler);
      }
    }, delay);
  }

  /**
   * Send event to JavaScript code via context
   *
   * @param eventName
   * @param params
   */
  public void sendEvent(String eventName, Integer key, @Nullable WritableMap params) {
    params.putInt("key", key);

    this.context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
  }
}
