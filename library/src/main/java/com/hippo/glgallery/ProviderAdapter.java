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
import com.hippo.yorozuya.MathUtils;

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

    private static final int INVALID_CHAPTER = -1;

    private static final int TEXT_OFFSET = 63;
    private static final long TEXT_MASK = 1L << TEXT_OFFSET;
    private static final int CHAPTER_OFFSET = 32;
    private static final long CHAPTER_MASK = 0x7fffffffL << CHAPTER_OFFSET;
    private static final int INDEX_OFFSET = 1;
    private static final long INDEX_MASK = 0x7fffffffL << INDEX_OFFSET;
    private static final int CLIP_OFFSET = 0;
    private static final long CLIP_MASK = 1L << CLIP_OFFSET;

    private final GalleryProvider mProvider;
    private final ImageTexture.Uploader mUploader;

    @Clip
    private int mClipMode = CLIP_NONE;
    private boolean mShowIndex = true;

    private int mChapterCount;
    private int[] mPageCountArray;
    private boolean[][] mClipsArray;

    private int mHeadChapter = INVALID_CHAPTER;
    private int mTailChapter = INVALID_CHAPTER;
    private int mHeadCheckedChapter = INVALID_CHAPTER;
    private int mTailCheckedChapter = INVALID_CHAPTER;

    // For page with image:
    //
    // 0   XXX...XXX   XXX...XXX   X
    // 1bit  31bit       31bit    1bit
    //      chapter      index    clip
    //
    // For page with text:
    //
    // 1   XXX...XXX   000...000
    // 1bit  31bit       32bit
    //      chapter
    private int mChapter;
    private int mPage;
    // false for first one, true for second one
    private boolean mClip;

    private final Rect mTemp = new Rect();

    public ProviderAdapter(@NonNull GLRootView glRootView, @NonNull GalleryProvider provider, int chapter, int page) {
        mProvider = provider;
        provider.setGLRoot(glRootView);
        provider.setListener(this);
        mUploader = new ImageTexture.Uploader(glRootView);

        mChapter = chapter;
        mPage = page;
        mClip = false;

        onUpdateChapterCount();
    }

    // INVALID_CHAPTER for failed
    private int nextNonEmptyChapter(int chapter) {
        final int[] pageCountArray = mPageCountArray;
        if (pageCountArray == null) {
            return INVALID_CHAPTER;
        }

        for (int i = chapter + 1, len = pageCountArray.length; i < len; ++i) {
            if (pageCountArray[i] != 0) {
                return i;
            }
        }

        return INVALID_CHAPTER;
    }

    // INVALID_CHAPTER for failed
    private int previousNonEmptyChapter(int chapter) {
        final int[] pageCountArray = mPageCountArray;
        if (pageCountArray == null) {
            return INVALID_CHAPTER;
        }

        for (int i = chapter - 1; i >= 0; --i) {
            if (pageCountArray[i] != 0) {
                return i;
            }
        }

        return INVALID_CHAPTER;
    }

    private void checkHeadTail(int seed) {
        final int[] pageCountArray = mPageCountArray;
        if (pageCountArray == null) {
            throw new IllegalStateException("Can't expand head and tail without chapter count known");
        }

        // Use current chapter as seed if no seed
        if (seed < 0 && seed >= mChapterCount) {
            seed = mChapter;
        }

        // Try to avoid empty seed
        if (pageCountArray[seed] == 0) {
            // seed chapter is empty, try to find first next non-empty chapter
            int newChapter = nextNonEmptyChapter(seed);
            if (newChapter == INVALID_CHAPTER) {
                // Can't find next non empty chapter, try to find first previous non-empty chapter
                newChapter = previousNonEmptyChapter(mChapter);
            }
            if (newChapter != INVALID_CHAPTER) {
                seed = newChapter;
            }
        }

        if (pageCountArray[seed] <= 0) {
            mHeadChapter = seed;
            mTailChapter = seed;
            mHeadCheckedChapter = seed;
            mTailCheckedChapter = seed;
            return;
        }

        mTailCheckedChapter = INVALID_CHAPTER;
        for (int i = seed, len = mChapterCount; i < len; ++i) {
            final int pageCount = pageCountArray[i];
            if (pageCount > 0) {
                // This chapter contain pages, check next
                mTailChapter = i;
            } else if (pageCount < 0) {
                // This chapter is not ready, stop here
                mTailChapter = i;
                mTailCheckedChapter = i;
                break;
            }
        }
        if (mTailCheckedChapter == INVALID_CHAPTER) {
            mTailCheckedChapter = mChapterCount - 1;
        }

        mHeadCheckedChapter = INVALID_CHAPTER;
        for (int i = seed; i >= 0; --i) {
            final int pageCount = pageCountArray[i];
            if (pageCount > 0) {
                // This chapter contain pages, check next
                mHeadChapter = i;
            } else if (pageCount < 0) {
                // This chapter is not ready, stop here
                mHeadChapter = i;
                mHeadCheckedChapter = i;
                break;
            }
        }
        if (mHeadCheckedChapter == INVALID_CHAPTER) {
            mHeadCheckedChapter = 0;
        }
    }

    private void onUpdateChapterCount() {
        final GalleryProvider provider = mProvider;
        final int chapterCount = provider.getChapterCount();
        mChapterCount = chapterCount;

        if (chapterCount > 0) {
            // Sanitize mChapter, avoid out of range
            mChapter = MathUtils.clamp(mChapter, 0, chapterCount - 1);

            final int[] pageCountArray = new int[chapterCount];
            final boolean[][] clipsArray = new boolean[chapterCount][];
            mPageCountArray = pageCountArray;
            mClipsArray = clipsArray;

            for (int i = 0; i < chapterCount; i++) {
                final int pageCount = provider.getPageCount(i);
                pageCountArray[i] = pageCount;

                if (pageCount > 0) {
                    clipsArray[i] = new boolean[pageCount];
                }
            }

            // Find tail chapter
            mHeadChapter = INVALID_CHAPTER;
            mTailChapter = INVALID_CHAPTER;
            mHeadCheckedChapter = INVALID_CHAPTER;
            mTailCheckedChapter = INVALID_CHAPTER;
            checkHeadTail(mChapter);

            // Sanitize mChapter, ensure mChapter in [mHeadChapter, mTailChapter]
            mChapter = MathUtils.clamp(mChapter, mHeadChapter, mTailChapter);

            // Sanitize mPage, avoid out of range
            final int pageCount = pageCountArray[mChapter];
            if (pageCount > 0) {
                mPage = MathUtils.clamp(mPage, 0, pageCount - 1);
                // No clip state, mClip always set false here
                mClip = false;
            }
        } else {
            mPageCountArray = null;
            mClipsArray = null;
            mHeadChapter = INVALID_CHAPTER;
            mTailChapter = INVALID_CHAPTER;
            mHeadCheckedChapter = INVALID_CHAPTER;
            mTailCheckedChapter = INVALID_CHAPTER;
        }
    }

    public void clearUploader() {
        mUploader.clear();
    }

    public void setClipMode(@Clip int clipMode) {
        if (mClipMode != clipMode) {
            mClipMode = clipMode;
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

    public static long genId(int chapter, int index, boolean clipIndex) {
        return ((long) chapter) << 32 | ((long) index) << 1 | (clipIndex ? 1 : 0);
    }

    public static long genId(int chapter) {
        return 1L << 63 | ((long) chapter) << 32;
    }

    private static int getChapter(long id) {
        return (int) ((id & CHAPTER_MASK) >>> CHAPTER_OFFSET);
    }

    private static boolean getIsText(long id) {
        return (id & TEXT_MASK) != 0;
    }

    private static int getPage(long id) {
        return (int) ((id & INDEX_MASK) >>> INDEX_OFFSET);
    }

    private static boolean getClip(long id) {
        return ((id & CLIP_MASK) >>> CLIP_OFFSET) != 0;
    }

    @Override
    public void next() {
        if (mChapterCount <= 0) {
            throw new IllegalStateException("Can't next, not data now");
        }
        final int pageCount = mPageCountArray[mChapter];
        if (pageCount == 0) {
            // Current chapter is empty
            throw new IllegalStateException("Can't next, current chapter is empty.");
        } else if (pageCount < 0) {
            if (mChapter == mTailChapter) {
                // Current chapter is tail chapter and tail chapter is not ready
                throw new IllegalStateException("Can't next, no next");
            }
            // Go to next chapter
        } else {
            // A chapter with pages, find next in this chapter first
            final boolean[] clips = mClipsArray[mChapter];
            final boolean clip = clips[mPage];

            if (mClip == clip && mPage == pageCount - 1) {
                if (mChapter == mTailChapter) {
                    throw new IllegalStateException("Can't next, no next");
                }
                // Go to next chapter
            } else {
                // Next image in this chapter
                if ((clip && mClip) | !clip) {
                    mClip = false;
                    ++mPage;
                } else {
                    mClip = true;
                }
                // Done
                return;
            }
        }

        // Next chapter
        mChapter = nextNonEmptyChapter(mChapter);
        if (mChapter == INVALID_CHAPTER || mChapter > mTailChapter) {
            throw new IllegalStateException("Can't next, internal error");
        }
        if (mPageCountArray[mChapter] > 0) {
            mPage = 0;
            mClip = false;
        }
    }

    @Override
    public void previous() {
        if (mChapterCount <= 0) {
            throw new IllegalStateException("Can't previous, not data now");
        }
        int pageCount = mPageCountArray[mChapter];
        if (pageCount == 0) {
            // Current chapter is empty
            throw new IllegalStateException("Can't previous, current chapter is empty.");
        } else if (pageCount < 0) {
            if (mChapter == mHeadChapter) {
                // Current chapter is head chapter and head chapter is not ready
                throw new IllegalStateException("Can't previous, no previous");
            }
            // Go to previous chapter
        } else {
            // A chapter with pages, find previous in this chapter first
            final boolean[] clips = mClipsArray[mChapter];

            if (!mClip && mPage == 0) {
                if (mChapter == mHeadChapter) {
                    throw new IllegalStateException("Can't previous, no previous");
                }
                // Go to previous chapter
            } else {
                // Previous image in this chapter
                if (mClip) {
                    mClip = false;
                } else {
                    --mPage;
                    mClip = clips[mPage];
                }
                // Done
                return;
            }
        }

        // Previous chapter
        mChapter = previousNonEmptyChapter(mChapter);
        if (mChapter == INVALID_CHAPTER || mChapter < mHeadChapter) {
            throw new IllegalStateException("Can't previous, internal error");
        }
        pageCount = mPageCountArray[mChapter];
        if (pageCount > 0) {
            mPage = pageCount - 1;
            mClip = mClipsArray[mChapter][mPage];
        }
    }

    @Override
    public boolean hasNext() {
        return !isTail(mChapter, mPage, mClip);
    }

    @Override
    public boolean hasPrevious() {
        return !isHead(mChapter, mPage, mClip);
    }

    // Return GalleryProvider.STATE_ERROR if can't get page count
    private int getPageCount(int chapter) {
        final int[] pageCountArray = mPageCountArray;
        return pageCountArray == null || chapter < 0 || chapter >= pageCountArray.length ?
                GalleryProvider.STATE_ERROR : pageCountArray[chapter];
    }

    @Override
    public long getCurrentId() {
        return getPageCount(mChapter) <= 0 ? genId(mChapter) : genId(mChapter, mPage, mClip);
    }

    @Override
    protected boolean setCurrentId(long id) {
        if (mChapterCount <= 0) {
            Log.e(LOG_TAG, "Can't setCurrentId, no data now.");
            return false;
        }

        final int chapter = getChapter(id);
        if (chapter < mHeadChapter || chapter > mTailChapter) {
            Log.e(LOG_TAG, "Can't setCurrentId, chapter out of range.");
            return false;
        }

        final int pageCount = mPageCountArray[chapter];
        if (getIsText(id)) {
            if ((pageCount == 0 && chapter == mHeadChapter && chapter == mTailChapter)
                    || (pageCount < 0 && (chapter == mHeadChapter || chapter == mTailChapter))) {
                mChapter = chapter;
                return true;
            } else {
                return false;
            }
        } else {
            if (pageCount <= 0) {
                return false;
            }

            final int page = getPage(id);
            if (page < 0 || page >= pageCount) {
                Log.e(LOG_TAG, "Can't setCurrentId, page out of range.");
                return false;
            }

            final boolean clip = getClip(id);
            if (!clip || mClipsArray[chapter][page]) {
                mChapter = chapter;
                mPage = page;
                mClip = clip;
                return true;
            } else {
                Log.e(LOG_TAG, "Can't setCurrentId, clip of range.");
                return false;
            }
        }
    }

    @Override
    public boolean isHead(long id) {
        final int chapter = getChapter(id);
        if (getIsText(id)) {
            return chapter == mHeadChapter;
        } else {
            final int page = getPage(id);
            final boolean clip = getClip(id);
            return isHead(chapter, page, clip);
        }
    }

    @Override
    public boolean isTail(long id) {
        final int chapter = getChapter(id);
        if (getIsText(id)) {
            return chapter == mTailChapter;
        } else {
            final int page = getPage(id);
            final boolean clip = getClip(id);
            return isTail(chapter, page, clip);
        }
    }

    @Override
    public String idToString(long id) {
        return "chapter = " + getChapter(id) + ", page = " + getPage(id) + ", clip = " + getClip(id);
    }

    private boolean isHead(int chapter, int page, boolean clip) {
        if (mChapterCount <= 0) {
            Log.e(LOG_TAG, "Can't check isHead, no data now.");
            return true;
        }
        final int pageCount = mPageCountArray[chapter];
        if (pageCount == 0) {
            Log.e(LOG_TAG, "Can't check isHead, this chapter is empty.");
            return true;
        }
        if (chapter < mHeadChapter || chapter > mTailChapter) {
            Log.e(LOG_TAG, "Can't check isHead, chapter out of range.");
            return true;
        }

        return chapter == mHeadChapter && (pageCount < 0 || (page == 0 && !clip));
    }

    private boolean isTail(int chapter, int page, boolean clip) {
        if (mChapterCount <= 0) {
            Log.e(LOG_TAG, "Can't check isHead, no data now.");
            return true;
        }
        final int pageCount = mPageCountArray[chapter];
        if (pageCount == 0) {
            Log.e(LOG_TAG, "Can't check isHead, this chapter is empty.");
            return true;
        }
        if (chapter < mHeadChapter || chapter > mTailChapter) {
            Log.e(LOG_TAG, "Can't check isHead, chapter out of range.");
            return true;
        }

        return chapter == mTailChapter && (pageCount < 0 || (page >= pageCount - 1 && clip == mClipsArray[chapter][pageCount - 1]));
    }

    private void bindView(GalleryPageView view, int chapter, int page, boolean clip) {
        final ImageData image = mProvider.request(chapter, page);
        if (image != null) {
            bindView(view, clip, image);
        } else {
            view.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, mShowIndex, page);
        }
    }

    @Override
    public void onBind(GalleryPageView view) {
        if (mChapterCount <= 0) {
            return;
        }

        final int pageCount = getPageCount(mChapter);
        if (pageCount == GalleryProvider.STATE_ERROR) {
            view.showError(mProvider.getError(mChapter), false, 0);
        } else if (pageCount == GalleryProvider.STATE_WAIT) {
            mProvider.requestChapter(mChapter);
            view.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, false, 0);
        } else if (pageCount == 0) {
            view.showError(mGalleryView.getEmptyStr(), false, 0);
        } else if (pageCount > 0) {
            bindView(view, mChapter, mPage, mClip);
        } else {
            throw new IllegalStateException("Invalid page count");
        }
    }

    @Override
    public void onUnbind(GalleryPageView view, long id) {
        if (!getIsText(id)) {
            mProvider.cancelRequest(getChapter(id), getPage(id));
        }
        view.clear();
    }

    @Override
    public String getError() {
        return mProvider.getError();
    }

    @Override
    public int getState() {
        final int chapterCount = mChapterCount;
        if (chapterCount == GalleryProvider.STATE_ERROR) {
            return STATE_ERROR;
        } else if (chapterCount == GalleryProvider.STATE_WAIT) {
            return STATE_WAIT;
        } else if (chapterCount == 0) {
            return STATE_EMPTY;
        } else if (chapterCount > 0) {
            return STATE_READY;
        } else {
            throw new IllegalStateException("Invalid chapter count: " + chapterCount);
        }
    }

    @Override
    public void onStateChanged() {
        onUpdateChapterCount();
        notifyStateChanged();
    }

    @Override
    public void onChapterStateChanged(int chapter) {
        if (mChapterCount <= 0) {
            return;
        }
        if (chapter < 0 || chapter >= mChapterCount) {
            // Out of range
            return;
        }

        final int oldChapter = mChapter;
        final int oldPageCount = mPageCountArray[chapter];
        final int newPageCount = mProvider.getPageCount(chapter);
        final int oldHeadChapter = mHeadChapter;
        final int oldTailChapter = mTailChapter;

        mPageCountArray[chapter] = newPageCount;
        mClipsArray[chapter] = new boolean[newPageCount];

        checkHeadTail(mChapter);

        // Sanitize mChapter, ensure mChapter in [mHeadChapter, mTailChapter]
        mChapter = MathUtils.clamp(mChapter, mHeadChapter, mTailChapter);

        // Check page and clip
        if (mChapter == oldChapter) {
            if (newPageCount > 0) {
                if (mChapter == mHeadChapter && mChapter != mTailChapter) {
                    mPage = newPageCount - 1;
                    mClip = false;
                } else if (mChapter == mTailChapter && mChapter != mHeadChapter) {
                    mPage = 0;
                    mClip = false;
                } else {
                    mPage = MathUtils.clamp(mPage, 0, newPageCount - 1);
                    mClip = false;
                }
            }
        } else if (mChapter > oldChapter) {
            mPage = 0;
            mClip = false;
        } else if (mChapter < oldChapter) {
            mPage = newPageCount - 1;
            mClip = false;
        }

        notifyDataChanged();
    }

    @Override
    public void onPageWait(int chapter, int page) {
        if (mChapterCount <= 0 || mClipsArray == null ||
                chapter < 0 || chapter >= mClipsArray.length) {
            return;
        }
        final boolean[] clips = mClipsArray[chapter];
        if (clips == null || page < 0 || page >= clips.length) {
            return;
        }

        final GalleryPageView page1 = findPageById(genId(chapter, page, false));
        final GalleryPageView page2 = clips[page] ? findPageById(genId(chapter, page, true)) : null;
        if (page1 != null) {
            page1.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, mShowIndex, page);
        }
        if (page2 != null) {
            page2.showProgress(GalleryPageView.PROGRESS_INDETERMINATE, mShowIndex, page);
        }
    }

    @Override
    public void onPagePercent(int chapter, int page, float percent) {
        if (mChapterCount <= 0 || mClipsArray == null ||
                chapter < 0 || chapter >= mClipsArray.length) {
            return;
        }
        final boolean[] clips = mClipsArray[chapter];
        if (clips == null || page < 0 || page >= clips.length) {
            return;
        }

        final GalleryPageView page1 = findPageById(genId(chapter, page, false));
        final GalleryPageView page2 = clips[page] ? findPageById(genId(chapter, page, false)) : null;
        if (page1 != null) {
            page1.showProgress(percent, mShowIndex, page);
        }
        if (page2 != null) {
            page2.showProgress(percent, mShowIndex, page);
        }
    }

    private void bindView(GalleryPageView page, boolean clip, ImageData image) {
        // Upload the texture
        final ImageTexture imageTexture = new ImageTexture(image);
        mUploader.addTexture(imageTexture);

        final int width = image.getWidth();
        final int height = image.getHeight();

        // Get clip rect
        final Rect temp = mTemp;
        if (mClipMode == CLIP_NONE || (float) width / (float) height < CLIP_LIMIT) {
            temp.set(0, 0, width, height);
        } else if ((mClipMode == CLIP_LEFT_RIGHT && !clip) || (mClipMode == CLIP_RIGHT_LEFT && clip)) {
            temp.set(0, 0, width / 2, height);
        } else if ((mClipMode == CLIP_LEFT_RIGHT && clip) || (mClipMode == CLIP_RIGHT_LEFT && !clip)) {
            temp.set(width / 2, 0, width, height);
        } else {
            throw new IllegalStateException("Invalid clip: " + mClipMode);
        }
        page.showImage(imageTexture, temp);
    }

    @Override
    public void onPageSucceed(int chapter, int page, ImageData image) {
        if (mChapterCount <= 0 || mClipsArray == null ||
                chapter < 0 || chapter >= mClipsArray.length) {
            return;
        }
        final boolean[] clips = mClipsArray[chapter];
        if (clips == null || page < 0 || page >= clips.length) {
            return;
        }

        // Check clip
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean newClip = mClipMode != CLIP_NONE && (float) width / (float) height >= CLIP_LIMIT;
        final boolean oldClip = clips[page];
        if ((!oldClip && newClip) || (oldClip && !newClip)) {
            notifyDataChanged();
        }
        clips[page] = newClip;

        final GalleryPageView page1 = findPageById(genId(chapter, page, false));
        final GalleryPageView page2 = newClip ? findPageById(genId(chapter, page, true)) : null;

        image.addReference();
        if (page1 != null || page2 != null) {
            if (page1 != null) {
                bindView(page1, false, image);
            }
            if (page2 != null) {
                bindView(page2, true, image);
            }
        }
        image.removeReference();
    }

    @Override
    public void onPageFailed(int chapter, int page, String error) {
        if (mChapterCount <= 0 || mClipsArray == null ||
                chapter < 0 || chapter >= mClipsArray.length) {
            return;
        }
        final boolean[] clips = mClipsArray[chapter];
        if (clips == null || page < 0 || page >= clips.length) {
            return;
        }

        final GalleryPageView page1 = findPageById(genId(chapter, page, false));
        final GalleryPageView page2 = clips[page] ? findPageById(genId(chapter, page, true)) : null;
        if (page1 != null) {
            page1.showError(error, mShowIndex, page);
        }
        if (page2 != null) {
            page2.showError(error, mShowIndex, page);
        }
    }

    @Override
    public void onDataChanged(int chapter, int page) {
        if (mChapterCount <= 0 || mClipsArray == null ||
                chapter < 0 || chapter >= mClipsArray.length) {
            return;
        }
        final boolean[] clips = mClipsArray[chapter];
        if (clips == null || page < 0 || page >= clips.length) {
            return;
        }

        final GalleryPageView page1 = findPageById(genId(chapter, page, false));
        final GalleryPageView page2 = clips[page] ? findPageById(genId(chapter, page, true)) : null;
        if (page1 != null || page2 != null) {
            final ImageData image = mProvider.request(chapter, page);
            if (image != null) {
                if (page1 != null) {
                    bindView(page1, false, image);
                }
                if (page2 != null) {
                    bindView(page1, true, image);
                }
            }
        }
    }

    private GalleryPageView findPageById(long id) {
        return mGalleryView != null ? mGalleryView.findPageById(id) : null;
    }
}
