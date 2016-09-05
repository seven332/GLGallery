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

import com.hippo.glview.image.GLImageMovableTextView;
import com.hippo.glview.image.ImageMovableTextTexture;
import com.hippo.glview.image.ImageTexture;
import com.hippo.glview.view.Gravity;
import com.hippo.glview.widget.GLFrameLayout;
import com.hippo.glview.widget.GLLinearLayout;
import com.hippo.glview.widget.GLProgressView;
import com.hippo.glview.widget.GLTextureView;

public class GalleryPageView extends GLFrameLayout {

    public static final float PROGRESS_GONE = -1.0f;
    public static final float PROGRESS_INDETERMINATE = -2.0f;

    private final TextBinder mTextBinder;

    private final ImageView mImage;
    private final GLLinearLayout mInfo;
    private final GLImageMovableTextView mPage;
    private final GLTextureView mError;
    private final GLProgressView mProgress;

    private final int mMinHeight;

    private long mId = GalleryView.Adapter.INVALID_ID;

    public GalleryPageView(ImageMovableTextTexture pageTextTexture, TextBinder textBinder,
            int progressColor, int progressBgColor, int progressSize,
            int minHeight, int infoInterval) {
        mTextBinder = textBinder;

        // Add image
        mImage = new ImageView();
        GravityLayoutParams glp = new GravityLayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        addComponent(mImage, glp);

        // Add other panel
        mInfo = new GLLinearLayout();
        mInfo.setOrientation(GLLinearLayout.VERTICAL);
        mInfo.setInterval(infoInterval);
        glp = new GravityLayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.CENTER;
        addComponent(mInfo, glp);

        // Add page
        mPage = new GLImageMovableTextView();
        mPage.setTextTexture(pageTextTexture);
        GLLinearLayout.LayoutParams lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mPage, lp);

        // Add error
        mError = new GLTextureView();
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mError, lp);

        // Add progress
        mProgress = new GLProgressView();
        mProgress.setBgColor(progressBgColor);
        mProgress.setColor(progressColor);
        mProgress.setMinimumWidth(progressSize);
        mProgress.setMinimumHeight(progressSize);
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mProgress, lp);

        mMinHeight = minHeight;
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        // The height of the actual image may be smaller than mPageMinHeight.
        // Set min height as 0 when the image is visible.
        // For PageLayoutManager, min height is useless.
        if (mImage.getVisibility() == VISIBLE) {
            return 0;
        } else {
            return mMinHeight;
        }
    }

    public void setPageId(long id) {
        mId = id;
    }

    public long getPageId() {
        return mId;
    }

    /**
     * Clear all resources
     */
    public void clear() {
        setImage(null, null);
        setError(null);
    }

    /**
     * Show progress in the View. showIndex to decide whether show index text.
     * index is the number to show.
     */
    public void showProgress(float progress, boolean showIndex, int index) {
        showInfo();
        setImage(null, null);
        if (showIndex) {
            setIndex(index);
        } else {
            hideIndex();
        }
        setProgress(progress);
        setError(null);
    }

    /**
     * Show image in the View.
     */
    public void showImage(ImageTexture image, Rect rect) {
        showImage();
        setImage(image, rect);
        setProgress(GalleryPageView.PROGRESS_GONE);
        setError(null);
    }

    /**
     * Show error text in the View.
     */
    public void showError(String error, boolean showIndex, int index) {
        showInfo();
        setImage(null, null);
        if (showIndex) {
            setIndex(index);
        } else {
            hideIndex();
        }
        setProgress(GalleryPageView.PROGRESS_GONE);
        setError(error);
    }

    private void showImage() {
        mImage.setVisibility(VISIBLE);
        mInfo.setVisibility(GONE);
    }

    private void showInfo() {
        // For image valid rect
        mImage.setVisibility(INVISIBLE);
        mInfo.setVisibility(VISIBLE);
    }

    private void unbindImage() {
        final ImageTexture texture = mImage.getImageTexture();
        if (texture != null) {
            mImage.setImageTexture(null, null);
            texture.recycle();
        }
    }

    private void setImage(ImageTexture imageTexture, Rect rect) {
        unbindImage();
        if (imageTexture != null) {
            mImage.setImageTexture(imageTexture, rect);
        }
    }

    private void setIndex(int index) {
        mPage.setVisibility(VISIBLE);
        mPage.setText(Integer.toString(index));
    }

    private void hideIndex() {
        mPage.setVisibility(GONE);
    }

    private void setProgress(float progress) {
        if (progress == PROGRESS_GONE) {
            mProgress.setVisibility(GONE);
        } else if (progress == PROGRESS_INDETERMINATE) {
            mProgress.setVisibility(VISIBLE);
            mProgress.setIndeterminate(true);
        } else {
            mProgress.setVisibility(VISIBLE);
            mProgress.setIndeterminate(false);
            mProgress.setProgress(progress);
        }
    }

    private void setError(String error) {
        mTextBinder.unbindText(mError);
        if (error == null) {
            mError.setVisibility(GONE);
        } else {
            mError.setVisibility(VISIBLE);
            mTextBinder.bindText(mError, error);
        }
    }

    public ImageView getImageView() {
        return mImage;
    }

    public boolean isLoaded() {
        return mImage.getVisibility() == VISIBLE;
    }

    public interface TextBinder {
        void unbindText(GLTextureView view);
        void bindText(GLTextureView view, String str);
    }
}
