package com.hippo.glgallery.example;

/*
 * Created by Hippo on 8/28/2016.
 */

import android.app.Activity;
import android.support.annotation.IdRes;

import com.hippo.glview.view.GLRootView;

public abstract class GLActivity extends Activity implements GLRootView.RendererListener {

    private GLRootView mGLRootView;

    @IdRes
    protected abstract int getGLRootViewId();

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (mGLRootView != null) {
            throw new IllegalStateException("Can't set content twice");
        }
        mGLRootView = (GLRootView) findViewById(getGLRootViewId());
        if (mGLRootView == null) {
            throw new IllegalStateException("Can't find GLRootView");
        }
        mGLRootView.setRendererListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLRootView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLRootView.onPause();
    }

    public GLRootView getGLRootView() {
        return mGLRootView;
    }

    @Override
    public void onSurfaceCreated() {}

    @Override
    public void onSurfaceChanged() {}

    @Override
    public void onDrawFrame() {}

    @Override
    public void onGLThreadStarts() {}

    @Override
    public void onGLThreadExits() {}

    @Override
    public void onGLThreadPause() {}

    @Override
    public void onGLThreadResume() {}
}
