package com.rdm.rdmtestplayer;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.VideoView;


public class PlayerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        ViewGroup.LayoutParams params
                = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        VideoView videoView = new VideoView(this);
        FrameLayout rootFrame = (FrameLayout)findViewById(R.id.rootFrame);
        rootFrame.addView(videoView, params);

        videoView.setVideoURI(Uri.parse("/sdcard/media/BeerDemo.mp4"));
        videoView.start();
    }

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
