package com.rdm.rdmtestplayer;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Content {
    private static final String TAG = "Content";


    private static final String BASEPATH = "/sdcard/media/";
    private static final String ENDPOINT = "http://3gfp.com/i/rdm_test_media/";
    private static final String CONTENT_LIST = "content_list";
    public static final int NUM_CONTENT_LINE_PARTS = 2;

    boolean mUpToDate = false;
    String mRemotePath;
    String mLocalPath;
    long mSizeInBytes;
    // md5

    public static String getContentListUrl() {
        return ENDPOINT + CONTENT_LIST;
    }

    public Content(final String contentLine) {
        final String[] parts = contentLine.split(",");
        if (parts.length < Content.NUM_CONTENT_LINE_PARTS) {
            Log.w(TAG, "Invalid content line: " + contentLine);
            throw new IllegalArgumentException("Invalid content line: " + contentLine);
        }

        final String fileName = parts[0];
        mRemotePath = ENDPOINT + Uri.encode(fileName, "/");
        mLocalPath = BASEPATH + fileName;
        mSizeInBytes = Long.valueOf(parts[1]);

        Log.i(TAG, "New: " + mRemotePath + "\n" + "    " + mLocalPath + "\n" + "     " + mSizeInBytes);
    }

    public boolean needsUpdate() {
        if (mUpToDate)
            return false;

        File file = new File(mLocalPath);
        if (!file.isFile())
            return true;
        if (file.length() != mSizeInBytes)
            return true;

        mUpToDate = true;
        return false;
    }

    public void sync() throws IOException {
        if (needsUpdate()) {
            download();
        }
    }

    public void download() throws IOException {
        File file = new File(mLocalPath);
        file.getParentFile().mkdirs();
        if (!ContentSync.getUrlBytes(mRemotePath, new FileOutputStream(file), mSizeInBytes)) {
            Log.e(TAG, "Failed to download: " + mRemotePath);
        }
        // Update up-to-date status
        needsUpdate();
    }
}