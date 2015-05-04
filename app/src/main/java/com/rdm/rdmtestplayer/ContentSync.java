package com.rdm.rdmtestplayer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ContentSync {
    private static final String TAG = "ContentSync";

    private static List<Content> sContentList;

    interface OnSyncProgressListener {
        void setNumberOfDownloads(int numberOfDownloads);

        void downloadStarted(String url, long totalBytes);

        void downloadProgress(long totalBytesRead);

        void downloadFinished();
    }

    private static OnSyncProgressListener sOnSyncProgressListener;

    public static void sync(OnSyncProgressListener onSyncProgressListener) throws IOException {
        sOnSyncProgressListener = onSyncProgressListener;
        List<Content> contentList = getContentList();
        if (sOnSyncProgressListener != null) {
            int numberToDownload = contentList.size() - getLocalContentList().size();
            sOnSyncProgressListener.setNumberOfDownloads(numberToDownload);
        }
        for (Content content : contentList) {
            content.sync();
        }
    }

    public static List<String> getLocalContentList() {
        if (sContentList == null)
            return new ArrayList<>();

        List<String> localList = new ArrayList<>();
        for (Content content : sContentList) {
            if (!content.needsUpdate())
                localList.add(content.mLocalPath);
        }
        return localList;
    }

    public static List<Content> getContentList() throws IOException {
        sContentList = new ArrayList<>();
        String list = getUrl(Content.getContentListUrl());
        StringReader stringReader = new StringReader(list);
        BufferedReader bufferedReader = new BufferedReader(stringReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            try {
                sContentList.add(new Content(line));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "", e);
            }
        }

        return sContentList;
    }

    public static byte[] getUrlBytes(String urlSpec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (getUrlBytes(urlSpec, out, 0))
            return out.toByteArray();
        return null;
    }

    // outputStream will be closed
    public static boolean getUrlBytes(String urlSpec, OutputStream outputStream, long totalBytes) throws IOException {
        if (urlSpec == null || outputStream == null)
            return false;
        downloadStarted(urlSpec, totalBytes);
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return false;

            int bytesRead = 0;
            long totalBytesRead = 0;
            byte[] buffer = new byte[32 * 1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                totalBytesRead += bytesRead;
                downloadProgress(totalBytesRead);
                outputStream.write(buffer, 0, bytesRead);
            }
            downloadFinished(totalBytesRead);
            return true;
        } finally {
            outputStream.close();
            connection.disconnect();
        }
    }

    public static String getUrl(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    private static void downloadStarted(String urlSpec, long totalBytes) {
        if (sOnSyncProgressListener != null)
            sOnSyncProgressListener.downloadStarted(urlSpec, totalBytes);
        Log.i(TAG, "Downloading: " + urlSpec);
    }

    private static void downloadProgress(long bytes) {
        if (sOnSyncProgressListener != null)
            sOnSyncProgressListener.downloadProgress(bytes);
    }

    private static void downloadFinished(long bytes) {
        Log.i(TAG, "Finished, bytes = " + bytes);
        if (sOnSyncProgressListener != null)
            sOnSyncProgressListener.downloadFinished();
    }

}
