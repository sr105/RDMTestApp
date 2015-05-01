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

    interface OnProgressUpdateListener {
        void downloadStarted(String url);

        void downloadProgress(double percentComplete);

        void downloadFinished();
    }

    private static OnProgressUpdateListener sOnProgressUpdateListener;

    public static void sync(OnProgressUpdateListener onProgressUpdateListener) throws Exception {
        sOnProgressUpdateListener = onProgressUpdateListener;
        for (Content content : getContentList()) {
            content.sync();
        }
    }

    public static List<String> getLocalContentList() {
        List<String> localList = new ArrayList<>();
        try {
            for (Content content : getContentList()) {
                if (!content.needsUpdate())
                    localList.add(content.mLocalPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return localList;
    }

    public static List<Content> getContentList() throws Exception {
        if (sContentList != null) {
            return sContentList;
        }
        sContentList = new ArrayList<>();
        String list = getUrl(Content.getContentListUrl());
        StringReader stringReader = new StringReader(list);
        BufferedReader bufferedReader = new BufferedReader(stringReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            try {
                sContentList.add(new Content(line));
            } catch (IllegalArgumentException ignored) {
                // Logged in Content constructor
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

    public static boolean getUrlBytes(String urlSpec, OutputStream outputStream, long totalBytes) throws IOException {
        if (sOnProgressUpdateListener != null)
            sOnProgressUpdateListener.downloadStarted(urlSpec);
        Log.i(TAG, "Downloading: " + urlSpec);
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        double totalBytesAsDouble = totalBytes / 100f;
        try {
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return false;

            int bytesRead = 0;
            long totalBytesRead = 0;
            double progress = 0f;
            long updateTime = 0;
            byte[] buffer = new byte[32 * 1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                totalBytesRead += bytesRead;
                if (sOnProgressUpdateListener != null && totalBytes > 0) {
                    double p = Math.ceil(totalBytesRead / totalBytesAsDouble);
                    if (p > progress && System.nanoTime() > updateTime) {
                        updateTime = System.nanoTime() + 1000000000L;
                        progress = p;
                        sOnProgressUpdateListener.downloadProgress(progress);
                    }
                }
                //Log.i(TAG, "bytes = " + totalBytesRead);
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            Log.i(TAG, "Finished, bytes = " + totalBytesRead);
            if (sOnProgressUpdateListener != null)
                sOnProgressUpdateListener.downloadFinished();
            return true;
        } finally {
            connection.disconnect();
        }
    }

    public static String getUrl(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

}
