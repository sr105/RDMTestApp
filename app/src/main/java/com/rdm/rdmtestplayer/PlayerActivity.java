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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class PlayerActivity extends Activity {
    private static final String TAG = "PlayerActivity";

    LinearLayout mProgressLayout;
    TextView mProgressTitle;
    TextView mProgressStepName;
    ProgressBar mStepProgress;
    ProgressBar mCompleteProgress;
    TextView mProgressErrors;

    VideoView mVideoView;

    List<Uri> mVideoUriList;
    private int mUriIndex = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        FrameLayout rootFrame = (FrameLayout) findViewById(R.id.rootFrame);

        mProgressLayout = (LinearLayout) findViewById(R.id.progressLayout);
        mProgressTitle = (TextView) findViewById(R.id.progressTitle);
        mProgressStepName = (TextView) findViewById(R.id.progressStepName);
        mStepProgress = (ProgressBar) findViewById(R.id.progressStepProgress);
        mCompleteProgress = (ProgressBar) findViewById(R.id.progressCompleteProgress);
        mProgressErrors = (TextView) findViewById(R.id.progressErrors);

        mProgressTitle.setText("Synchronizing Content...\n");

        mVideoView = new VideoView(this);
        mVideoView.setOnCompletionListener(mOnCompletionListener);
        mVideoView.setOnErrorListener(mOnErrorListener);

        rootFrame.addView(mVideoView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mVideoView.setVisibility(View.INVISIBLE);

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
            private final long UPDATE_INTERVAL_IN_NANOS = 1000000000L; // 1 second
            private long mNextUpdate = -1L;
            private double mTotalBytesAsDouble = 0.0d;
            private long mBytesPerPercentagePoint = 0L;
            private long mNextUpdateTime = 0L;

            @Override
            public void setNumberOfDownloads(int numberOfDownloads) {
                publishProgress("n", "" + numberOfDownloads);
            }

            @Override
            public void downloadStarted(String url, long totalBytes) {
                mNextUpdateTime = 0L;
                mNextUpdate = -1L;
                mTotalBytesAsDouble = totalBytes;
                if (totalBytes <= 0)
                    mBytesPerPercentagePoint = 0L;
                else
                    mBytesPerPercentagePoint = totalBytes / 100;
                publishProgress("d", url);
            }

            @Override
            public void downloadProgress(long bytes) {
                // Update if:
                // - we can compute a percentage
                // - one second has elapsed since the last update
                // - progress is at least one whole point greater than the last update
                if (mBytesPerPercentagePoint == 0
                        || System.nanoTime() < mNextUpdateTime
                        || bytes < mNextUpdate)
                    return;
                mNextUpdateTime = System.nanoTime() + UPDATE_INTERVAL_IN_NANOS;
                double progress = Math.ceil(100.0d * bytes / mTotalBytesAsDouble);
                mNextUpdate = mBytesPerPercentagePoint * (long) progress;
                publishProgress("p", "" + (long)progress);
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
            if (values[0].equals("n")) {
                mCompleteProgress.setMax(Integer.valueOf(values[1]));
                mCompleteProgress.setProgress(0);
            }
            if (values[0].equals("d")) {
                mProgressStepName.setText(values[1]);
                mStepProgress.setProgress(0);
                mCompleteProgress.incrementProgressBy(1);
                return;
            }
            if (values[0].equals("p")) {
                int value = Integer.valueOf(values[1]);
                mStepProgress.setProgress(value);
                return;
            }
            if (values[0].equals("e")) {
                mStepProgress.setProgress(100);
                return;
            }
            if (values[0].equals("x")) {
                mProgressErrors.append(values[1]);
                return;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ContentSync.sync(onProgressUpdateListener);
            } catch (Exception e) {
                publishProgress("x", e.getLocalizedMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            mVideoView.setVisibility(View.VISIBLE);
            mProgressLayout.setVisibility(View.GONE);
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
