package com.zmxv.RNSound;

import android.util.Log;
import com.facebook.react.bridge.Callback;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

/**
 * Implementation of ExoPlayer EventListener for RNSound
 */
public class ExoPlayerEventListener implements ExoPlayer.EventListener {
    final static Object NULL = null;
    private RNSoundModule module;
    private Integer key;
    private SimpleExoPlayer player;

    private Callback callbackPrepare;
    private boolean initCallbackWasCalled = false;

    private boolean shouldLooping = false;

    public ExoPlayerEventListener(RNSoundModule module, Integer key, SimpleExoPlayer player, Callback callbackPrepare) {
        this.module = module;
        this.key = key;
        this.player = player;
        this.callbackPrepare = callbackPrepare;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object o) {
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroupArray, TrackSelectionArray trackSelectionArray) {
    }

    @Override
    public void onLoadingChanged(boolean b) {
    }

    @Override
    public void onPlayerStateChanged(boolean b, int i) {
        // On player is loaded media source put player in playerPool
        if (i == ExoPlayer.STATE_READY) {
            if (this.initCallbackWasCalled) return;
            this.initCallbackWasCalled = true;

            // Put player intro pool
            this.module.playerPool.put(this.key, this.player);
            WritableMap props = Arguments.createMap();
            props.putDouble("duration", this.player.getDuration() * .001);

            // Execute callback successful
            try {
                this.callbackPrepare.invoke(NULL, props);
            }
            catch(RuntimeException runtimeException) {
                // The callback was already invoked
                Log.e("RNSoundModule", "Exception", runtimeException);
            }
        }

        // On playback ends, restart it (if looping is active)
        if (i == ExoPlayer.STATE_ENDED) {
            if (this.shouldLooping) {
                this.player.seekTo(0);
            }
            else {
                // Send isPlaying false event to event emitter
                WritableMap data = Arguments.createMap();
                data.putBoolean("isPlaying", false);
                Long positionInMs = player.getCurrentPosition();
                data.putDouble("currentTime", Math.floor(positionInMs * .001));
                module.sendEvent("RNSound-playing", key, data);
            }
        }
    }

    // On player error throw error message in JS callback
    @Override
    public void onPlayerError(ExoPlaybackException e) {
        if (this.initCallbackWasCalled) return;
        this.initCallbackWasCalled = true;

        // Remove this EventListener from RNSoundModule map
        this.module.playerEventListenerPool.remove(this.key);

        // Execute callback error
        try {
            WritableMap props = Arguments.createMap();
            props.putString("what", e.getMessage());

            this.callbackPrepare.invoke(props, NULL);
        }
        catch(RuntimeException runtimeException) {
            // The callback was already invoked
            Log.e("RNSoundModule", "Exception", runtimeException);
        }
    }

    @Override
    public void onPositionDiscontinuity() {
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    }

    /**
     * Set looping of audio playback
     *
     * @param shouldLooping
     */
    public void setLooping(final Boolean shouldLooping) {
        this.shouldLooping = shouldLooping;
    }
}
