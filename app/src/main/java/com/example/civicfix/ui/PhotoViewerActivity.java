package com.example.civicfix.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.civicfix.databinding.ActivityPhotoViewerBinding;

public class PhotoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "photo_url";
    public static final String EXTRA_TITLE = "photo_title";

    private ActivityPhotoViewerBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityPhotoViewerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        // Full-screen: hide the status and navigation bars (swipe from the edge to show them)
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }

        b.tvTitle.setText(title != null ? title : "");
        b.btnClose.setOnClickListener(v -> finish());
        b.zoomImage.setOnTapListener(this::toggleControls);

        // Load the full-size photo so zooming stays sharp
        Glide.with(this)
                .load(url)
                .override(Target.SIZE_ORIGINAL)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Drawable> target, boolean isFirstResource) {
                        b.progress.setVisibility(View.GONE);
                        b.tvError.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> target, DataSource dataSource,
                                                   boolean isFirstResource) {
                        b.progress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(b.zoomImage);
    }

    private void toggleControls() {
        boolean show = b.topBar.getVisibility() != View.VISIBLE;
        b.topBar.setVisibility(show ? View.VISIBLE : View.GONE);
        b.tvHint.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}