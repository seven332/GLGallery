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

import android.graphics.Rect;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;

import com.hippo.glview.image.ImageTexture;
import com.hippo.glview.view.GLRootView;
import com.hippo.image.ImageData;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// TODO Set provider ?
public class ProviderAdapter extends GalleryView.Adapter implements GalleryProvider.Listener {

    private static final String LOG_TAG = ProviderAdapter.class.getSimpleName();

    @IntDef({CLIP_NONE, CLIP_LEFT_RIGHT, CLIP_RIGHT_LEFT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Clip {}

    public static final int CLIP_NONE = 0;
    public static final int CLIP_LEFT_RIGHT = 1;
    public static final int CLIP_RIGHT_LEFT = 2;

    private static final float CLIP_LIMIT = 1.3f;

    private final GalleryProvider mProvider;
    private final ImageTexture.Uploader mUploader;

    @Clip
    private int mClip = CLIP_NONE;
    private boolean mShowIndex = true;

    private int mSize;
    private boolean[] mClipArray;
    // mId = (mIndex << 1) || (mClipIndex ? 1 : 0)
    private int mId;
    private int mIndex;
    // false for first one, true for second one
    private boolean mClipIndex;

    private final Rect mTemp = new Rect();

    public ProviderAdapter(@NonNull GLRootView glRootView, @NonNull GalleryProvider provider) {
        mProvider = provider;
        provider.setGLRoot(glRootView);
        provider.setListener(this);
        mUploader = new ImageTexture.Uploader(glRootView);
        mSize = provider.getSize();
        ensureClipArray(false);
    }

    private void ensureClipArray(boolean update) {
        if (mSize > 0) {
            if (mClipArray != null || update) {
                // Default false
                mClipArray = new boolean[mSize];
            }
        }
    }

    public void clearUploader() {
        mUploader.clear();
    }

    public void setClip(@Clip int clip) {
        if (mClip != clip) {
            mClip = clip;
            if (isAttached()) {
                notifyDataChanged();
            }
        }
    }

    public void setShowIndex(boolean showIndex) {
        if (mShowIndex != showIndex) {
            mShowIndex = showIndex;
            if (isAttached()) {
                notifyDataChanged();
            }
        }
    }

    @Override
    public void next() {
        if (mSize <= 0 || mClipArray == null) {
            throw new IllegalStateException("Can't next, not data now");
        }

        int newId = mId + 1;
        int newIndex = newId >> 1;
        boolean newClipIndex = (newId & 1) != 0;

        if (newIndex >= mSize) {
            throw new IllegalStateException("Can't next, out of range");
        }

        // Fix
        if (newClipIndex && !mClipArray[newIndex]) {
            if (++newIndex >= mSize) {
                throw new IllegalStateException("Can't next, out of range");
            }
            newClipIndex = false;
            newId = newIndex << 1;
        }

        mId = newId;
        mIndex = newIndex;
        mClipIndex = newClipIndex;
    }

    @Override
    public void previous() {
        if (mSize <= 0 || mClipArray == null) {
            throw new IllegalStateException("Can't previous, not data now");
        }

        int newId = mId - 1;
        final int newIndex = newId >> 1;
        boolean newClipIndex = (newId & 1) != 0;

        if (newIndex < 0) {
            throw new IllegalStateException("Can't previous, out of range");
        }

        // Fix clip index
        if (newClipIndex && !mClipArray[newIndex]) {
            newClipIndex = false;
            newId = newIndex << 1;
        }

        mId = newId;
        mIndex = newIndex;
        mClipIndex = newClipIndex;
    }

    @Override
    public boolean hasNext() {
        return !isTail(mIndex, mClipIndex);
    }

    @Override
    public boolean hasPrevious() {
        return !isHead(mIndex, mClipIndex);
    }

    @Override
    public int getCurrentId() {
        return mId;
    }

    @Override
    protected boolean setCurrentId(int id) {
        if (mSize <= 0 || mClipArray == null) {
            Log.e(LOG_TAG, "Should not call setCurrentId when data is not ready");
            return false;
        }

        final int index = id >> 1;
        final boolean clipIndex = (id & 1) != 0;

        if (index >= 0 && index < mSize && (!clipIndex || mClipArray[index])) {
            mId = id;
            mIndex = index;
            mClipIndex = clipIndex;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isHead(int id) {
        final int index = id >> 1;
        final boolean clipIndex = (id & 1) != 0;
        return isHead(index, clipIndex);
    }

    @Override
    public boolean isTail(int id) {
        final int index = id >> 1;
        final boolean clipIndex = (id & 1) != 0;
        return isTail(index, clipIndex);
    }

    private boolean isHead(int index, boolean clipIndex) {
        if (mSize <= 0 || mClipArray == null) {
            Log.e(LOG_TAG, "Should not call isHead when data is not ready");
            return true;
        }
        return index == 0 && !clipIndex;
    }

    private boolean isTail(int index, boolean clipIndex) {
        if (mSize <= 0 || mClipArray == null) {
            Log.e(LOG_TAG, "Should not call isTail when data is not ready");
            return true;
        }
        return index == mSize - 1 && (clipIndex || !mClipArray[index]);
    }

    @Override
    public void onBind(GalleryPageView view) {
        mProvider.request(mIndex);
        view.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, mShowIndex, mIndex);
    }

    @Override
    public void onUnbind(GalleryPageView view, int id) {
        mProvider.cancelRequest(id >> 1);
        view.clear();
    }

    @Override
    public String getError() {
        return mProvider.getError();
    }

    @Override
    public int getState() {
        final int size = mSize;
        if (size == GalleryProvider.STATE_ERROR) {
            return STATE_ERROR;
        } else if (size == GalleryProvider.STATE_WAIT) {
            return STATE_WAIT;
        } else if (size == 0) {
            return STATE_EMPTY;
        } else if (size > 0) {
            return STATE_READY;
        } else {
            throw new IllegalStateException("Invalid size: " + size);
        }
    }

    @Override
    public void onStateChanged() {
        // Update size
        mSize = mProvider.getSize();
        ensureClipArray(true);
        notifyStateChanged();
    }

    @Override
    public void onPageWait(int index) {
        if (mSize <= 0 || mClipArray == null) {
            // onStateChanged() has not been called
            return;
        }

        final GalleryPageView page1 = findPageById(index << 1);
        final GalleryPageView page2 = mClipArray[index] ? findPageById(index << 1 | 1) : null;
        if (page1 != null) {
            page1.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, mShowIndex, index);
        }
        if (page2 != null) {
            page2.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, mShowIndex, index);
        }
    }

    @Override
    public void onPagePercent(int index, float percent) {
        if (mSize <= 0 || mClipArray == null) {
            // onStateChanged() has not been called
            return;
        }

        final GalleryPageView page1 = findPageById(index << 1);
        final GalleryPageView page2 = mClipArray[index] ? findPageById(index << 1 | 1) : null;
        if (page1 != null) {
            page1.showProgress(percent, mShowIndex, index);
        }
        if (page2 != null) {
            page2.showProgress(percent, mShowIndex, index);
        }
    }

    @Override
    public void onPageSucceed(int index, ImageData image) {
        if (mSize <= 0 || mClipArray == null) {
            // onStateChanged() has not been called
            return;
        }

        // Check clip
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean newClip = mClip != CLIP_NONE && (float) width / (float) height >= CLIP_LIMIT;
        final boolean oldClip = mClipArray[index];
        if ((!oldClip && newClip) || (oldClip && !newClip)) {
            notifyDataChanged();
        }
        mClipArray[index] = newClip;

        final GalleryPageView page1 = findPageById(index << 1);
        final GalleryPageView page2 = newClip ? findPageById(index << 1 | 1) : null;

        if (page1 != null || page2 != null) {
            final Rect temp = mTemp;
            if (page1 != null) {
                // Upload the texture
                final ImageTexture imageTexture = new ImageTexture(image);
                mUploader.addTexture(imageTexture);
                // Get clip rect
                if (!newClip || mClip == CLIP_NONE) {
                    temp.set(0, 0, width, height);
                } else if (mClip == CLIP_LEFT_RIGHT) {
                    temp.set(0, 0, width / 2, height);
                } else if (mClip == CLIP_RIGHT_LEFT) {
                    temp.set(width / 2, 0, width - width / 2, height);
                } else {
                    throw new IllegalStateException("Invalid clip: " + mClip);
                }
                page1.showImage(imageTexture, temp);
            }
            if (page2 != null) {
                // Upload the texture
                final ImageTexture imageTexture = new ImageTexture(image);
                mUploader.addTexture(imageTexture);
                // Get clip rect
                if (mClip == CLIP_NONE) {
                    temp.set(0, 0, width, height);
                } else if (mClip == CLIP_LEFT_RIGHT) {
                    temp.set(width / 2, 0, width - width / 2, height);
                } else if (mClip == CLIP_RIGHT_LEFT) {
                    temp.set(0, 0, width / 2, height);
                } else {
                    throw new IllegalStateException("Invalid clip: " + mClip);
                }
                page2.showImage(imageTexture, temp);
            }
        }
    }

    @Override
    public void onPageFailed(int index, String error) {
        if (mSize <= 0 || mClipArray == null) {
            // onStateChanged() has not been called
            return;
        }

        final GalleryPageView page1 = findPageById(index << 1);
        final GalleryPageView page2 = mClipArray[index] ? findPageById(index << 1 | 1) : null;
        if (page1 != null) {
            page1.showError(error, mShowIndex, index);
        }
        if (page2 != null) {
            page2.showError(error, mShowIndex, index);
        }
    }

    @Override
    public void onDataChanged(int index) {
        if (mSize <= 0 || mClipArray == null) {
            // onStateChanged() has not been called
            return;
        }

        final GalleryPageView page1 = findPageById(index << 1);
        final GalleryPageView page2 = mClipArray[index] ? findPageById(index << 1 | 1) : null;
        if (page1 != null || page2 != null) {
            mProvider.request(index);
        }
    }

    private GalleryPageView findPageById(int id) {
        return mGalleryView != null ? mGalleryView.findPageById(id) : null;
    }
}
