package com.rdm.rdmtestplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.widget.MediaController;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * VideoView class using a SurfaceTexture based on TextureVideoView
 * from https://github.com/malmstein/fenster but without any of the GUI
 * controls. This is meant to be a programmatically controlled video player.
 */
public class SurfaceTextureVideoView extends TextureView implements
        MediaController.MediaPlayerControl {
    private static String TAG = "SurfaceTextureVideoView";

    public static final int VIDEO_BEGINNING = 0;

    // User supplied content uri and (optional) headers
    private Uri mUri;
    private Map<String, String> mHeaders;

    // User supplied listeners (optional)
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnInfoListener mOnInfoListener;

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private static final int MILLIS_IN_SEC = 1000;
    private static final long NOTIFY_REPLAY_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    // All the stuff we need for playing and showing a video
    private SurfaceTexture mSurfaceTexture;
    private MediaPlayer mMediaPlayer = null;
    private final VideoSizeCalculator videoSizeCalculator;

    private float mVolumeLevel;
    private int mAudioSession;

    // Playback status variables
    private int mCurrentBufferPercentage;
    private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;

    private int mSeekWhenPrepared; // recording the seek position while preparing
    private OnPlayStateListener onPlayStateListener;

    /*
     * Setup & Initialization
     */

    public SurfaceTextureVideoView(final Context context) {
        super(context);
        videoSizeCalculator = new VideoSizeCalculator();
        initVideoView();
    }

    public SurfaceTextureVideoView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SurfaceTextureVideoView(final Context context, final AttributeSet attrs,
                                   final int defStyle) {
        super(context, attrs, defStyle);
        videoSizeCalculator = new VideoSizeCalculator();
        initVideoView();
    }

    private void initVideoView() {
        videoSizeCalculator.setVideoSize(0, 0);
        mVolumeLevel = 1.0f;

        setSurfaceTextureListener(mSTListener);

        setFocusable(false);
        setFocusableInTouchMode(false);
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
        setOnInfoListener(onInfoToPlayStateListener);
    }

    // Exactly 640 x Exactly 351
    @Override
    protected void onMeasure(final int widthMeasureSpec,
                             final int heightMeasureSpec) {
        //        Log.i(TAG, "onMeasure(" + widthMeasureSpec + ", " + heightMeasureSpec
        //                + ")");
        //        Log.i(TAG, "(" + videoSizeCalculator.getWidth() + ", "
        //                + videoSizeCalculator.getHeight() + ")");
        VideoSizeCalculator.Dimens dimens = videoSizeCalculator.measure(
                widthMeasureSpec, heightMeasureSpec);
        //        Log.i(TAG, "(" + dimens.getWidth() + ", " + dimens.getHeight() + ")");
        setMeasuredDimension(dimens.getWidth(), dimens.getHeight());
    }

    public int resolveAdjustedSize(final int desiredSize, final int measureSpec) {
        return getDefaultSize(desiredSize, measureSpec);
    }

    /*
     * Initiate Playback
     */

    public void setVideoFromBeginning(final Uri uri) {
        setVideo(uri, VIDEO_BEGINNING);
    }

    public void setVideoFromBeginning(final String path) {
        setVideo(Uri.parse(path), VIDEO_BEGINNING);
    }

    public void setVideo(final String url, final int seekInSeconds) {
        setVideoURI(Uri.parse(url), null, seekInSeconds);
    }

    public void setVideo(final Uri uri, final int seekInSeconds) {
        setVideoURI(uri, null, seekInSeconds);
    }

    public void setVideoURI(final Uri uri) {
        setVideoURI(uri, null, VIDEO_BEGINNING);
    }

    private void setVideoURI(final Uri uri, final Map<String, String> headers,
                             final int seekInSeconds) {
        Log.d(TAG, "start playing: " + uri);
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = seekInSeconds * 1000;
        openVideo();
        requestLayout();
        invalidate();
    }

    private void openVideo() {
        if (notReadyForPlaybackJustYetWillTryAgainLater()) {
            return;
        }
        tellTheMusicPlaybackServiceToPause();

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);
        try {
            mMediaPlayer = new MediaPlayer();

            if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }
            mMediaPlayer.setVolume(mVolumeLevel, mVolumeLevel);
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(getContext(), mUri, mHeaders);
            mMediaPlayer.setSurface(new Surface(mSurfaceTexture));
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();

            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
        } catch (final IOException | IllegalArgumentException ex) {
            notifyUnableToOpenContent(ex);
        }
    }

    private boolean notReadyForPlaybackJustYetWillTryAgainLater() {
        return mUri == null || mSurfaceTexture == null;
    }

    private void tellTheMusicPlaybackServiceToPause() {
        // these constants need to be published somewhere in the framework.
        Intent i = new Intent("com.android.music.musicservicecommand");
        i.putExtra("command", "pause");
        getContext().sendBroadcast(i);
    }

    private void notifyUnableToOpenContent(final Exception ex) {
        Log.w("Unable to open content: " + mUri, ex);
        mCurrentState = STATE_ERROR;
        mTargetState = STATE_ERROR;
        mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
    }

    /*
     * Just before and during playing...
     */

    @Override
    public void seekTo(final int millis) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(millis);
            mSeekWhenPrepared = 0;
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(final MediaPlayer mp) {
                    Log.i(TAG, "seek completed");
                }
            });
        } else {
            mSeekWhenPrepared = millis;
        }
    }

    public void seekToSeconds(final int seconds) {
        seekTo(seconds * MILLIS_IN_SEC);
    }

    public void setVolumeLevel(float level) {
        mVolumeLevel = level;
        try {
            if (mMediaPlayer != null) {
                mMediaPlayer.setVolume(mVolumeLevel, mVolumeLevel);
            }
        } catch (IllegalStateException e) {
            // do nothing
        }
    }

    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            setKeepScreenOn(true);
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    /*
     * While playing...
     */

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
                setKeepScreenOn(false);
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        release(false);
    }

    public void resume() {
        openVideo();
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            setKeepScreenOn(false);
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    /*
     * Playback Status
     */

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }

        return -1;
    }

    /**
     * @return current position in milliseconds
     */
    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getCurrentPositionInSeconds() {
        return getCurrentPosition() / MILLIS_IN_SEC;
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null && mCurrentState != STATE_ERROR
                && mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public int getAudioSessionId() {
        if (mAudioSession == 0) {
            MediaPlayer foo = new MediaPlayer();
            mAudioSession = foo.getAudioSessionId();
            foo.release();
        }
        return mAudioSession;
    }

    public Uri getCurrentUri() {
        return mUri;
    }

    /*
     * MediaPlayer Listeners
     */

    private final MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener = new MediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(final MediaPlayer mp, final int width,
                                       final int height) {
            videoSizeCalculator.setVideoSize(mp.getVideoWidth(),
                    mp.getVideoHeight());
            if (videoSizeCalculator.hasASizeYet()) {
                requestLayout();
            }
        }
    };

    private final MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(final MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            mCanPause = true;
            mCanSeekBack = true;
            mCanSeekForward = true;

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            videoSizeCalculator.setVideoSize(mp.getVideoWidth(), mp.getVideoHeight());

            // mSeekWhenPrepared may be changed after seekTo() call
            int seekToPosition = mSeekWhenPrepared;
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }

            if (mTargetState == STATE_PLAYING) {
                start();
            } else {
                if (pausedAt(seekToPosition)) {
                }
            }
        }
    };

    private boolean pausedAt(final int seekToPosition) {
        return !isPlaying() && (seekToPosition != 0 || getCurrentPosition() > 0);
    }

    private final MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {

        @Override
        public void onCompletion(final MediaPlayer mp) {
            setKeepScreenOn(false);
            mCurrentState = STATE_PLAYBACK_COMPLETED;
            mTargetState = STATE_PLAYBACK_COMPLETED;
            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mMediaPlayer);
            }
        }
    };

    private final MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(final MediaPlayer mp, final int arg1,
                              final int arg2) {
            if (mOnInfoListener != null) {
                mOnInfoListener.onInfo(mp, arg1, arg2);
            }
            return true;
        }
    };

    private final MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(final MediaPlayer mp, final int frameworkError,
                               final int implError) {
            Log.d(TAG, "Error: " + frameworkError + "," + implError);
            if (mCurrentState == STATE_ERROR) {
                return true;
            }
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;

            if (allowPlayStateToHandle(frameworkError)) {
                return true;
            }

            if (allowErrorListenerToHandle(frameworkError, implError)) {
                return true;
            }

            handleError(frameworkError);
            return true;
        }
    };

    private boolean allowPlayStateToHandle(final int frameworkError) {
        if (frameworkError == MediaPlayer.MEDIA_ERROR_UNKNOWN
                || frameworkError == MediaPlayer.MEDIA_ERROR_IO) {
            Log.e(TAG,
                    "TextureVideoView error. File or network related operation errors.");
            if (hasPlayStateListener()) {
                return onPlayStateListener.onStopWithExternalError(mMediaPlayer.getCurrentPosition()
                        / MILLIS_IN_SEC);
            }
        }
        return false;
    }

    private boolean allowErrorListenerToHandle(final int frameworkError,
                                               final int implError) {
        if (mOnErrorListener != null) {
            mOnErrorListener.onError(mMediaPlayer, frameworkError, implError);
        }

        return false;
    }

    private void handleError(final int frameworkError) {
        getErrorMessage(frameworkError);
    }

    private static int getErrorMessage(final int frameworkError) {
        int messageId = R.string.play_error_message;

        if (frameworkError == MediaPlayer.MEDIA_ERROR_IO) {
            Log.e(TAG,
                    "TextureVideoView error. File or network related operation errors.");
        } else {
            if (frameworkError == MediaPlayer.MEDIA_ERROR_MALFORMED) {
                Log.e(TAG,
                        "TextureVideoView error. Bitstream is not conforming to the related coding standard or file spec.");
            } else {
                if (frameworkError == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                    Log.e(TAG,
                            "TextureVideoView error. Media server died. In this case, the application must release the MediaPlayer object and instantiate a new one.");
                } else {
                    if (frameworkError == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                        Log.e(TAG,
                                "TextureVideoView error. Some operation takes too long to complete, usually more than 3-5 seconds.");
                    } else {
                        if (frameworkError == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
                            Log.e(TAG,
                                    "TextureVideoView error. Unspecified media player error.");
                        } else {
                            if (frameworkError == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
                                Log.e(TAG,
                                        "TextureVideoView error. Bitstream is conforming to the related coding standard or file spec, but the media framework does not support the feature.");
                            } else {
                                if (frameworkError == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                                    Log.e(TAG,
                                            "TextureVideoView error. The video is streamed and its container is not valid for progressive playback i.e the video's index (e.g moov atom) is not at the start of the file.");
                                    messageId = R.string.play_progressive_error_message;
                                }
                            }
                        }
                    }
                }
            }
        }
        return messageId;
    }

    private final MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(final MediaPlayer mp, final int percent) {
            mCurrentBufferPercentage = percent;
        }
    };

    private boolean noPlayStateListener() {
        return !hasPlayStateListener();
    }

    private boolean hasPlayStateListener() {
        return onPlayStateListener != null;
    }

    private final MediaPlayer.OnInfoListener onInfoToPlayStateListener = new MediaPlayer.OnInfoListener() {

        @Override
        public boolean onInfo(final MediaPlayer mp, final int what,
                              final int extra) {
            if (noPlayStateListener()) {
                return false;
            }

            if (MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START == what) {
                onPlayStateListener.onFirstVideoFrameRendered();
                onPlayStateListener.onPlay();
            }
            if (MediaPlayer.MEDIA_INFO_BUFFERING_START == what) {
                onPlayStateListener.onBuffer();
            }
            if (MediaPlayer.MEDIA_INFO_BUFFERING_END == what) {
                onPlayStateListener.onPlay();
            }

            return false;
        }
    };

    /*
     * User Listeners
     */

    /**
     * Register a callback to be invoked when the media file is loaded and ready
     * to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(final MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file has been
     * reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(final MediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs during playback or
     * setup. If no listener is specified, or if the listener returned false,
     * VideoView will inform the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(final MediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event occurs
     * during playback or setup.
     *
     * @param l The callback that will be run
     */
    private void setOnInfoListener(final MediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    public void setOnPlayStateListener(final OnPlayStateListener onPlayStateListener) {
        this.onPlayStateListener = onPlayStateListener;
    }

    public interface OnPlayStateListener {
        void onFirstVideoFrameRendered();

        void onPlay();

        void onBuffer();

        boolean onStopWithExternalError(int position);
    }

    /*
     * SurfaceTexture Listener
     */

    private final SurfaceTextureListener mSTListener = new SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(final SurfaceTexture surface,
                                              final int width, final int height) {
            mSurfaceTexture = surface;
            openVideo();
        }

        @Override
        public void onSurfaceTextureSizeChanged(final SurfaceTexture surface,
                                                final int width, final int height) {
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = videoSizeCalculator.currentSizeIs(width,
                    height);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
            mSurfaceTexture = null;
            release(true);
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
            mSurfaceTexture = surface;
        }
    };

    /*
     * release the media player in any state
     */
    private void release(final boolean clearTargetState) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (clearTargetState) {
                mTargetState = STATE_IDLE;
            }
        }
    }

}
