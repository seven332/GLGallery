/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.glgallery;

/*
 * Created by Hippo on 8/31/2016.
 */

import android.support.annotation.NonNull;
import android.util.Log;

import com.hippo.glview.glrenderer.BasicTexture;
import com.hippo.glview.glrenderer.StringTexture;
import com.hippo.glview.glrenderer.Texture;
import com.hippo.glview.widget.GLTextureView;

/**
 * A LayoutManager that only show an error string in center.
 */
public class ErrorLayoutManager extends StaticLayoutManager {

    private static final String LOG_TAG = ErrorLayoutManager.class.getSimpleName();

    private final float mErrorTextSize;
    private final int mErrorTextColor;
    private final GLTextureView mGLTextureView;

    public ErrorLayoutManager(@NonNull GalleryView galleryView, float errorTextSize, int errorTextColor) {
        super(galleryView);
        mErrorTextSize = errorTextSize;
        mErrorTextColor = errorTextColor;
        mGLTextureView = new GLTextureView();
    }

    String getErrorString() {
        switch (mAdapter.getState()) {
            case GalleryView.Adapter.STATE_ERROR:
                final String errorString = mAdapter.getError();
                return errorString != null ? errorString : mGalleryView.getDefaultErrorStr();
            case GalleryView.Adapter.STATE_EMPTY:
                return mGalleryView.getEmptyStr();
            case GalleryView.Adapter.STATE_READY:
            case GalleryView.Adapter.STATE_WAIT:
            default:
                Log.e(LOG_TAG, "Should not get error string when no error.");
                return mGalleryView.getDefaultErrorStr();
        }
    }

    @Override
    protected void addViews() {
        final String errorString = getErrorString();
        final Texture texture = StringTexture.newInstance(
                errorString, mErrorTextSize, mErrorTextColor);
        mGLTextureView.setTexture(texture);
        mGalleryView.addComponent(mGLTextureView);
    }

    @Override
    protected void removeViews() {
        mGalleryView.removeComponent(mGLTextureView);
        // Release texture
        final Texture texture = mGLTextureView.getTexture();
        if (texture != null) {
            mGLTextureView.setTexture(null);
            if (texture instanceof BasicTexture) {
                ((BasicTexture) texture).recycle();
            }
        }
    }

    @Override
    public void onFill() {
        placeCenter(mGLTextureView);
    }
}
