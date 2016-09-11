package com.hippo.glgallery.example;

/*
 * Created by Hippo on 8/28/2016.
 */

import android.app.Activity;
import android.support.annotation.IdRes;

import com.hippo.glview.view.GLRoot;

public abstract class GLActivity extends Activity implements GLRoot.RendererListener {

    private GLRoot mGLRoot;

    @IdRes
    protected abstract int getGLRootViewId();

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (mGLRoot != null) {
            throw new IllegalStateException("Can't set content twice");
        }
        mGLRoot = (GLRoot) findViewById(getGLRootViewId());
        if (mGLRoot == null) {
            throw new IllegalStateException("Can't find GLRootView");
        }
        mGLRoot.setRendererListener(this);
        mGLRoot.applyRenderer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLRoot.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLRoot.onPause();
    }

    public GLRoot getGLRoot() {
        return mGLRoot;
    }

    @Override
    public void onSurfaceCreated() {}

    @Override
    public void onSurfaceChanged() {}

    @Override
    public void onDrawFrame() {}

    @Override
    public void onGLThreadStart() {}

    @Override
    public void onGLThreadExit() {}

    @Override
    public void onGLThreadPause() {}

    @Override
    public void onGLThreadResume() {}
}
