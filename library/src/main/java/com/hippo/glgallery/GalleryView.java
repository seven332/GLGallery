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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.MotionEvent;

import com.hippo.glview.annotation.RenderThread;
import com.hippo.glview.glrenderer.GLCanvas;
import com.hippo.glview.image.ImageMovableTextTexture;
import com.hippo.glview.util.GalleryUtils;
import com.hippo.glview.view.AnimationTime;
import com.hippo.glview.view.GLRoot;
import com.hippo.glview.view.GLView;
import com.hippo.glview.widget.GLEdgeView;
import com.hippo.yorozuya.Pool;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class GalleryView extends GLView implements GestureRecognizer.Listener {

    private static final String LOG_TAG = GalleryView.class.getSimpleName();

    @IntDef({LAYOUT_PAGER_LEFT_TO_RIGHT, LAYOUT_PAGER_RIGHT_TO_LEFT,
            LAYOUT_SCROLL_TOP_TO_BOTTOM, LAYOUT_SCROLL_LEFT_TO_RIGHT, LAYOUT_SCROLL_RIGHT_TO_LEFT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LayoutMode {}

    @IntDef({SCALE_ORIGIN, SCALE_FIT_WIDTH, SCALE_FIT_HEIGHT, SCALE_FIT, SCALE_FIXED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleMode {}

    @IntDef({START_POSITION_TOP_LEFT, START_POSITION_TOP_RIGHT, START_POSITION_BOTTOM_LEFT,
            START_POSITION_BOTTOM_RIGHT, START_POSITION_CENTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StartPosition {}

    public static final int LAYOUT_PAGER_LEFT_TO_RIGHT = 0;
    public static final int LAYOUT_PAGER_RIGHT_TO_LEFT = 1;
    public static final int LAYOUT_SCROLL_TOP_TO_BOTTOM = 2;
    public static final int LAYOUT_SCROLL_LEFT_TO_RIGHT = 3;
    public static final int LAYOUT_SCROLL_RIGHT_TO_LEFT = 4;

    public static final int SCALE_ORIGIN = ImageView.SCALE_ORIGIN;
    public static final int SCALE_FIT_WIDTH = ImageView.SCALE_FIT_WIDTH;
    public static final int SCALE_FIT_HEIGHT = ImageView.SCALE_FIT_HEIGHT;
    public static final int SCALE_FIT = ImageView.SCALE_FIT;
    public static final int SCALE_FIXED = ImageView.SCALE_FIXED;

    public static final int START_POSITION_TOP_LEFT = ImageView.START_POSITION_TOP_LEFT;
    public static final int START_POSITION_TOP_RIGHT = ImageView.START_POSITION_TOP_RIGHT;
    public static final int START_POSITION_BOTTOM_LEFT = ImageView.START_POSITION_BOTTOM_LEFT;
    public static final int START_POSITION_BOTTOM_RIGHT = ImageView.START_POSITION_BOTTOM_RIGHT;
    public static final int START_POSITION_CENTER = ImageView.START_POSITION_CENTER;

    private static final float[] LEFT_AREA = {0.0f, 0.0f, 1.0f / 3.0f, 1f};
    private static final float[] RIGHT_AREA = {2.0f / 3.0f, 0.0f, 1.0f, 1f};
    private static final float[] MENU_AREA = {1.0f / 3.0f, 0.0f, 2.0f / 3.0f, 1.0f / 2.0f};
    private static final float[] SLIDER_AREA = {1.0f / 3.0f, 1.0f / 2.0f, 2.0f / 3.0f, 1.0f};

    private final Context mContext;
    private final GestureRecognizer mGestureRecognizer;

    @Nullable
    private Adapter mAdapter;
    @Nullable
    private Listener mListener;

    private final GalleryPageView.Params mPageParams;
    private ImageMovableTextTexture mIndexTextTexture;

    private WaitingLayoutManager mWaitingLayoutManager;
    private ErrorLayoutManager mErrorLayoutManager;
    private PagerLayoutManager mPagerLayoutManager;
    private ScrollLayoutManager mScrollLayoutManager;
    @Nullable
    private LayoutManager mLayoutManager;

    private final Postman mPostman;

    // EdgeView is special, it is not a child of GalleryView,
    // but it works like a child of GalleryView.
    // It make task easier for LayoutManager.
    private final GLEdgeView mEdgeView;
    private final Pool<GalleryPageView> mGalleryPageViewPool = new Pool<>(5);

    private final int mBackgroundColor;
    private final int mPagerInterval;
    private final int mScrollInterval;
    private final int mProgressSize;
    private final int mProgressColor;
    private final int mIndexTextSize;
    private final int mIndexTextColor;
    private final Typeface mIndexTextTypeface;
    private final int mErrorTextSize;
    private final int mErrorTextColor;

    private final String mDefaultErrorString;
    private final String mEmptyString;

    private boolean mEnableRequestFill = true;
    private boolean mRequestFill = false;
    private boolean mWillFill = false;

    private boolean mScale = false;
    private boolean mScroll = false;

    private final Rect mLeftArea = new Rect();
    private final Rect mRightArea = new Rect();
    private final Rect mMenuArea = new Rect();
    private final Rect mSliderArea = new Rect();

    private int mLayoutMode;
    private int mScaleMode;
    private int mStartPosition;

    // The id to represent current page
    private long mCurrentId;

    public static class Builder {

        private final Context mContext;
        private final GLRoot mGLRoot;

        public int layoutMode = LAYOUT_PAGER_LEFT_TO_RIGHT;
        public int scaleMode = SCALE_FIT;
        public int startPosition = START_POSITION_TOP_LEFT;

        public int backgroundColor = Color.BLACK;
        public int edgeColor = Color.WHITE;
        public int pagerInterval = 48;
        public int scrollInterval = 24;
        public int pageMinWidth = 256;
        public int pageMinHeight = 256;
        public int pageInfoInterval = 24;
        public int progressSize = 56;
        public int progressColor = Color.WHITE;
        public int indexTextSize = 56;
        public int indexTextColor = Color.WHITE;
        public Typeface indexTextTypeface = Typeface.DEFAULT;
        public int textSize = 48;
        public int textColor = Color.WHITE;
        public int errorTextSize = 24;
        public int errorTextColor = Color.RED;
        public String defaultErrorString = "Error";
        public String emptyString = "Empty";

        public Builder(@NonNull Context context, @NonNull GLRoot GLRoot) {
            mContext = context;
            mGLRoot = GLRoot;
        }

        public GalleryView build() {
            return new GalleryView(this);
        }
    }

    private GalleryView(Builder build) {
        mContext = build.mContext;
        mGestureRecognizer = new GestureRecognizer(mContext, this);
        mEdgeView = new GLEdgeView(build.edgeColor);
        mPostman = new GalleryViewPostman(this);

        build.mGLRoot.registerHandler(mPostman);

        mLayoutMode = build.layoutMode;
        mScaleMode = build.scaleMode;
        mStartPosition = build.startPosition;

        mBackgroundColor = build.backgroundColor;
        mPagerInterval = build.pagerInterval;
        mScrollInterval = build.scrollInterval;
        mProgressSize = build.progressSize;
        mProgressColor = build.progressColor;
        mIndexTextSize = build.indexTextSize;
        mIndexTextColor = build.indexTextColor;
        mIndexTextTypeface = build.indexTextTypeface;
        mErrorTextSize = build.errorTextSize;
        mErrorTextColor = build.errorTextColor;

        mDefaultErrorString = build.defaultErrorString;
        mEmptyString = build.emptyString;

        final GalleryPageView.Params params = new GalleryPageView.Params();
        params.progressSize = build.progressSize;
        params.progressColor = build.progressColor;
        params.progressBgColor = build.backgroundColor;
        params.pageMinWidth = build.pageMinWidth;
        params.pageMinHeight = build.pageMinHeight;
        params.infoInterval = build.pageInfoInterval;
        params.textSize = build.textSize;
        params.textColor = build.textColor;
        params.errorTextSize = build.errorTextSize;
        params.errorTextColor = build.errorTextColor;
        mPageParams = params;

        setBackgroundColor(mBackgroundColor);
    }

    public void setAdapter(@Nullable Adapter adapter) {
        // Detach LayoutManager to clear view
        if (mLayoutManager != null) {
            detachLayoutManager();
        }

        // Disconnect old Adapter and GalleryView
        if (mAdapter != null) {
            mAdapter.setGalleryView(null);
            mAdapter = null;
        }

        if (adapter != null) {
            mAdapter = adapter;
            adapter.setGalleryView(this);
            // Attach LayoutManager now if GalleryView is attached to root
            if (isAttachedToRoot()) {
                attachLayoutManager();
            }
        }
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    private void ensureWaitingLayoutManager() {
        if (mWaitingLayoutManager == null) {
            mWaitingLayoutManager = new WaitingLayoutManager(
                    this, mProgressSize, mProgressColor, mBackgroundColor);
        }
    }

    private void ensureErrorLayoutManager() {
        if (mErrorLayoutManager == null) {
            mErrorLayoutManager = new ErrorLayoutManager(
                    this, mErrorTextSize, mErrorTextColor);
        }
    }

    private void ensurePagerLayoutManager() {
        if (mPagerLayoutManager == null) {
            mPagerLayoutManager = new PagerLayoutManager(mContext, this,
                    mScaleMode, mStartPosition, 1.0f, mPagerInterval);
        }
    }

    private void ensureScrollLayoutManager() {
        if (mScrollLayoutManager == null) {
            mScrollLayoutManager = new ScrollLayoutManager(mContext, this, mScrollInterval);
        }
    }

    @PagerLayoutManager.Mode
    private int getPagerModeFromLayoutMode(int layoutMode) {
        switch (layoutMode) {
            case LAYOUT_PAGER_LEFT_TO_RIGHT:
                return PagerLayoutManager.MODE_LEFT_TO_RIGHT;
            case LAYOUT_PAGER_RIGHT_TO_LEFT:
                return PagerLayoutManager.MODE_RIGHT_TO_LEFT;
            default:
                throw new IllegalStateException("Can't convert this layout mode to pager mode: " + layoutMode);
        }
    }

    @ScrollLayoutManager.Mode
    private int getScrollModeFromLayoutMode(int layoutMode) {
        switch (layoutMode) {
            case LAYOUT_SCROLL_TOP_TO_BOTTOM:
                return ScrollLayoutManager.MODE_TOP_TO_BOTTOM;
            case LAYOUT_SCROLL_LEFT_TO_RIGHT:
                return ScrollLayoutManager.MODE_LEFT_TO_RIGHT;
            case LAYOUT_SCROLL_RIGHT_TO_LEFT:
                return ScrollLayoutManager.MODE_RIGHT_TO_LEFT;
            default:
                throw new IllegalStateException("Can't convert this layout mode to scroll mode: " + layoutMode);
        }
    }

    private void attachLayoutManager() {
        if (mAdapter == null) {
            Log.e(LOG_TAG, "No Adapter for the GalleryView.");
            return;
        }

        if (mLayoutManager != null) {
            Log.e(LOG_TAG, "Already attach LayoutManager.");
            return;
        }

        final int state = mAdapter.getState();
        switch (state) {
            case Adapter.STATE_WAIT:
                ensureWaitingLayoutManager();
                mWaitingLayoutManager.onAttach(mAdapter);
                mAdapter = null;
                mLayoutManager = mWaitingLayoutManager;
                break;
            case Adapter.STATE_EMPTY:
            case Adapter.STATE_ERROR:
                ensureErrorLayoutManager();
                mErrorLayoutManager.onAttach(mAdapter);
                mAdapter = null;
                mLayoutManager = mErrorLayoutManager;
                break;
            case Adapter.STATE_READY:
                switch (mLayoutMode) {
                    case LAYOUT_PAGER_LEFT_TO_RIGHT:
                    case LAYOUT_PAGER_RIGHT_TO_LEFT:
                        ensurePagerLayoutManager();
                        mPagerLayoutManager.setMode(getPagerModeFromLayoutMode(mLayoutMode));
                        mPagerLayoutManager.onAttach(mAdapter);
                        mAdapter = null;
                        mLayoutManager = mPagerLayoutManager;
                        break;
                    case LAYOUT_SCROLL_TOP_TO_BOTTOM:
                    case LAYOUT_SCROLL_LEFT_TO_RIGHT:
                    case LAYOUT_SCROLL_RIGHT_TO_LEFT:
                        ensureScrollLayoutManager();
                        mScrollLayoutManager.setMode(getScrollModeFromLayoutMode(mLayoutMode));
                        mScrollLayoutManager.onAttach(mAdapter);
                        mAdapter = null;
                        mLayoutManager = mScrollLayoutManager;
                        break;
                    default:
                        throw new IllegalStateException("Invalid layout mode: " + mLayoutMode);
                }
                break;
            default:
                throw new IllegalStateException("Invalid state: " + state);
        }

        requestFill();
    }

    private void detachLayoutManager() {
        if (mLayoutManager == null) {
            Log.w(LOG_TAG, "No LayoutManager attached.");
            return;
        }

        mAdapter = mLayoutManager.onDetach();
        mLayoutManager = null;
    }


    @Override
    public void onAttachToRoot(GLRoot root) {
        super.onAttachToRoot(root);
        mEdgeView.onAttachToRoot(root);

        if (mIndexTextTexture == null) {
            mIndexTextTexture = ImageMovableTextTexture.create(mIndexTextTypeface,
                    mIndexTextSize, mIndexTextColor,
                    new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'});
        }
        attachLayoutManager();
    }

    @Override
    public void onDetachFromRoot() {
        detachLayoutManager();
        if (mIndexTextTexture != null) {
            mIndexTextTexture.recycle();
            mIndexTextTexture = null;
        }

        super.onDetachFromRoot();
        mEdgeView.onDetachFromRoot();
    }

    /**
     * Return value itself if it is one for {@link #LAYOUT_PAGER_LEFT_TO_RIGHT},
     * {@link #LAYOUT_PAGER_RIGHT_TO_LEFT}, {@link #LAYOUT_SCROLL_TOP_TO_BOTTOM},
     * {@link #LAYOUT_SCROLL_LEFT_TO_RIGHT} and {@link #LAYOUT_PAGER_RIGHT_TO_LEFT},
     * otherwise return {@link #LAYOUT_PAGER_LEFT_TO_RIGHT}.
     */
    @LayoutMode
    public static int sanitizeLayoutMode(int value) {
        if (value != LAYOUT_PAGER_LEFT_TO_RIGHT &&
                value != LAYOUT_PAGER_RIGHT_TO_LEFT &&
                value != LAYOUT_SCROLL_TOP_TO_BOTTOM &&
                value != LAYOUT_SCROLL_LEFT_TO_RIGHT &&
                value != LAYOUT_SCROLL_RIGHT_TO_LEFT) {
            return LAYOUT_PAGER_LEFT_TO_RIGHT;
        } else {
            return value;
        }
    }

    /**
     * Return value itself if it is one for {@link #SCALE_ORIGIN},
     * {@link #SCALE_FIT_WIDTH}, {@link #SCALE_FIT_HEIGHT},
     * {@link #SCALE_FIT} and {@link #SCALE_FIXED},
     * otherwise return {@link #SCALE_FIT}.
     */
    @ScaleMode
    public static int sanitizeScaleMode(int value) {
        if (value != SCALE_ORIGIN &&
                value != SCALE_FIT_WIDTH &&
                value != SCALE_FIT_HEIGHT &&
                value != SCALE_FIT &&
                value != SCALE_FIXED) {
            return SCALE_FIT;
        } else {
            return value;
        }
    }

    /**
     * Return value itself if it is one for {@link #START_POSITION_TOP_LEFT},
     * {@link #START_POSITION_TOP_RIGHT}, {@link #START_POSITION_BOTTOM_LEFT},
     * {@link #START_POSITION_BOTTOM_RIGHT} and {@link #START_POSITION_CENTER},
     * otherwise return {@link #START_POSITION_TOP_LEFT}.
     */
    @StartPosition
    public static int sanitizeStartPosition(int value) {
        if (value != START_POSITION_TOP_LEFT &&
                value != START_POSITION_TOP_RIGHT &&
                value != START_POSITION_BOTTOM_LEFT &&
                value != START_POSITION_BOTTOM_RIGHT &&
                value != START_POSITION_CENTER) {
            return START_POSITION_TOP_LEFT;
        } else {
            return value;
        }
    }

    @LayoutMode
    public int getLayoutMode() {
        return mLayoutMode;
    }

    @ScaleMode
    public int getScaleMode() {
        return mScaleMode;
    }

    @StartPosition
    public int getStartPosition() {
        return mStartPosition;
    }

    @Override
    public void requestLayout() {
        // Do not need requestLayout, because the size will not change
        requestFill();
    }

    void requestFill() {
        if (mEnableRequestFill) {
            mRequestFill = true;
            if (!mWillFill) {
                invalidate();
            }
        }
    }

    @Override
    protected boolean dispatchTouchEvent(MotionEvent event) {
        // Do not pass event to component, so handle event here
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    GLEdgeView getEdgeView() {
        return mEdgeView;
    }

    String getDefaultErrorStr() {
        return mDefaultErrorString;
    }

    String getEmptyStr() {
        return mEmptyString;
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top, int right, int bottom) {
        mEdgeView.layout(left, top, right, bottom);

        fill();

        if (changeSize) {
            final int width = right - left;
            final int height = bottom - top;
            mLeftArea.set((int) (LEFT_AREA[0] * width), (int) (LEFT_AREA[1] * height),
                    (int) (LEFT_AREA[2] * width), (int) (LEFT_AREA[3] * height));
            mRightArea.set((int) (RIGHT_AREA[0] * width), (int) (RIGHT_AREA[1] * height),
                    (int) (RIGHT_AREA[2] * width), (int) (RIGHT_AREA[3] * height));
            mMenuArea.set((int) (MENU_AREA[0] * width), (int) (MENU_AREA[1] * height),
                    (int) (MENU_AREA[2] * width), (int) (MENU_AREA[3] * height));
            mSliderArea.set((int) (SLIDER_AREA[0] * width), (int) (SLIDER_AREA[1] * height),
                    (int) (SLIDER_AREA[2] * width), (int) (SLIDER_AREA[3] * height));
        }
    }

    private void onNotifyStateChanged() {
        // Update LayoutManager
        if (mLayoutManager != null) {
            detachLayoutManager();
            attachLayoutManager();
        }
    }

    private void notifyDataChanged() {
        if (mLayoutManager != null) {
            mLayoutManager.onDataChanged();
        }
    }

    void setLayoutModeInternal(int layoutMode) {
        if (mLayoutMode == layoutMode) {
            return;
        }
        mLayoutMode = layoutMode;

        if (mLayoutManager == null) {
            // onAttachToRoot has not been called
            // or no Adapter now.
            return;
        }
        if (mLayoutManager == mWaitingLayoutManager || mLayoutManager == mErrorLayoutManager) {
            // Waiting or got error
            return;
        }

        switch (layoutMode) {
            case LAYOUT_PAGER_LEFT_TO_RIGHT:
            case LAYOUT_PAGER_RIGHT_TO_LEFT:
                @PagerLayoutManager.Mode
                final int pagerMode = getPagerModeFromLayoutMode(layoutMode);
                if (mLayoutManager == mPagerLayoutManager) {
                    // PagerLayoutManager already attached, just change mode
                    mPagerLayoutManager.setMode(pagerMode);
                } else {
                    // Detach old LayoutManager, attach PagerLayoutManager
                    ensurePagerLayoutManager();
                    mPagerLayoutManager.setMode(pagerMode);
                    mPagerLayoutManager.onAttach(mLayoutManager.onDetach());
                    mLayoutManager = mPagerLayoutManager;
                }
                break;
            case LAYOUT_SCROLL_TOP_TO_BOTTOM:
            case LAYOUT_SCROLL_LEFT_TO_RIGHT:
            case LAYOUT_SCROLL_RIGHT_TO_LEFT:
                @ScrollLayoutManager.Mode
                final int scrollMode = getScrollModeFromLayoutMode(layoutMode);
                if (mLayoutManager == mScrollLayoutManager) {
                    // ScrollLayoutManager already attached, just change mode
                    mScrollLayoutManager.setMode(scrollMode);
                } else {
                    // Detach old LayoutManager, attach ScrollLayoutManager
                    ensureScrollLayoutManager();
                    mScrollLayoutManager.setMode(scrollMode);
                    mScrollLayoutManager.onAttach(mLayoutManager.onDetach());
                    mLayoutManager = mScrollLayoutManager;
                }
                break;
            default:
                throw new IllegalStateException("Invalid layout mode: " + mLayoutMode);
        }

        requestFill();
    }

    void setScaleModeInternal(int scaleMode) {
        mScaleMode = scaleMode;
        if (mPagerLayoutManager != null) {
            mPagerLayoutManager.setScaleMode(scaleMode);
        }
    }

    void setStartPositionInternal(int startPosition) {
        mStartPosition = startPosition;
        if (mPagerLayoutManager != null) {
            mPagerLayoutManager.setStartPosition(startPosition);
        }
    }

    void pageNextInternal() {
        if (mLayoutManager != null) {
            mLayoutManager.onPageNext();
        }
    }

    void pagePreviousInternal() {
        if (mLayoutManager != null) {
            mLayoutManager.onPagePrevious();
        }
    }

    void pageToIdInternal(int id) {
        if (mLayoutManager != null) {
            mLayoutManager.onPageToId(id);
        }
    }

    void scaleToNextLevelInternal(float x, float y) {
        if (mLayoutManager != null) {
            mLayoutManager.onScaleToNextLevel(x, y);
        }
    }

    void onSingleTapUpInternal(float x, float y) {}

    void onSingleTapConfirmedInternal(float x, float y) {
        if ((mLayoutManager == null || mLayoutManager.isTouchActionValid())
                && mListener != null) {
            mListener.onClick(x, y);
        }
    }

    void onDoubleTapInternal(float x, float y) {}

    void onDoubleTapConfirmedInternal(float x, float y) {
        if (!mScale && (mLayoutManager == null || mLayoutManager.isTouchActionValid())
                && mListener != null) {
            mListener.onDoubleClick(x, y);
        }
    }

    void onLongPressInternal(float x, float y) {
        if (!mScale && (mLayoutManager == null || mLayoutManager.isTouchActionValid())
                && mListener != null) {
            mListener.onLongClick(x, y);
        }
    }

    void onScrollInternal(float dx, float dy, float totalX, float totalY, float x, float y) {
        if (mScale) {
            return;
        }
        mScroll = true;

        if (mLayoutManager != null) {
            mLayoutManager.onScroll(dx, dy, totalX, totalY, x, y);
        }
    }

    void onFlingInternal(float velocityX, float velocityY) {
        if (mLayoutManager != null) {
            mLayoutManager.onFling(velocityX, velocityY);
        }
    }

    void onScaleBeginInternal(float focusX, float focusY) {
        onScaleInternal(focusX, focusY, 1.0f);
    }

    void onScaleInternal(float focusX, float focusY, float scale) {
        if (mScroll || (mLayoutManager != null && !mLayoutManager.canScale())) {
            return;
        }
        mScale = true;

        if (mLayoutManager != null) {
            mLayoutManager.onScale(focusX, focusY, scale);
        }
    }

    void onScaleEndInternal() {}

    void onDownInternal(float x, float y) {
        mScale = false;
        mScroll = false;
        if (mLayoutManager != null) {
            mLayoutManager.onDown();
        }
    }

    void onUpInternal() {
        if (mLayoutManager != null) {
            mLayoutManager.onUp();
        }
    }

    void onPointerDownInternal(float x, float y) {
        if (!mScroll && (mLayoutManager != null && mLayoutManager.canScale())) {
            mScale = true;
        }
    }

    void onPointerUpInternal() {}

    @RenderThread
    void forceFill() {
        mRequestFill = true;
        fill();
    }

    @RenderThread
    private void fill() {
        GalleryUtils.assertInRenderThread();

        if (!mRequestFill) {
            return;
        }

        // Disable request layout
        mEnableRequestFill = false;
        if (mLayoutManager != null) {
            mLayoutManager.onFill();
        }
        mEnableRequestFill = true;
        mRequestFill = false;
    }


    @Override
    public void render(GLCanvas canvas) {
        mWillFill = true;

        if (mLayoutManager != null && mLayoutManager.onUpdateAnimation(AnimationTime.get())) {
            invalidate();
        }

        fill();
        mWillFill = false;

        super.render(canvas);
        mEdgeView.render(canvas);

        final long newCurrentId;
        if (mLayoutManager != null) {
            newCurrentId = mLayoutManager.getCurrentId();
        } else {
            newCurrentId = Adapter.INVALID_ID;
        }

        if (mCurrentId != newCurrentId) {
            mCurrentId = newCurrentId;
            if (mListener != null) {
                mListener.onUpdateCurrentId(newCurrentId);
            }
        }
    }

    public GalleryPageView findPageById(long id) {
        if (mLayoutManager != null) {
            return mLayoutManager.findPageById(id);
        } else {
            return null;
        }
    }

    GalleryPageView obtainPage() {
        GalleryPageView page = mGalleryPageViewPool.pop();
        if (page == null) {
            page = new GalleryPageView(this, mPageParams, mIndexTextTexture);
        }
        return page;
    }

    void releasePage(GalleryPageView page) {
        mGalleryPageViewPool.push(page);
    }

    public static abstract class Adapter {

        private static final String LOG_TAG = Adapter.class.getSimpleName();

        @IntDef({STATE_WAIT, STATE_READY, STATE_EMPTY, STATE_ERROR})
        @Retention(RetentionPolicy.SOURCE)
        public @interface State {}

        public static final long INVALID_ID = -1L;

        public static final int STATE_WAIT = 0;
        public static final int STATE_READY = 1;
        public static final int STATE_EMPTY = 2;
        public static final int STATE_ERROR = 3;

        @Nullable
        protected GalleryView mGalleryView;

        private void setGalleryView(@Nullable GalleryView galleryView) {
            mGalleryView = galleryView;
        }

        public boolean isAttached() {
            return mGalleryView != null;
        }

        /**
         * Move current position to next.
         * Throw IllegalStateException if not next.
         *
         * @see #hasNext()
         */
        public abstract void next();

        /**
         * Move current position to previous.
         * Throw IllegalStateException if not previous.
         *
         * @see #hasPrevious()
         */
        public abstract void previous();

        /**
         * Return true if has next position.
         *
         * @see #next()
         */
        public abstract boolean hasNext();

        /**
         * Return true if has previous position.
         *
         * @see #previous()
         */
        public abstract boolean hasPrevious();

        /**
         * Return an id to represent current position.
         * The id must be unique.
         */
        public abstract long getCurrentId();

        /**
         * Move current position to where the id represents.
         * Return {@code false} if the id is invalid.
         */
        protected abstract boolean setCurrentId(long id);

        /**
         * Return {@code true} if this id represents the first page.
         */
        public abstract boolean isHead(long id);

        /**
         * Return {@code true} if this id represents the last page.
         */
        public abstract boolean isTail(long id);

        public abstract String idToString(long id);

        void bind(GalleryPageView view) {
            view.setPageId(getCurrentId());
            onBind(view);
        }

        void unbind(GalleryPageView view, long id) {
            onUnbind(view, id);
            view.setPageId(INVALID_ID);
        }

        public abstract void onBind(GalleryPageView view);

        public abstract void onUnbind(GalleryPageView view, long id);

        /**
         * @return Null for no error
         */
        public abstract String getError();

        /**
         * Return current state.
         */
        @State
        public abstract int getState();

        public void notifyStateChanged() {
            if (mGalleryView != null) {
                mGalleryView.onNotifyStateChanged();
            } else {
                Log.e(LOG_TAG, "It is not attached to any GalleryView.");
            }
        }

        public void notifyDataChanged() {
            if (mGalleryView != null) {
                mGalleryView.notifyDataChanged();
            } else {
                Log.e(LOG_TAG, "It is not attached to any GalleryView.");
            }
        }
    }

    public static abstract class LayoutManager {

        protected GalleryView mGalleryView;

        public LayoutManager(@NonNull GalleryView galleryView) {
            mGalleryView = galleryView;
        }

        public abstract void onAttach(Adapter adapter);

        public abstract Adapter onDetach();

        public abstract void onFill();

        public abstract void onDown();

        public abstract void onUp();

        public abstract void onScaleToNextLevel(float x, float y);

        public abstract void onScroll(float dx, float dy, float totalX, float totalY, float x, float y);

        public abstract void onFling(float velocityX, float velocityY);

        public abstract boolean canScale();

        public abstract void onScale(float focusX, float focusY, float scale);

        public abstract void onPageNext();

        public abstract void onPagePrevious();

        public abstract void onPageToId(long id);

        /**
         * {@code true} for call {@link #invalidate()}.
         *
         * @param time Current animation time
         */
        public abstract boolean onUpdateAnimation(long time);

        public abstract boolean isTouchActionValid();

        public abstract GalleryPageView findPageById(long id);

        public abstract long getIdUnder(float x, float y);

        public abstract long getCurrentId();

        public abstract void onDataChanged();
    }

    public interface Listener {

        void onUpdateCurrentId(long id);

        void onClick(float x, float y);

        void onDoubleClick(float x, float y);

        void onLongClick(float x, float y);
    }

    ////////////////////////////////////////
    //
    // Non render thread methods.
    //
    // Must use Postman to post it to render thread.
    //
    ////////////////////////////////////////

    /**
     * Set layout mode of the {@code GalleryView}.
     * <p>
     * It can be called in UI thread.
     */
    public void setLayoutMode(@LayoutMode int layoutMode) {
        mPostman.postMethod(GalleryViewPostman.METHOD_SET_LAYOUT_MODE, layoutMode);
    }

    /**
     * Set scale mode of the {@code GalleryView}.
     * <p>
     * It can be called in UI thread.
     */
    public void setScaleMode(@ScaleMode int scaleMode) {
        mPostman.postMethod(GalleryViewPostman.METHOD_SET_SCALE_MODE, scaleMode);
    }

    /**
     * Set start position of the {@code GalleryView}.
     * <p>
     * It can be called in UI thread.
     */
    public void setStartPosition(@StartPosition int startPosition) {
        mPostman.postMethod(GalleryViewPostman.METHOD_SET_START_POSITION, startPosition);
    }

    /**
     * Turn to next page.
     * <p>
     * It can be called in UI thread.
     */
    public void pageNext() {
        mPostman.postMethod(GalleryViewPostman.METHOD_PAGE_NEXT);
    }

    /**
     * Turn to previous page
     * <p>
     * It can be called in UI thread.
     */
    public void pagePrevious() {
        mPostman.postMethod(GalleryViewPostman.METHOD_PAGE_PREVIOUS);
    }

    /**
     * Go to the page that this id represent.
     * <p>
     * It can be called in UI thread.
     */
    public void pageToId(long id) {
        mPostman.postMethod(GalleryViewPostman.METHOD_PAGE_TO_ID, id);
    }

    /**
     * Set current scale to next level.
     * <p>
     * It can be called in UI thread.
     */
    public void scaleToNextLevel(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_SCALE_TO_NEXT_LEVEL, x, y);
    }

    @Override
    public boolean onSingleTapUp(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_SINGLE_TAP_UP, x, y);
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_SINGLE_TAP_CONFIRMED, x, y);
        return true;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_DOUBLE_TAP, x, y);
        return true;
    }

    @Override
    public boolean onDoubleTapConfirmed(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_DOUBLE_TAP_CONFIRMED, x, y);
        return true;
    }

    @Override
    public void onLongPress(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_LONG_PRESS, x, y);
    }

    @Override
    public boolean onScroll(float dx, float dy, float totalX, float totalY, float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_SCROLL, dx, dy, totalX, totalY, x, y);
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_FLING, velocityX, velocityY);
        return true;
    }

    @Override
    public boolean onScaleBegin(float focusX, float focusY) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_SCALE_BEGIN, focusX, focusY);
        return true;
    }

    @Override
    public boolean onScale(float focusX, float focusY, float scale) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_SCALE, focusX, focusY, scale);
        return true;
    }

    @Override
    public void onScaleEnd() {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_SCALE_END);
    }

    @Override
    public void onDown(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_DOWN, x, y);
    }

    @Override
    public void onUp() {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_UP);
    }

    @Override
    public void onPointerDown(float x, float y) {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_POINTER_DOWN, x, y);
    }

    @Override
    public void onPointerUp() {
        mPostman.postMethod(GalleryViewPostman.METHOD_ON_POINTER_UP);
    }
}
