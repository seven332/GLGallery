package com.hippo.glgallery.example;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;

import com.hippo.glgallery.GalleryProvider;
import com.hippo.glgallery.GalleryView;
import com.hippo.glgallery.ProviderAdapter;
import com.hippo.glview.image.ImageTexture;
import com.hippo.glview.view.GLRoot;
import com.hippo.image.Image;
import com.hippo.unifile.UniFile;
import com.hippo.yorozuya.LayoutUtils;

import java.io.File;

public class MainActivity extends GLActivity implements GalleryView.Listener {

    private GalleryProvider mGalleryProvider;
    private ProviderAdapter mAdapter;

    @Override
    protected int getGLRootViewId() {
        return R.id.gl_root;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onGLThreadStart() {
        // Must create buffer before any image render
        Image.createBuffer(ImageTexture.LARGEST_TILE_SIZE * ImageTexture.LARGEST_TILE_SIZE);

        final GLRoot glRoot = getGLRoot();

        mGalleryProvider = new DirGalleryProvider(getResources(),
                UniFile.fromFile(new File(Environment.getExternalStorageDirectory(), "GLGallery")));
        mAdapter = new ProviderAdapter(glRoot, mGalleryProvider, 0, 3);
        mAdapter.setShowIndex(true);
        mAdapter.setClipMode(ProviderAdapter.CLIP_RIGHT_LEFT);

        final GalleryView.Builder builder = new GalleryView.Builder(this, glRoot);
        builder.layoutMode = GalleryView.LAYOUT_SCROLL_TOP_TO_BOTTOM;

        builder.backgroundColor = 0xff212121;
        builder.edgeColor = 0x333f51b5;

        builder.pagerInterval = LayoutUtils.dp2pix(this, 24);
        builder.scrollInterval = LayoutUtils.dp2pix(this, 24);
        builder.pageMinHeight = LayoutUtils.dp2pix(this, 256);
        builder.pageInfoInterval = LayoutUtils.dp2pix(this, 24);

        builder.progressColor = 0xffffd740;
        builder.progressSize = LayoutUtils.dp2pix(this, 56);
        builder.indexTextColor = 0x61ffffff;
        builder.indexTextSize = LayoutUtils.dp2pix(this, 56);
        builder.indexTextTypeface = Typeface.DEFAULT;

        builder.errorTextColor= 0xfff44336;
        builder.errorTextSize = LayoutUtils.dp2pix(this, 24);

        builder.defaultErrorString = "Weird";
        builder.emptyString = "Empty";

        final GalleryView galleryView = builder.build();
        galleryView.setAdapter(mAdapter);

        glRoot.setContentPane(galleryView);

        mGalleryProvider.start();
    }

    @Override
    public void onGLThreadExit() {
        Image.destroyBuffer();

        mGalleryProvider.stop();
        mAdapter.clearUploader();
    }

    @Override
    public void onUpdateCurrentId(long index) {

    }

    @Override
    public void onClick(float x, float y) {

    }

    @Override
    public void onDoubleClick(float x, float y) {

    }

    @Override
    public void onLongClick(float x, float y) {

    }
}
