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

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import com.hippo.beerbelly.LruCache;
import com.hippo.beerbelly.LruCacheHelper;
import com.hippo.glview.annotation.RenderThread;
import com.hippo.glview.glrenderer.GLCanvas;
import com.hippo.glview.view.GLRoot;
import com.hippo.image.ImageData;
import com.hippo.yorozuya.ConcurrentPool;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.OSUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public abstract class GalleryProvider {

    public static final int STATE_WAIT = -1;
    public static final int STATE_ERROR = -2;

    private static final long MAX_CACHE_SIZE = 128 * 1024 * 1024; // 128MB
    private static final long MIN_CACHE_SIZE = 32 * 1024 * 1024; // 32MB

    private final ConcurrentPool<NotifyTask> mNotifyTaskPool = new ConcurrentPool<>(5);
    private volatile Listener mListener;
    private volatile GLRoot mGLRoot;

    private final LruCache<Integer, ImageData> mImageCache;

    private boolean mStarted = false;

    public GalleryProvider() {
        final int imageCacheSize = (int) MathUtils.clamp(
                OSUtils.getTotalMemory() / 16, MIN_CACHE_SIZE, MAX_CACHE_SIZE);
        mImageCache = LruCache.create(imageCacheSize, new ImageCacheHelper(), false);
    }

    @UiThread
    public void start() {
        if (mStarted) {
            throw new IllegalStateException("Can't start it twice");
        }
        mStarted = true;
    }

    @UiThread
    public void stop() {
        mImageCache.close();
    }

    public void setGLRoot(GLRoot glRoot) {
        mGLRoot = glRoot;
    }

    /**
     * @return {@link #STATE_WAIT} for wait,
     *          {@link #STATE_ERROR} for error, {@link #getError()} to get error message,
     *          0 for empty.
     */
    public abstract int getSize();

    /**
     * Find image in cache first. Call {@link #onRequest(int)} if miss.
     */
    @RenderThread
    public final void request(int index) {
        final ImageData imageData = mImageCache.get(index);
        if (imageData != null) {
            notifyPageSucceed(index, imageData);
        } else {
            onRequest(index);
        }
    }

    /**
     * Cache will be ignored. Call {@link #onForceRequest(int)} directly.
     */
    public final void forceRequest(int index) {
        onForceRequest(index);
    }

    protected abstract void onRequest(int index);

    protected abstract void onForceRequest(int index);

    public final void cancelRequest(int index) {
        onCancelRequest(index);
    }

    protected abstract void onCancelRequest(int index);

    public abstract String getError();

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void notifyStateChanged() {
        notify(NotifyTask.TYPE_STATE_CHANGED, -1, 0.0f, null, null);
    }

    public void notifyDataChanged(int index) {
        notify(NotifyTask.TYPE_DATA_CHANGED, index, 0.0f, null, null);
    }

    public void notifyPageWait(int index) {
        notify(NotifyTask.TYPE_WAIT, index, 0.0f, null, null);
    }

    public void notifyPagePercent(int index, float percent) {
        notify(NotifyTask.TYPE_PERCENT, index, percent, null, null);
    }

    public void notifyPageSucceed(int index, @Nullable ImageData image) {
        notify(NotifyTask.TYPE_SUCCEED, index, 0.0f, image, null);
    }

    public void notifyPageFailed(int index, String error) {
        notify(NotifyTask.TYPE_FAILED, index, 0.0f, null, error);
    }

    private void notify(@NotifyTask.Type int type, int index, float percent, ImageData image, String error) {
        final Listener listener = mListener;
        if (listener == null) {
            return;
        }

        final GLRoot glRoot = mGLRoot;
        if (glRoot == null) {
            return;
        }

        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask(listener, mNotifyTaskPool, mImageCache);
        }
        task.setData(type, index, percent, image, error);
        glRoot.addOnGLIdleListener(task);
    }

    private static class NotifyTask implements GLRoot.OnGLIdleListener {

        @IntDef({TYPE_STATE_CHANGED, TYPE_DATA_CHANGED,
                TYPE_WAIT, TYPE_PERCENT, TYPE_SUCCEED, TYPE_FAILED})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {}

        public static final int TYPE_STATE_CHANGED = 0;
        public static final int TYPE_DATA_CHANGED = 1;
        public static final int TYPE_WAIT = 2;
        public static final int TYPE_PERCENT = 3;
        public static final int TYPE_SUCCEED = 4;
        public static final int TYPE_FAILED = 5;

        private final Listener mListener;
        private final ConcurrentPool<NotifyTask> mPool;
        private final LruCache<Integer, ImageData> mCache;

        @Type
        private int mType;
        private int mIndex;
        private float mPercent;
        private ImageData mImage;
        private String mError;

        public NotifyTask(Listener listener, ConcurrentPool<NotifyTask> pool,
                LruCache<Integer, ImageData> cache) {
            mListener = listener;
            mPool = pool;
            mCache = cache;
        }

        public void setData(@Type int type, int index, float percent, ImageData image, String error) {
            mType = type;
            mIndex = index;
            mPercent = percent;
            mImage = image;
            mError = error;
        }

        @Override
        public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            switch (mType) {
                case TYPE_STATE_CHANGED:
                    mListener.onStateChanged();
                    break;
                case TYPE_DATA_CHANGED:
                    mListener.onDataChanged(mIndex);
                    break;
                case TYPE_WAIT:
                    mListener.onPageWait(mIndex);
                    break;
                case TYPE_PERCENT:
                    mListener.onPagePercent(mIndex, mPercent);
                    break;
                case TYPE_SUCCEED:
                    mListener.onPageSucceed(mIndex, mImage);
                    mCache.put(mIndex, mImage);
                    break;
                case TYPE_FAILED:
                    mListener.onPageFailed(mIndex, mError);
                    break;
            }

            // Clean data
            mImage = null;
            mError = null;
            // Push back
            mPool.push(this);

            return false;
        }
    }

    private static class ImageCacheHelper implements LruCacheHelper<Integer, ImageData> {

        @Override
        public int sizeOf(Integer key, ImageData value) {
            return value.getWidth() * value.getHeight() * 4;
        }

        @Override
        public ImageData create(Integer key) {
            return null;
        }

        @Override
        public void onEntryAdded(Integer key, ImageData value) {
            value.addReference();
        }

        @Override
        public void onEntryRemoved(boolean evicted, Integer key, ImageData oldValue, ImageData newValue) {
            oldValue.removeReference();
            if (!oldValue.isReferenced()) {
                oldValue.recycle();
            }
        }
    }

    public interface Listener {

        void onStateChanged();

        void onPageWait(int index);

        void onPagePercent(int index, float percent);

        void onPageSucceed(int index, ImageData image);

        void onPageFailed(int index, String error);

        void onDataChanged(int index);
    }
}
