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

import com.hippo.glview.glrenderer.BasicTexture;
import com.hippo.glview.glrenderer.StringTexture;
import com.hippo.glview.glrenderer.Texture;
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

    private final ImageView mImage;
    private final GLLinearLayout mInfo;
    private final GLImageMovableTextView mIndex;
    private final GLTextureView mText;
    private final GLProgressView mProgress;

    private final GalleryView mGalleryView;
    private final Params mParams;

    private long mId = GalleryView.Adapter.INVALID_ID;

    public static class Params {
        int progressSize;
        int progressColor;
        int progressBgColor;
        int pageMinWidth;
        int pageMinHeight;
        int infoInterval;
        int textSize;
        int textColor;
        int errorTextSize;
        int errorTextColor;
    }

    public GalleryPageView(GalleryView galleryView, Params params, ImageMovableTextTexture pageTextTexture) {
        // Add image
        mImage = new ImageView();
        GravityLayoutParams glp = new GravityLayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        addComponent(mImage, glp);

        // Add other panel
        mInfo = new GLLinearLayout();
        mInfo.setOrientation(GLLinearLayout.VERTICAL);
        mInfo.setInterval(params.infoInterval);
        glp = new GravityLayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        glp.gravity = Gravity.CENTER;
        addComponent(mInfo, glp);

        // Add page
        mIndex = new GLImageMovableTextView();
        mIndex.setTextTexture(pageTextTexture);
        GLLinearLayout.LayoutParams lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mIndex, lp);

        // Add error
        mText = new GLTextureView();
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mText, lp);

        // Add progress
        mProgress = new GLProgressView();
        mProgress.setBgColor(params.progressBgColor);
        mProgress.setColor(params.progressColor);
        mProgress.setMinimumWidth(params.progressSize);
        mProgress.setMinimumHeight(params.progressSize);
        lp = new GLLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        mInfo.addComponent(mProgress, lp);

        mGalleryView = galleryView;
        mParams = params;
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        // The width of the actual image may be smaller than mPageMinWidth.
        // Set min width as 0 when the image is visible.
        if ((mGalleryView.getLayoutMode() == GalleryView.LAYOUT_SCROLL_LEFT_TO_RIGHT
                || mGalleryView.getLayoutMode() == GalleryView.LAYOUT_SCROLL_RIGHT_TO_LEFT)
                && mImage.getVisibility() != VISIBLE) {
            return mParams.pageMinWidth;
        } else {
            return super.getSuggestedMinimumWidth();
        }
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        // The height of the actual image may be smaller than mPageMinHeight.
        // Set min height as 0 when the image is visible.
        if (mGalleryView.getLayoutMode() == GalleryView.LAYOUT_SCROLL_TOP_TO_BOTTOM
                && mImage.getVisibility() != VISIBLE) {
            return mParams.pageMinHeight;
        } else {
            return super.getSuggestedMinimumHeight();
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
     * The index will increase by to display.
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
     * Show only text in the View.
     */
    public void showText(String str) {
        showInfo();
        setImage(null, null);
        hideIndex();
        setProgress(GalleryPageView.PROGRESS_GONE);
        setText(str);
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
        mIndex.setVisibility(VISIBLE);
        mIndex.setText(Integer.toString(index + 1));
    }

    private void hideIndex() {
        mIndex.setVisibility(GONE);
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

    private void setText(String str) {
        unbindText(mText);
        if (str == null) {
            mText.setVisibility(GONE);
        } else {
            mText.setVisibility(VISIBLE);
            bindText(mText, str, mParams.textSize, mParams.textColor);
        }
    }

    private void setError(String error) {
        unbindText(mText);
        if (error == null) {
            mText.setVisibility(GONE);
        } else {
            mText.setVisibility(VISIBLE);
            bindText(mText, error, mParams.errorTextSize, mParams.errorTextColor);
        }
    }

    private void unbindText(GLTextureView view) {
        final Texture texture = view.getTexture();
        if (texture != null) {
            view.setTexture(null);
            if (texture instanceof BasicTexture) {
                ((BasicTexture) texture).recycle();
            }
        }
    }

    private void bindText(GLTextureView view, String str, int size, int color) {
        final Texture texture = StringTexture.newInstance(str, size, color);
        view.setTexture(texture);
    }

    public ImageView getImageView() {
        return mImage;
    }

    public boolean isLoaded() {
        return mImage.getVisibility() == VISIBLE;
    }
}
