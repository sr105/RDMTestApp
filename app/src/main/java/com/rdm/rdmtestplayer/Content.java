package com.rdm.rdmtestplayer;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
 * A CSV formatted content list is loaded from an HTTP url. Then,
 * content is synced locally based on the information in the list.
 * Format:
 *     filepath relative to base url,size in bytes,md5 hash
 */
class Content {
    private static final String TAG = "Content";

    // TODO: use getExternalFilesDir() or similar here for BASE_PATH
    private static final String BASE_PATH = "/sdcard/media/";
    private static final String ENDPOINT = "http://3gfp.com/i/rdm_test_media/";
    private static final String CONTENT_LIST = "content_list";
    private static final int NUM_CONTENT_LINE_PARTS = 3;

    private boolean mUpToDate = false;
    private String mRemotePath;
    String mLocalPath;
    private long mSizeInBytes;
    private String mMd5String;

    public static String getContentListUrl() {
        return ENDPOINT + CONTENT_LIST;
    }

    public static String getLocalContentListUrl() {
        return BASE_PATH + CONTENT_LIST;
    }

    public Content(final String contentLine) throws IllegalArgumentException {
        final String[] parts = contentLine.split(",");
        if (parts.length < Content.NUM_CONTENT_LINE_PARTS)
            throw new IllegalArgumentException("Invalid content line: " + contentLine);

        final String fileName = parts[0];
        mRemotePath = ENDPOINT + Uri.encode(fileName, "/");
        mLocalPath = BASE_PATH + fileName;
        mSizeInBytes = Long.valueOf(parts[1]);
        mMd5String = parts[2];

//        Log.i(TAG, "New: " + mRemotePath
//                + "\n    " + mLocalPath
//                + "\n    " + mSizeInBytes
//                + "\n    " + mMd5String);
    }

    public boolean needsUpdate() {
        if (mUpToDate)
            return false;

        File file = new File(mLocalPath);
        if (!file.isFile())
            return true;
        if (file.length() != mSizeInBytes)
            return true;
        if (!getMd5String(file).equals(mMd5String))
            return true;

        mUpToDate = true;
        return false;
    }

    public void sync() throws IOException {
        if (needsUpdate()) {
            download();
        }
    }

    private void download() throws IOException {
        File file = new File(mLocalPath);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (!ContentSync.getUrlBytes(mRemotePath, new FileOutputStream(file), mSizeInBytes)) {
            Log.e(TAG, "Failed to download: " + mRemotePath);
        }
        // Update up-to-date status
        needsUpdate();
    }

    private static String getMd5String(File file) {
        byte[] hash = getMd5(file);
        String result = "";
        for (byte hashByte : hash != null ? hash : new byte[0]) {
            result += Integer.toString((hashByte & 0xff) + 0x100, 16).substring(1);
        }
        //Log.i(TAG, "MD5: " + file.getName() + "  " + result);
        return result;
    }

    private static byte[] getMd5(File file) {
        MessageDigest md = null;
        DigestInputStream dis = null;
        try {
            md = MessageDigest.getInstance("MD5");
            dis = new DigestInputStream(new FileInputStream(file), md);
            /* Read stream to EOF as normal... */
            byte[] buffer = new byte[32 * 1024];
            int bytesRead = 0;
            while (bytesRead != -1) {
                bytesRead = dis.read(buffer);
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "", e);
            return null;
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch (IOException ignored) {
            }
        }

        return md.digest();
    }

}
