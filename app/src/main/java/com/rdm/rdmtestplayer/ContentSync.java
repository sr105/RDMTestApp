package com.rdm.rdmtestplayer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

class ContentSync {
    private static final String TAG = "ContentSync";

    private static List<Content> sContentList;

    interface OnSyncProgressListener {
        void setNumberOfDownloads(int numberOfDownloads);

        void downloadStarted(String url, long totalBytes);

        void downloadProgress(long totalBytesRead);

        void downloadFinished();
    }

    private static OnSyncProgressListener sOnSyncProgressListener;

    public static void sync(OnSyncProgressListener onSyncProgressListener,
                            String downloadPath) throws IOException {
        Content.setDownloadPath(downloadPath);
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

    private static void parseRemoteContentList() {
        try {
            String list = getUrl(Content.getContentListUrl());
            parseContentList(new StringReader(list));
            // update local copy, if parsed successfully
            if (!sContentList.isEmpty())
                dumpStringToFile(list, new File(Content.getLocalContentListUrl()));
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
    }

    private static void parseLocalContentList() {
        try {
            parseContentList(new FileReader(new File(Content.getLocalContentListUrl())));
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
    }

    private static void parseContentList(Reader reader) {
        sContentList = new ArrayList<>();
        try {
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    sContentList.add(new Content(line));
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "", e);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
    }

    private static List<Content> getContentList() {
        parseRemoteContentList();
        if (sContentList.isEmpty())
            parseLocalContentList();
        return sContentList;
    }

    private static byte[] getUrlBytes(String urlSpec) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (getUrlBytes(urlSpec, out, 0))
            return out.toByteArray();
        return new byte[0];
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

            int bytesRead;
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

    private static String getUrl(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    private static void downloadStarted(String urlSpec, long totalBytes) {
        if (sOnSyncProgressListener != null)
            sOnSyncProgressListener.downloadStarted(urlSpec, totalBytes);
        //Log.i(TAG, "Downloading: " + urlSpec);
    }

    private static void downloadProgress(long bytes) {
        if (sOnSyncProgressListener != null)
            sOnSyncProgressListener.downloadProgress(bytes);
    }

    @SuppressWarnings("UnusedParameters")
    private static void downloadFinished(long bytes) {
        //Log.i(TAG, "Finished, bytes = " + bytes);
        if (sOnSyncProgressListener != null)
            sOnSyncProgressListener.downloadFinished();
    }

    public static void dumpStringToFile(String text, File file)
    {
        FileOutputStream out = null;
        try
        {
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            out = new FileOutputStream(file);
            out.write(text.getBytes());
            out.close();
        }
        catch (IOException e)
        {
            Log.w(Thread.currentThread().getName(), e);
        }
        finally
        {
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException ignored)
                {
                }
            }
        }
    }

}
