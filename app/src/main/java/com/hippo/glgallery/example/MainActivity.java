package com.hippo.glgallery.example;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;

import com.hippo.glgallery.GalleryProvider;
import com.hippo.glgallery.GalleryView;
import com.hippo.glgallery.SimpleAdapter;
import com.hippo.glgallery.SimpleProviderListener;
import com.hippo.glview.view.GLRootView;
import com.hippo.unifile.UniFile;

import java.io.File;

public class MainActivity extends Activity {

    private GalleryProvider mGalleryProvider;
    private SimpleProviderListener mProviderListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GLRootView glRootView = (GLRootView) findViewById(R.id.gl_root);

        mGalleryProvider = new DirGalleryProvider(getResources(),
                UniFile.fromFile(new File(Environment.getExternalStorageDirectory(), "GLGallery")));
        GalleryView galleryView = new GalleryView.Builder(this, new SimpleAdapter(mGalleryProvider))
                .setLayoutMode(GalleryView.LAYOUT_TOP_TO_BOTTOM)
                .build();
        mProviderListener = new SimpleProviderListener(glRootView, galleryView, mGalleryProvider);

        mGalleryProvider.setListener(mProviderListener);
        mGalleryProvider.setGLRoot(glRootView);

        glRootView.setContentPane(galleryView);

        mGalleryProvider.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGalleryProvider.stop();
        mProviderListener.clearUploader();
    }
}
