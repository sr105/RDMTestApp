package com.rdm.rdmtestplayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class PlayerActivity extends Activity {
    private static final String TAG = "PlayerActivity";

    private interface VideoViewInterface {
        View getView();

        void setVideoURI(Uri uri);

        void setOnCompletionListener(MediaPlayer.OnCompletionListener l);

        void setOnErrorListener(MediaPlayer.OnErrorListener l);

        void start();

        void setVisibility(int visibility);
    }

    private class ExtendedVideoView extends VideoView implements VideoViewInterface {
        public ExtendedVideoView(Context context) {
            super(context);
        }

        public View getView() {
            return this;
        }
    }

    private class ExtendedSurfaceTextureVideoView extends SurfaceTextureVideoView implements VideoViewInterface {
        public ExtendedSurfaceTextureVideoView(Context context) {
            super(context);
        }

        public View getView() {
            return this;
        }
    }

    private VideoViewInterface mVideoViewInterface;
    private List<Uri> mVideoUriList;
    private int mUriIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        FrameLayout rootFrame = (FrameLayout) findViewById(R.id.rootFrame);

        mVideoViewInterface = new ExtendedSurfaceTextureVideoView(this);
//        mVideoViewInterface = new ExtendedVideoView(this);

        mVideoViewInterface.setOnCompletionListener(mOnCompletionListener);
        mVideoViewInterface.setOnErrorListener(mOnErrorListener);
        rootFrame.addView(mVideoViewInterface.getView(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mVideoViewInterface.setVisibility(View.INVISIBLE);

        new ContentSyncAsyncTask(this, mOnContentSyncFinishedRunnable).execute();
    }

    private final Runnable mOnContentSyncFinishedRunnable = new Runnable() {
        @Override
        public void run() {
            mVideoViewInterface.setVisibility(View.VISIBLE);
            populateVideoList();
            mOnCompletionListener.onCompletion(null);
        }
    };

    private void populateVideoList() {
        mVideoUriList = new ArrayList<>();
        for (String path : ContentSync.getLocalContentList()) {
            mVideoUriList.add(Uri.parse(path));
        }
    }

    private Uri getNextUri() {
        if (mVideoUriList.isEmpty()) {
            Toast.makeText(this, "No Content. Giving up.", Toast.LENGTH_LONG).show();
            finish();
            return Uri.EMPTY;
        }
        mUriIndex = (mUriIndex + 1) % mVideoUriList.size();
        return mVideoUriList.get(mUriIndex);
    }

    private final MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Uri uri = getNextUri();
                    Log.i(TAG, "Start: " + new File(uri.getPath()).getName());
                    mVideoViewInterface.setVideoURI(uri);
                    mVideoViewInterface.start();
                }
            };

    private final MediaPlayer.OnErrorListener mOnErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.e(TAG, "There was an error playing " + mVideoUriList.get(mUriIndex));
            // Returning false here will cause mOnCompletionListener.onCompletion()
            // to be called, but it will also popup an error dialog.
            mOnCompletionListener.onCompletion(mp);
            return true;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        hideNavigationBar();
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#ffffff"));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        hideNavigationBar();
    }

    private void hideNavigationBar() {
        // Everything else (full screen, hide status bar, etc.) is done by our theme
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

}
