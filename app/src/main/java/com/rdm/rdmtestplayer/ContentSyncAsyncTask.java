package com.rdm.rdmtestplayer;

import android.app.Activity;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ContentSyncAsyncTask extends AsyncTask<Void, String, Void> {

    private final Runnable mOnFinishedRunnable;

    private LinearLayout mProgressLayout;
    private TextView mProgressTitle;
    private TextView mProgressStepName;
    private ProgressBar mStepProgress;
    private ProgressBar mCompleteProgress;
    private TextView mProgressErrors;

    /**
     * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
     */
    public ContentSyncAsyncTask(Activity activity, Runnable onFinishedRunnable) {
        super();
        addToActivity(activity);
        mProgressLayout.setVisibility(View.GONE);
        mOnFinishedRunnable = onFinishedRunnable;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressLayout.setVisibility(View.VISIBLE);
    }

    public void addToActivity(Activity activity) {
        mProgressLayout = (LinearLayout) activity.getLayoutInflater().inflate(R.layout.progress, null);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        activity.addContentView(mProgressLayout, layoutParams);

        mProgressLayout = (LinearLayout) mProgressLayout.findViewById(R.id.progressLayout);
        mProgressTitle = (TextView) mProgressLayout.findViewById(R.id.progressTitle);
        mProgressStepName = (TextView) mProgressLayout.findViewById(R.id.progressStepName);
        mStepProgress = (ProgressBar) mProgressLayout.findViewById(R.id.progressStepProgress);
        mCompleteProgress = (ProgressBar) mProgressLayout.findViewById(R.id.progressCompleteProgress);
        mProgressErrors = (TextView) mProgressLayout.findViewById(R.id.progressErrors);

        mProgressTitle.setText("Synchronizing Content...\n");
    }

    public void removeFromActivity() {
        ViewGroup viewGroup = (ViewGroup) mProgressLayout.getParent();
        viewGroup.removeView(mProgressLayout);
    }

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
            ContentSync.sync(mOnSyncProgressListener);
        } catch (Exception e) {
            publishProgress("x", e.getLocalizedMessage());
            e.printStackTrace();
        }
        return null;
    }

    protected void finishUp() {
        removeFromActivity();
        if (mOnFinishedRunnable != null)
            mOnFinishedRunnable.run();
    }

    @Override
    protected void onCancelled() {
        finishUp();
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        finishUp();
    }

    ContentSync.OnSyncProgressListener mOnSyncProgressListener = new ContentSync.OnSyncProgressListener() {
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
            publishProgress("p", "" + (long) progress);
        }

        @Override
        public void downloadFinished() {
            publishProgress("e");
        }
    };
}
