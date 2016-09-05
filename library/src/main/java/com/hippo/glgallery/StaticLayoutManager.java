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
 * Created by Hippo on 8/30/2016.
 */

import android.support.annotation.NonNull;

import com.hippo.glview.view.GLView;

import junit.framework.Assert;

abstract class StaticLayoutManager extends GalleryView.LayoutManager {

    protected GalleryView.Adapter mAdapter;

    public StaticLayoutManager(@NonNull GalleryView galleryView) {
        super(galleryView);
    }

    @Override
    public void onAttach(GalleryView.Adapter adapter) {
        Assert.assertNull("The WaitingLayoutManager is attached", mAdapter);
        Assert.assertNotNull("The adapter is null", adapter);

        // Assign adapter
        mAdapter = adapter;

        addViews();
    }

    @Override
    public GalleryView.Adapter onDetach() {
        Assert.assertNotNull("The PagerLayoutManager is not attached", mAdapter);

        removeViews();

        // Clear iterator
        final GalleryView.Adapter adapter = mAdapter;
        mAdapter = null;

        return adapter;
    }

    // Add views to GalleryView
    protected abstract void addViews();

    // Remove views from GalleryView
    protected abstract void removeViews();

    @Override
    public void onDown() {}

    @Override
    public void onUp() {}

    @Override
    public void onScroll(float dx, float dy, float totalX, float totalY, float x, float y) {}

    @Override
    public void onFling(float velocityX, float velocityY) {}

    @Override
    public boolean canScale() {
        return false;
    }

    @Override
    public void onScale(float focusX, float focusY, float scale) {}

    @Override
    public void onScaleToNextLevel(float x, float y) {}

    @Override
    public boolean onUpdateAnimation(long time) {
        return false;
    }

    @Override
    public void onPageNext() {}

    @Override
    public void onPagePrevious() {}

    @Override
    public void onPageToId(long id) {}

    @Override
    public boolean isTouchActionValid() {
        return true;
    }

    @Override
    public GalleryPageView findPageById(long id) {
        return null;
    }

    @Override
    public long getIdUnder(float x, float y) {
        return 0;
    }

    @Override
    public long getCurrentId() {
        return GalleryView.Adapter.INVALID_ID;
    }

    @Override
    public void onDataChanged() {}

    /**
     * Place view in the center of GalleryView.
     */
    protected void placeCenter(GLView view) {
        final int widthSpec = GLView.MeasureSpec.makeMeasureSpec(
                view.getWidth(), GLView.LayoutParams.WRAP_CONTENT);
        final int heightSpec = GLView.MeasureSpec.makeMeasureSpec(
                view.getHeight(), GLView.LayoutParams.WRAP_CONTENT);
        view.measure(widthSpec, heightSpec);
        final int width = view.getMeasuredWidth();
        final int height = view.getMeasuredHeight();
        final int left = mGalleryView.getWidth() / 2 - width / 2;
        final int top = mGalleryView.getHeight() / 2 - height / 2;
        view.layout(left, top, left + width, top + height);
    }
}
