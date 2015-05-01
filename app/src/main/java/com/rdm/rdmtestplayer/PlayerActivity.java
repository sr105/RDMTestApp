package com.rdm.rdmtestplayer;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class PlayerActivity extends Activity {
    private static final String TAG = "PlayerActivity";

    TextView mTextView;
    VideoView mVideoView;

    List<Uri> mVideoUriList;
    private int mUriIndex = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        FrameLayout rootFrame = (FrameLayout) findViewById(R.id.rootFrame);

        mVideoView = new VideoView(this);
        mVideoView.setOnCompletionListener(mOnCompletionListener);
        mVideoView.setOnErrorListener(mOnErrorListener);

        rootFrame.addView(mVideoView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mVideoView.setVisibility(View.INVISIBLE);

        mTextView = (TextView) findViewById(R.id.textView);
        mTextView.setText("Synchronizing Content...\n");
        mSyncAsyncTask.execute();
    }

    private void populateVideoList() {
        mVideoUriList = new ArrayList<Uri>();
        for (String path : ContentSync.getLocalContentList()) {
            mVideoUriList.add(Uri.parse(path));
        }
    }

    private Uri getNextUri() {
        mUriIndex = (mUriIndex + 1) % mVideoUriList.size();
        return mVideoUriList.get(mUriIndex);
    }

    private final MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Uri uri = getNextUri();
                    Log.i(TAG, "Start: " + new File(uri.getPath()).getName());
                    mVideoView.setVideoURI(uri);
                    mVideoView.start();
                }
            };

    private final MediaPlayer.OnErrorListener mOnErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.e(TAG, "There was an error playing " + mVideoUriList.get(mUriIndex));
            // Returning false here will cause mOnCompletionListener.onCompletion()
            // to be called, but it will also popup an error dialog.
            //return false;
            mOnCompletionListener.onCompletion(mp);
            return true;
        }
    };

    private final AsyncTask<Void, String, Void> mSyncAsyncTask = new AsyncTask<Void, String, Void>() {
        ContentSync.OnProgressUpdateListener onProgressUpdateListener = new ContentSync.OnProgressUpdateListener() {
            @Override
            public void downloadStarted(String url) {
                publishProgress("d", url);
            }

            @Override
            public void downloadProgress(double percentComplete) {
                publishProgress("p", String.format("%3.0f%%", percentComplete));
            }

            @Override
            public void downloadFinished() {
                publishProgress("e");
            }
        };

        protected String mPreviousText;

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if (values[0].equals("d")) {
                mTextView.setText(mTextView.getText().toString() + "\n" + "Downloading: " + values[1]);
                mPreviousText = mTextView.getText().toString();
                return;
            }
            if (values[0].equals("p")) {
                mTextView.setText(mPreviousText + "...  " + values[1]);
                return;
            }
            if (values[0].equals("e")) {
                mTextView.setText(mPreviousText + "\n");
                return;
            }

        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ContentSync.sync(onProgressUpdateListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            mVideoView.setVisibility(View.VISIBLE);
            populateVideoList();
            mOnCompletionListener.onCompletion(null);
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

    public void hideNavigationBar() {
        // Everything else (full screen, hide status bar, etc.) is done by our theme
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

}
