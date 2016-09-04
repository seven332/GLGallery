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
import android.graphics.Rect;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;

import com.hippo.glview.anim.Animation;
import com.hippo.glview.anim.FloatAnimation;
import com.hippo.glview.view.GLView;
import com.hippo.glview.widget.GLEdgeView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.MathUtils;

import junit.framework.Assert;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

// TODO Handle Image animation start
class ScrollLayoutManager extends GalleryView.LayoutManager {

    private static final String LOG_TAG = ScrollLayoutManager.class.getSimpleName();

    @IntDef({MODE_TOP_TO_BOTTOM, MODE_LEFT_TO_RIGHT, MODE_RIGHT_TO_LEFT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {}

    public static final int MODE_TOP_TO_BOTTOM = 0;
    public static final int MODE_LEFT_TO_RIGHT = 1;
    public static final int MODE_RIGHT_TO_LEFT = 2;

    private static final float RESERVATION = 1f;

    private static final float MAX_SCALE = 2.0f;
    private static final float MIN_SCALE = 1.0f;
    private static final float SCALE_ERROR = 0.01f;

    private static final int INVALID_TOP = Integer.MAX_VALUE;

    private GalleryView.Adapter mAdapter;

    private final LinkedList<GalleryPageView> mPages = new LinkedList<>();
    private final LinkedList<GalleryPageView> mTempPages = new LinkedList<>();

    @Mode
    private int mMode = MODE_RIGHT_TO_LEFT;
    private float mScale = 1.5f;
    private int mOffsetX;
    private int mOffsetY;
    private int mDeltaX;
    private int mDeltaY;
    private int mKeepTopPageId = GalleryView.Adapter.INVALID_ID;
    private int mKeepTop = INVALID_TOP;
    private int mFirstShownPageId = GalleryView.Adapter.INVALID_ID;
    private boolean mScrollUp;
    private boolean mFlingUp;
    private boolean mStopAnimationFinger;

    private final int mInterval;

    private final Rect mScreenBounds = new Rect();

    private final PageFling mPageFling;
    private final SmoothScaler mSmoothScaler;
    private final OverScroller mOverScroller;

    private int mBottomStateBottom;
    private boolean mBottomStateHasNext;

    public ScrollLayoutManager(Context context, @NonNull GalleryView galleryView, int interval) {
        super(galleryView);

        mInterval = interval;
        mPageFling = new PageFling(context);
        mSmoothScaler = new SmoothScaler();
        mOverScroller = new OverScroller();
    }

    private void resetParameters() {
        mScale = 1.0f;
        mOffsetX = 0;
        mOffsetY = 0;
        mDeltaX = 0;
        mDeltaY = 0;
        mKeepTopPageId = GalleryView.Adapter.INVALID_ID;
        mKeepTop = INVALID_TOP;
        mFirstShownPageId = GalleryView.Adapter.INVALID_ID;
        mScrollUp = false;
        mFlingUp = false;
        mStopAnimationFinger = false;
    }

    // Return true for animations are running
    private boolean cancelAllAnimations() {
        final boolean running = mPageFling.isRunning() ||
                mSmoothScaler.isRunning() ||
                mOverScroller.isRunning();
        mPageFling.cancel();
        mSmoothScaler.cancel();
        mOverScroller.cancel();
        return running;
    }

    public void setMode(@Mode int mode) {
        if (mMode == mode) {
            return;
        }

        mMode = mode;
        if (mAdapter != null) {
            // It is attached, refill
            // Cancel all animations
            cancelAllAnimations();
            // Remove all pages
            removeAllPages();
            // Reset parameters
            resetParameters();
            // Request fill
            mGalleryView.requestFill();
        }
    }


    @Override
    public void onAttach(GalleryView.Adapter adapter) {
        Assert.assertNull("The ScrollLayoutManager is attached", mAdapter);
        Assert.assertNotNull("The iterator is null", adapter);
        mAdapter = adapter;
        // Reset parameters
        resetParameters();
    }

    private void removePage(@NonNull GalleryPageView page) {
        mGalleryView.removeComponent(page);
        mAdapter.unbind(page, page.getId());
        mGalleryView.releasePage(page);
    }

    private void removeAllPages() {
        for (final GalleryPageView page : mPages) {
            removePage(page);
        }
        mPages.clear();
    }

    @Override
    public GalleryView.Adapter onDetach() {
        Assert.assertNotNull("The PagerLayoutManager is not attached", mAdapter);

        // Cancel all animations
        cancelAllAnimations();

        // Remove all pages
        removeAllPages();

        // Clear iterator
        final GalleryView.Adapter adapter = mAdapter;
        mAdapter = null;

        return adapter;
    }

    private GalleryPageView obtainPage() {
        final GalleryPageView page = mGalleryView.obtainPage();
        page.getImageView().setScaleOffset(ImageView.SCALE_FIT, ImageView.START_POSITION_TOP_RIGHT, 1.0f);
        return page;
    }

    private GalleryPageView getPageById(List<GalleryPageView> pages, int id, boolean remove) {
        for (final Iterator<GalleryPageView> iterator = pages.iterator(); iterator.hasNext();) {
            final GalleryPageView page = iterator.next();
            if (page.getId() == id) {
                if (remove) {
                    iterator.remove();
                }
                return page;
            }
        }
        return null;
    }

    private boolean isInScreen(GalleryPageView page) {
        final Rect bound = page.bounds();
        return Rect.intersects(bound, mScreenBounds);
    }

    private float getReservation() {
        return Math.max(RESERVATION, (((1 + 2 * RESERVATION) * mScale) - 1) / 2);
    }

    // Adapter position will go to first layout page.
    private void fillPagesVertical(int startOffset) {
        final GalleryView.Adapter adapter = mAdapter;
        final GalleryView galleryView = mGalleryView;
        final LinkedList<GalleryPageView> pages = mPages;
        final LinkedList<GalleryPageView> tempPages = mTempPages;
        final int width = galleryView.getWidth();
        final int height = galleryView.getHeight();
        final int pageWidth = (int) (width * mScale);
        final int interval = mInterval;
        final float reservation = getReservation();
        final int minY = (int) (-height * reservation);
        final int maxY = (int) (height * (1 + reservation));
        final int widthSpec = GLView.MeasureSpec.makeMeasureSpec(pageWidth, GLView.MeasureSpec.EXACTLY);
        final int heightSpec = GLView.MeasureSpec.makeMeasureSpec(height, GLView.MeasureSpec.UNSPECIFIED);

        // Fix adapter position and start offset
        if (!adapter.hasPrevious() && startOffset > 0) {
            startOffset = 0;
        } else if (startOffset < minY) {
            while (true) {
                final GalleryPageView page = getPageById(pages, adapter.getCurrentId(), false);
                if (page == null) {
                    startOffset = minY;
                    break;
                } else {
                    page.measure(widthSpec, heightSpec);
                    if (startOffset + page.getHeight() > minY) {
                        break;
                    } else if (adapter.hasNext()) {
                        adapter.next();
                        startOffset += page.getHeight() + interval;
                        if (startOffset >= minY) {
                            break;
                        }
                    } else {
                        startOffset = 0;
                        break;
                    }
                }
            }
        } else if (startOffset >= maxY) {
            if (adapter.hasPrevious()) {
                adapter.previous();
                int startBottomOffset = startOffset - interval;
                while (true) {
                    final GalleryPageView page = getPageById(pages, adapter.getCurrentId(), false);
                    if (page == null) {
                        startOffset = maxY - 1;
                        break;
                    } else {
                        page.measure(widthSpec, heightSpec);
                        startOffset = startBottomOffset - page.getHeight();
                        if (startOffset < maxY) {
                            break;
                        } else if (adapter.hasPrevious()) {
                            adapter.previous();
                            startBottomOffset = startOffset - interval;
                        } else {
                            startOffset = 0;
                            break;
                        }
                    }
                }
            } else {
                startOffset = 0;
            }
        }

        // Put page to temp list
        tempPages.addAll(pages);
        pages.clear();

        // Sanitize offsetX
        final int margin = pageWidth - width;
        if (margin >= 0) {
            mOffsetX = MathUtils.clamp(mOffsetX, -margin, 0);
        } else {
            mOffsetX = -margin / 2;
        }

        // Layout start page
        GalleryPageView page = getPageById(tempPages, adapter.getCurrentId(), true);
        if (page == null) {
            page = obtainPage();
            galleryView.addComponent(page);
            adapter.bind(page);
        }
        pages.add(page);
        page.measure(widthSpec, heightSpec);
        page.layout(mOffsetX, startOffset, mOffsetX + pageWidth, startOffset + page.getMeasuredHeight());

        // Save id
        final int savedId = adapter.getCurrentId();

        // Prepare for layout up and down
        int topBound = startOffset - interval;
        int bottomBound = startOffset + page.getMeasuredHeight() + interval;

        // Layout up
        while (topBound > minY && adapter.hasPrevious()) {
            adapter.previous();
            page = getPageById(tempPages, adapter.getCurrentId(), true);
            if (page == null) {
                page = obtainPage();
                galleryView.addComponent(page);
                adapter.bind(page);
            }
            pages.addFirst(page);
            page.measure(widthSpec, heightSpec);
            page.layout(mOffsetX, topBound - page.getMeasuredHeight(), mOffsetX + pageWidth, topBound);
            // Update topBound
            topBound -= page.getMeasuredHeight() + interval;
        }

        // Avoid space in top
        if (!adapter.hasPrevious()) {
            page = pages.getFirst();
            if (page.bounds().top > 0) {
                final int offset = -page.bounds().top;
                for (GalleryPageView p: pages) {
                    p.offsetTopAndBottom(offset);
                }
                bottomBound += offset;
            }
        }

        // Restore id
        adapter.setCurrentId(savedId);

        // Check down
        while (bottomBound < maxY && adapter.hasNext()) {
            adapter.next();
            page = getPageById(tempPages, adapter.getCurrentId(), true);
            if (page == null) {
                page = obtainPage();
                galleryView.addComponent(page);
                adapter.bind(page);
            }
            pages.addLast(page);
            page.measure(widthSpec, heightSpec);
            page.layout(mOffsetX, bottomBound, mOffsetX + pageWidth, bottomBound + page.getMeasuredHeight());
            // Update
            bottomBound += page.getMeasuredHeight() + interval;
        }

        // Avoid space in bottom
        final boolean hasNext = adapter.hasNext();
        adapter.setCurrentId(pages.getFirst().getId());
        if (!hasNext) {
            while (true) {
                page = pages.getLast();
                final int pagesBottom = page.bounds().bottom;
                if (pagesBottom >= height) {
                    // No space in bottom
                    break;
                }
                page = pages.getFirst();
                final boolean hasPrevious = adapter.hasPrevious();
                final int pagesTop = page.bounds().top;
                int offset = height - pagesBottom;
                if (!hasPrevious) {
                    if (pagesTop >= 0) {
                        // First layout page is the head page,
                        // and it fit screen top.
                        // Actually pagesTop can't be greater than 0.
                        break;
                    }
                    offset = Math.min(height - pagesBottom, pagesTop);
                }
                // Scroll down whole pages
                for (GalleryPageView p: pages) {
                    p.offsetTopAndBottom(offset);
                }

                if (!hasPrevious) {
                    // First layout page is the head page.
                    // Can't layout up.
                    break;
                }

                // Prepare for layout up
                topBound = pagesTop + offset - interval;

                // Layout up
                while (topBound > minY && adapter.hasPrevious()) {
                    adapter.previous();
                    page = getPageById(tempPages, adapter.getCurrentId(), true);
                    if (page == null) {
                        page = obtainPage();
                        galleryView.addComponent(page);
                        adapter.bind(page);
                    }
                    pages.addFirst(page);
                    page.measure(widthSpec, heightSpec);
                    page.layout(mOffsetX, topBound - page.getMeasuredHeight(), mOffsetX + pageWidth, topBound);
                    // Update topBound
                    topBound -= page.getMeasuredHeight() + interval;
                }
            }
        }

        // Remove remain page
        for (GalleryPageView p : tempPages) {
            removePage(p);
        }
        tempPages.clear();

        // Update mOffsetY
        if (!pages.isEmpty()) {
            page = pages.getFirst();
            mOffsetY = page.bounds().top;
        }
    }

    private void fillPagesHorizontal(int startOffset) {
        // TODO
    }

    private void fillPagesHorizontalReverse(int startOffset) {
        // TODO
    }

    private void fillPages(int startOffset) {
        switch (mMode) {
            case MODE_TOP_TO_BOTTOM:
                fillPagesVertical(startOffset);
                break;
            case MODE_LEFT_TO_RIGHT:
                fillPagesHorizontal(startOffset);
                break;
            case MODE_RIGHT_TO_LEFT:
                fillPagesHorizontalReverse(startOffset);
                break;
            default:
                throw new IllegalStateException("Invalid mode: " + mMode);
        }
    }

    @Override
    public void onFill() {
        final GalleryView.Adapter adapter = mAdapter;
        final GalleryView galleryView = mGalleryView;
        Assert.assertNotNull("The PagerLayoutManager is not attached", adapter);

        // Update bounds
        mScreenBounds.set(0, 0, galleryView.getWidth(), galleryView.getHeight());

        final LinkedList<GalleryPageView> pages = mPages;

        // Find keep index and keep top
        final int keepTopId;
        int keepTop = INVALID_TOP;
        if (mKeepTopPageId != GalleryView.Adapter.INVALID_ID) {
            keepTopId = mKeepTopPageId;
            keepTop = mKeepTop;
        } else if (mFirstShownPageId != GalleryView.Adapter.INVALID_ID) {
            keepTopId = mFirstShownPageId;
        } else {
            keepTopId = GalleryView.Adapter.INVALID_ID;
        }
        if (keepTopId != GalleryView.Adapter.INVALID_ID && keepTop == INVALID_TOP) {
            // Find keepTop now
            keepTop = mOffsetY;
            for (GalleryPageView page : pages) {
                // Check keep page
                if (keepTopId == page.getId()) {
                    break;
                }
                keepTop += page.getHeight() + mInterval;
            }
        }

        final int startOffset;
        if (keepTopId != GalleryView.Adapter.INVALID_ID
                && adapter.setCurrentId(keepTopId)) {
            startOffset = keepTop;
        } else {
            // Can't find keep top or can't go to keep top id, keep adapter position
            startOffset = mOffsetY;
        }
        fillPages(startOffset);

        // Get first shown image
        mFirstShownPageId = GalleryView.Adapter.INVALID_ID;
        for (GalleryPageView page : mPages) {
            // Check first shown loaded page
            if ((mScrollUp || mFlingUp) && !page.isLoaded()) {
                continue;
            }

            if (isInScreen(page)) {
                mFirstShownPageId = page.getId();
                break;
            }
        }
    }

    @Override
    public void onDown() {
        mDeltaX = 0;
        mDeltaY = 0;
        mScrollUp = false;
        mStopAnimationFinger = cancelAllAnimations();
    }

    @Override
    public void onUp() {
        mScrollUp = false;
        mGalleryView.getEdgeView().onRelease();
    }

    public void overScrollEdge(int dx, int dy, float x, float y) {
        final GLEdgeView edgeView = mGalleryView.getEdgeView();

        mDeltaX += dx;
        mDeltaY += dy;

        if (mDeltaX < 0) {
            edgeView.onPull(-mDeltaX, y, GLEdgeView.LEFT);
            if (!edgeView.isFinished(GLEdgeView.RIGHT)) {
                edgeView.onRelease(GLEdgeView.RIGHT);
            }
        } else if (mDeltaX > 0) {
            edgeView.onPull(mDeltaX, y, GLEdgeView.RIGHT);
            if (!edgeView.isFinished(GLEdgeView.LEFT)) {
                edgeView.onRelease(GLEdgeView.LEFT);
            }
        }

        if (mDeltaY < 0) {
            edgeView.onPull(-mDeltaY, x, GLEdgeView.TOP);
            if (!edgeView.isFinished(GLEdgeView.BOTTOM)) {
                edgeView.onRelease(GLEdgeView.BOTTOM);
            }
        } else if (mDeltaY > 0) {
            edgeView.onPull(mDeltaY, x, GLEdgeView.BOTTOM);
            if (!edgeView.isFinished(GLEdgeView.TOP)) {
                edgeView.onRelease(GLEdgeView.TOP);
            }
        }
    }

    private void getBottomState() {
        final List<GalleryPageView> pages = mPages;
        if (pages.isEmpty()) {
            Log.e(LOG_TAG, "No pages, can't get bottom state");
            return;
        }

        int bottom = mOffsetY;
        int i = 0;
        for (GalleryPageView page : pages) {
            if (i != 0) {
                bottom += mInterval;
            }
            bottom += page.getHeight();
            i++;
        }

        final boolean hasNext = !mAdapter.isTail(mPages.getLast().getId());

        mBottomStateBottom = bottom;
        mBottomStateHasNext = hasNext;
    }

    // True for get top or bottom
    private boolean scrollInternal(float dx, float dy, boolean fling, float x, float y) {
        if (mPages.size() <= 0) {
            return false;
        }

        final GalleryView galleryView = mGalleryView;
        final int width = galleryView.getWidth();
        final int height = galleryView.getHeight();
        final int pageWidth = (int) (width * mScale);
        final float reservation = getReservation();
        boolean requestFill = false;
        boolean result = false;

        final int margin = pageWidth - width;
        final int dxInt = (int) dx;
        if (margin > 0 && 0 != dxInt) {
            final int oldOffsetX = mOffsetX;
            final int exceptOffsetX = oldOffsetX - dxInt;
            mOffsetX = MathUtils.clamp(exceptOffsetX, -margin, 0);
            if (mOffsetX != oldOffsetX) {
                requestFill = true;
            }
            // Do not show over scroll effect for left and right
            /*
            int extraOffsetX = mOffsetX - exceptOffsetX;
            if (0 != extraOffsetX) {
                overScrollEdge(extraOffsetX, 0, x, y);
            }
            */
        }

        int remainY = (int) dy;
        while (remainY != 0) {
            if (remainY < 0) { // Try to show top
                final int limit;
                if (mAdapter.hasPrevious()) {
                    limit = (int) (-height * reservation) + mInterval;
                } else {
                    limit = 0;
                }

                if (mOffsetY - remainY <= limit) {
                    mOffsetY -= remainY;
                    remainY = 0;
                    requestFill = true;
                    mDeltaX = 0;
                    mDeltaY = 0;
                } else {
                    if (mAdapter.hasPrevious()) {
                        mOffsetY = limit;
                        remainY = remainY + limit - mOffsetY;
                        // Offset one pixel to avoid infinite loop
                        ++mOffsetY;
                        ++remainY;
                        galleryView.forceFill();
                        requestFill = false;
                        mDeltaX = 0;
                        mDeltaY = 0;
                    } else {
                        if (mOffsetY != limit) {
                            mOffsetY = limit;
                            requestFill = true;
                        }
                        if (!fling) {
                            overScrollEdge(0, remainY + limit - mOffsetY, x, y);
                        }
                        remainY = 0;
                        result = true;
                    }
                }
            } else { // Try to show bottom
                getBottomState();
                final int bottom = mBottomStateBottom;
                final boolean hasNext = mBottomStateHasNext;

                int limit;
                if (hasNext) {
                    limit = (int) (height * (1 + reservation)) - mInterval;
                } else {
                    limit = height;
                }
                // Fix limit for page not fill screen
                limit = Math.min(bottom, limit);

                if (bottom - remainY >= limit) {
                    mOffsetY -= remainY;
                    remainY = 0;
                    requestFill = true;
                    mDeltaX = 0;
                    mDeltaY = 0;
                } else {
                    if (hasNext) {
                        mOffsetY -= bottom - limit;
                        remainY = remainY + limit - bottom;
                        // Offset one pixel to avoid infinite loop
                        --mOffsetY;
                        --remainY;
                        galleryView.forceFill();
                        requestFill = false;
                        mDeltaX = 0;
                        mDeltaY = 0;
                    } else {
                        if (mOffsetY != limit) {
                            mOffsetY -= bottom - limit;
                            requestFill = true;
                        }
                        if (!fling) {
                            overScrollEdge(0, remainY + limit - bottom, x, y);
                        }
                        remainY = 0;
                        result = true;
                    }
                }
            }
        }

        if (requestFill) {
            mGalleryView.requestFill();
        }

        return result;
    }

    @Override
    public void onScroll(float dx, float dy, float totalX, float totalY, float x, float y) {
        mKeepTopPageId = GalleryView.Adapter.INVALID_ID;
        mKeepTop = INVALID_TOP;
        mScrollUp = dy < 0;
        scrollInternal(dx, dy, false, x, y);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        if (mPages.isEmpty()) {
            return;
        }

        mKeepTopPageId = GalleryView.Adapter.INVALID_ID;
        mKeepTop = INVALID_TOP;
        mFlingUp = velocityY > 0;

        final int maxX;
        final int minX;
        final int width = mGalleryView.getWidth();
        final int pageWidth = (int) (width * mScale);
        final int margin = pageWidth - width;
        if (margin > 0) {
            maxX = -mOffsetX;
            minX = -margin + mOffsetX;
        } else {
            maxX = 0;
            minX = 0;
        }

        final int maxY;
        if (mAdapter.hasPrevious()) {
            maxY = Integer.MAX_VALUE;
        } else {
            maxY = -mOffsetY;
        }

        getBottomState();
        final int bottom = mBottomStateBottom;
        final boolean hasNext = mBottomStateHasNext;
        final int minY;
        if (hasNext) {
            minY = Integer.MIN_VALUE;
        } else {
            minY = mGalleryView.getHeight() - bottom;
        }

        mPageFling.startFling((int) velocityX, minX, maxX,
                (int) velocityY, minY, maxY);
    }

    @Override
    public boolean canScale() {
        return !mPages.isEmpty();
    }

    @Override
    public void onScale(float focusX, float focusY, float scale) {
        if (mPages.isEmpty()) {
            return;
        }

        final float oldScale = mScale;
        mScale = MathUtils.clamp(oldScale * scale, MIN_SCALE, MAX_SCALE);
        scale = mScale / oldScale;

        if (mScale != oldScale) {
            GalleryPageView page = null;
            // Keep scale page origin position
            for (GalleryPageView p : mPages) {
                if (p.bounds().top < focusY) {
                    page = p;
                } else {
                    break;
                }
            }

            if (page != null) {
                mKeepTopPageId = page.getId();
                mKeepTop = page.bounds().top;

                mGalleryView.forceFill();
                final int oldKeepTop = mKeepTop;
                mKeepTop = INVALID_TOP;

                // Apply scroll
                final int newOffsetX = (int) (focusX - ((focusX - mOffsetX) * scale));
                final int newKeepTop;
                if (page.isLoaded()) {
                    newKeepTop = (int) (focusY - ((focusY - oldKeepTop) * scale));
                } else {
                    newKeepTop = oldKeepTop;
                }
                scrollInternal(mOffsetX - newOffsetX, oldKeepTop - newKeepTop, false, focusX, focusY);
            } else {
                Log.e(LOG_TAG, "Can't find target page");
                mKeepTopPageId = GalleryView.Adapter.INVALID_ID;
                mKeepTop = INVALID_TOP;
                mGalleryView.forceFill();
            }
        }
    }

    @Override
    public void onScaleToNextLevel(float x, float y) {
        if (mPages.isEmpty()) {
            return;
        }

        final float startScale = mScale;
        final float endScale;
        if (startScale < MAX_SCALE - SCALE_ERROR) {
            endScale = MAX_SCALE;
        } else {
            endScale = MIN_SCALE;
        }

        mSmoothScaler.startSmoothScaler(x, y, startScale, endScale, 300);
    }

    @Override
    public void onPagePrevious() {
        if (mPages.isEmpty()) {
            return;
        }

        ///////
        // UP
        ///////
        final GalleryView galleryView = mGalleryView;
        if (!mAdapter.hasPrevious() && mOffsetY >= 0) {
            mOverScroller.overScroll(GLEdgeView.TOP);
        } else {
            // Cancel all animations
            cancelAllAnimations();

            // Get first shown page
            GalleryPageView previousPage = null;
            GalleryPageView firstShownPage = null;
            for (GalleryPageView p: mPages) {
                if (isInScreen(p)) {
                    firstShownPage = p;
                    break;
                }
                previousPage = p;
            }

            final int height = galleryView.getHeight();
            final int maxOffset = height - mInterval;
            if (firstShownPage == null) {
                Log.e(LOG_TAG, "Can't find first shown page when paging left");
                mOffsetY += height / 2;
            } else {
                final int firstShownTop = firstShownPage.bounds().top;
                if (firstShownTop >= 0) {
                    if (previousPage == null) {
                        Log.e(LOG_TAG, "Can't find previous page when paging left and offsetY == 0");
                        mOffsetY += height / 2;
                    } else {
                        mOffsetY += Math.min(maxOffset, -previousPage.bounds().top);
                    }
                } else {
                    mOffsetY += Math.min(maxOffset, -firstShownTop);
                }
            }

            // Request fill
            mGalleryView.requestFill();
        }
    }

    @Override
    public void onPageNext() {
        if (mPages.isEmpty()) {
            return;
        }

        /////////
        // DOWN
        /////////
        final GalleryView galleryView = mGalleryView;
        getBottomState();
        final int bottom = mBottomStateBottom;
        final boolean hasNext = mBottomStateHasNext;
        if (!hasNext && bottom <= galleryView.getHeight()) {
            mOverScroller.overScroll(GLEdgeView.BOTTOM);
        } else {
            // Cancel all animations
            cancelAllAnimations();

            // Get first shown page
            GalleryPageView lastShownPage = null;
            GalleryPageView nextPage = null;
            for (GalleryPageView p: mPages) {
                if (isInScreen(p)) {
                    lastShownPage = p;
                } else if (null != lastShownPage) {
                    nextPage = p;
                    break;
                }
            }

            final int height = galleryView.getHeight();
            final int maxOffset = height - mInterval;
            if (null == lastShownPage) {
                Log.e(LOG_TAG, "Can't find last shown page when paging left");
                mOffsetY -= height / 2;
            } else {
                final int lastShownBottom = lastShownPage.bounds().bottom;
                if (lastShownBottom <= height) {
                    if (null == nextPage) {
                        Log.e(LOG_TAG, "Can't find previous page when paging left and offsetY == 0");
                        mOffsetY -= height / 2;
                    } else {
                        mOffsetY -= Math.min(maxOffset, nextPage.bounds().bottom - height);
                    }
                } else {
                    mOffsetY -= Math.min(maxOffset, lastShownBottom - height);
                }
            }

            // Request fill
            mGalleryView.requestFill();
        }
    }

    @Override
    public void onPageToId(int id) {
        if (mAdapter.setCurrentId(id)) {
            mKeepTopPageId = id;
            mKeepTop = INVALID_TOP;

            if (!mPages.isEmpty()) {
                // Fix the index page
                GalleryPageView targetPage = null;
                for (GalleryPageView page : mPages) {
                    if (page.getId() == id) {
                        targetPage = page;
                        break;
                    }
                }

                if (targetPage != null) {
                    // Cancel all animations
                    cancelAllAnimations();
                    mOffsetY -= targetPage.bounds().top;
                    // Request fill
                    mGalleryView.requestFill();
                } else {
                    mOffsetY = 0;
                    // Cancel all animations
                    cancelAllAnimations();
                    // Remove all pages
                    removeAllPages();
                    // Request fill
                    mGalleryView.requestFill();
                }
            }
        }
    }

    @Override
    public boolean onUpdateAnimation(long time) {
        boolean invalidate = mPageFling.calculate(time);
        invalidate |= mSmoothScaler.calculate(time);
        invalidate |= mOverScroller.calculate(time);
        return invalidate;
    }

    @Override
    public boolean isTouchActionValid() {
        return !mStopAnimationFinger;
    }

    @Override
    public GalleryPageView findPageById(int id) {
        for (GalleryPageView page : mPages) {
            if (page.getId() == id) {
                return page;
            }
        }
        return null;
    }

    @Override
    public int getIdUnder(float x, float y) {
        if (mPages.isEmpty()) {
            return GalleryView.Adapter.INVALID_ID;
        } else {
            final int intX = (int) x;
            final int intY = (int) y;
            for (GalleryPageView page : mPages) {
                if (page.bounds().contains(intX, intY)) {
                    return page.getId();
                }
            }
            return GalleryView.Adapter.INVALID_ID;
        }
    }

    @Override
    public int getCurrentId() {
        for (GalleryPageView page : mPages) {
            if (isInScreen(page)) {
                return page.getId();
            }
        }
        return GalleryView.Adapter.INVALID_ID;
    }

    @Override
    public void onDataChanged() {
        // Refill
        removeAllPages();
        mGalleryView.requestFill();
    }

    private class PageFling extends Fling {

        private int mVelocityX;
        private int mVelocityY;
        private int mDx;
        private int mDy;
        private int mLastX;
        private int mLastY;

        public PageFling(Context context) {
            super(context);
        }

        public void startFling(int velocityX, int minX, int maxX,
                int velocityY, int minY, int maxY) {
            mVelocityX = velocityX;
            mVelocityY = velocityY;
            mDx = (int) (getSplineFlingDistance(velocityX) * Math.signum(velocityX));
            mDy = (int) (getSplineFlingDistance(velocityY) * Math.signum(velocityY));
            mLastX = 0;
            mLastY = 0;
            int durationX = getSplineFlingDuration(velocityX);
            int durationY = getSplineFlingDuration(velocityY);

            if (mDx < minX) {
                durationX = adjustDuration(0, mDx, minX, durationX);
                mDx = minX;
            }
            if (mDx > maxX) {
                durationX = adjustDuration(0, mDx, maxX, durationX);
                mDx = maxX;
            }
            if (mDy < minY) {
                durationY = adjustDuration(0, mDy, minY, durationY);
                mDy = minY;
            }
            if (mDy > maxY) {
                durationY = adjustDuration(0, mDy, maxY, durationY);
                mDy = maxY;
            }

            if (mDx == 0 && mDy == 0) {
                return;
            }

            setDuration(Math.max(durationX, durationY));
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            final int x = (int) (mDx * progress);
            final int y = (int) (mDy * progress);
            final int offsetX = x - mLastX;
            final int offsetY = y - mLastY;
            if (scrollInternal(-offsetX, -offsetY, true, 0, 0)) {
                cancel();
                onFinish();
            }
            mLastX = x;
            mLastY = y;
        }

        @Override
        protected void onFinish() {
            mFlingUp = false;

            final boolean topEdge = !mAdapter.hasPrevious() && mOffsetY >= 0;

            getBottomState();
            final int bottom = mBottomStateBottom;
            final boolean hasNext = mBottomStateHasNext;
            final boolean bottomEdge = !hasNext && bottom <= mGalleryView.getHeight();

            if (topEdge && bottomEdge) {
                return;
            }

            final GLEdgeView edgeView = mGalleryView.getEdgeView();
            if (topEdge && edgeView.isFinished(GLEdgeView.TOP)) {
                edgeView.onAbsorb(mVelocityY, GLEdgeView.TOP);
            } else if (bottomEdge && edgeView.isFinished(GLEdgeView.BOTTOM)) {
                edgeView.onAbsorb(-mVelocityY, GLEdgeView.BOTTOM);
            }
        }
    }

    private class SmoothScaler extends Animation {

        private float mFocusX;
        private float mFocusY;
        private float mStartScale;
        private float mEndScale;
        private float mLastScale;

        public SmoothScaler() {
            setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        }

        public void startSmoothScaler(float focusX, float focusY,
                float startScale, float endScale, int duration) {
            mFocusX = focusX;
            mFocusY = focusY;
            mStartScale = startScale;
            mEndScale = endScale;
            mLastScale = startScale;
            setDuration(duration);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            if (mPages.size() <= 0) {
                return;
            }

            final float scale = MathUtils.lerp(mStartScale, mEndScale, progress);
            onScale(mFocusX, mFocusY, scale / mLastScale);
            mLastScale = scale;
        }
    }

    private class OverScroller extends FloatAnimation {

        private int mDirection;
        private int mPosition;

        public OverScroller() {
            setDuration(300L);
        }

        public void overScroll(int direction) {
            mDirection = direction;
            final int range;
            switch (mDirection) {
                case GLEdgeView.LEFT:
                case GLEdgeView.RIGHT:
                    range = mGalleryView.getWidth() / 7;
                    mPosition = mGalleryView.getHeight() / 2;
                    break;
                case GLEdgeView.TOP:
                case GLEdgeView.BOTTOM:
                    range = mGalleryView.getHeight() / 7;
                    mPosition = mGalleryView.getWidth() / 2;
                    break;
                default:
                    return;
            }
            setRange(0, range);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            super.onCalculate(progress);
            mGalleryView.getEdgeView().onPull(get(), mPosition, mDirection);
        }

        @Override
        protected void onFinish() {
            mGalleryView.getEdgeView().onRelease(mDirection);
        }
    }
}
