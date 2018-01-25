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
import android.graphics.RectF;
import android.support.annotation.Nullable;

import com.hippo.glview.anim.AlphaAnimation;
import com.hippo.glview.glrenderer.GLCanvas;
import com.hippo.glview.glrenderer.Texture;
import com.hippo.glview.image.ImageTexture;
import com.hippo.glview.view.GLView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.MathUtils;

import java.util.Arrays;

class ImageView extends GLView implements ImageTexture.Callback {

    public static final int SCALE_ORIGIN = 0;
    public static final int SCALE_FIT_WIDTH = 1;
    public static final int SCALE_FIT_HEIGHT = 2;
    public static final int SCALE_FIT = 3;
    public static final int SCALE_FIXED = 4;

    public static final int START_POSITION_TOP_LEFT = 0;
    public static final int START_POSITION_TOP_RIGHT = 1;
    public static final int START_POSITION_BOTTOM_LEFT = 2;
    public static final int START_POSITION_BOTTOM_RIGHT = 3;
    public static final int START_POSITION_CENTER = 4;

    private static final long ALPHA_ANIMATION_DURING = 200L;

    private ImageTexture mImage;
    private int mClipLeft;
    private int mClipTop;
    private int mClipRight;
    private int mClipBottom;

    // The area in view for whole content
    private final RectF mDst = new RectF();
    // The area in image to draw
    private final RectF mSrcActual = new RectF();
    // The area in view to draw
    private final RectF mDstActual = new RectF();
    // The area can be seen in Screen
    private final Rect mValidRect = new Rect();

    private int mScaleMode = SCALE_FIT;
    private int mStartPosition = START_POSITION_TOP_RIGHT;
    // The scale value that setScaleOffset() passed
    private float mAssignedScale = 1.0f;
    // The scale value that shows actually
    private float mActualScale = 1.0f;

    // The scale to fit width.
    private float mWidthScale;
    // The scale to fit height.
    private float mHeightScale;
    // The scale to fit view.
    private float mFitScale;
    // The max value that scale can be.
    private float mMaxScale;
    // The min value that scale can be.
    private float mMinScale;
    // mWidthScale, mHeightScale, mFitScale, Math.min(mFitScale * 2, mMaxScale)
    private final float[] mScaleArray = new float[4];

    // True for call setScaleOffset() before rendering
    private boolean mScaleOffsetDirty = true;
    // True for call applyPositionInRoot() before rendering
    private boolean mPositionInRootDirty = true;

    // The alpha animation to make image show up smoothly
    private AlphaAnimation mAlphaAnimation;

    /**
     * Return the content width. It might be smaller
     * than image width if apply clip rect.
     */
    public int getContentWidth() {
        return mClipRight - mClipLeft;
    }

    /**
     * Return the content height. It might be smaller
     * than image height if apply clip rect.
     */
    public int getContentHeight() {
        return mClipBottom - mClipTop;
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return Math.max(super.getSuggestedMinimumWidth(),
                mImage == null ? 0 : getContentWidth());
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return Math.max(super.getSuggestedMinimumHeight(),
                mImage == null ? 0 : getContentHeight());
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (mImage == null) {
            super.onMeasure(widthSpec, heightSpec);
        } else {
            final float ratio = (float) getContentWidth() / getContentHeight();
            final int widthSize = MeasureSpec.getSize(widthSpec);
            final int heightSize = MeasureSpec.getSize(heightSpec);
            final int widthMode = MeasureSpec.getMode(widthSpec);
            final int heightMode = MeasureSpec.getMode(heightSpec);
            int measureWidth = -1;
            int measureHeight = -1;

            // Try to make the ratio of this view is the same as
            // the ratio of the image.
            if (widthMode == MeasureSpec.EXACTLY) {
                measureWidth = widthSize;
                if (heightMode == MeasureSpec.EXACTLY) {
                    measureHeight = heightSize;
                } else {
                    measureHeight = (int) (widthSize / ratio);
                    if (heightMode == MeasureSpec.AT_MOST) {
                        measureHeight = Math.min(measureHeight, heightSize);
                    }
                }
            } else if (heightMode == MeasureSpec.EXACTLY) {
                measureHeight = heightSize;
                measureWidth = (int) (heightSize * ratio);
                if (widthMode == MeasureSpec.AT_MOST) {
                    measureWidth = Math.min(measureWidth, widthSize);
                }
            }

            if (measureWidth == -1 || measureHeight == -1) {
                super.onMeasure(widthSpec, heightSpec);
            } else {
                setMeasuredSize(measureWidth, measureHeight);
            }
        }
    }

    @Override
    protected void onSizeChanged(int newW, int newH, int oldW, int oldH) {
        // Update all scale value
        if (mImage != null) {
            updateScale();
        }

        // Trigger update render rect
        mScaleOffsetDirty = true;
        mPositionInRootDirty = true;
    }

    @Override
    protected void onPositionInRootChanged(int x, int y, int oldX, int oldY) {
        mPositionInRootDirty = true;

        if (mImage != null) {
            getValidRect(mValidRect);
            if (!mValidRect.isEmpty()) {
                mImage.start();
            } else {
                mImage.stop();
            }
        }
    }

    @Nullable
    public float[] getSuggestedScaleLevel() {
        return mImage != null ? mScaleArray : null;
    }

    private void updateScale() {
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();
        if (viewHeight <= 0 || viewHeight <= 0) {
            return;
        }
        final int contentWidth = getContentWidth();
        final int contentHeight = getContentHeight();

        final float widthScale = (float) viewWidth / contentWidth;
        final float heightScale = (float) viewHeight / contentHeight;
        final float fitScale = Math.min(widthScale, heightScale);

        mWidthScale = widthScale;
        mHeightScale = heightScale;
        mFitScale = fitScale;
        mMaxScale = Math.max(10.0f, fitScale);
        mMinScale = Math.min(1.0f / 10.0f, fitScale);

        final float[] scaleArray = mScaleArray;
        scaleArray[0] = mWidthScale;
        scaleArray[0] = mHeightScale;
        scaleArray[0] = mFitScale;
        scaleArray[0] = Math.min(mFitScale * 2, mMaxScale);
        Arrays.sort(scaleArray);
    }

    private void ensureAlphaAnimation() {
        if (mAlphaAnimation == null) {
            mAlphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            mAlphaAnimation.setDuration(ALPHA_ANIMATION_DURING);
            mAlphaAnimation.setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        }
    }

    /**
     * Set ImageTexture for the ImageView.
     *
     * @param clipRect it will be ignored if {@code null} or empty or no intersection.
     */
    public void setImageTexture(ImageTexture image, Rect clipRect) {
        // Clean old image
        if (mImage != null) {
            mImage.setCallback(null);
            mImage.stop();
        }

        final int oldContentWidth = getContentWidth();
        final int oldContentHeight = getContentHeight();

        mImage = image;

        if (image != null) {
            image.setCallback(this);

            final int imageWidth = image.getWidth();
            final int imageHeight = image.getHeight();

            if (clipRect == null || !clipRect.intersect(0, 0, imageWidth, imageHeight) || clipRect.isEmpty()) {
                mClipLeft = 0;
                mClipTop = 0;
                mClipRight = imageWidth;
                mClipBottom = imageHeight;
            } else {
                mClipLeft = clipRect.left;
                mClipTop = clipRect.top;
                mClipRight = clipRect.right;
                mClipBottom = clipRect.bottom;
            }

            updateScale();

            getValidRect(mValidRect);
            if (!mValidRect.isEmpty()) {
                mImage.start();
            }

            // Only show alpha animation for the ImageView which can be seen.
            //getValidRect(mValidRect);
            //if (!mValidRect.isEmpty()) {
            //    ensureAlphaAnimation();
            //    startAnimation(mAlphaAnimation, true);
            //}
        } else {
            mClipLeft = 0;
            mClipTop = 0;
            mClipRight = 1;
            mClipBottom = 1;
        }

        mScaleOffsetDirty = true;
        mPositionInRootDirty = true;

        if (oldContentWidth != getContentWidth() || oldContentHeight != getContentHeight()) {
            requestLayout();
        }
    }

    public ImageTexture getImageTexture() {
        return mImage;
    }

    public boolean isLoaded() {
        return mImage != null;
    }

    public boolean canFlingVertically() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.top < 0.0f || mDst.bottom > getHeight();
    }

    public boolean canFlingHorizontally() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.left < 0.0f || mDst.right > getWidth();
    }

    public boolean canFling() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return false;
            }
        }

        return mDst.left < 0.0f || mDst.top < 0.0f || mDst.right > getWidth() || mDst.bottom > getHeight();
    }

    public int getMaxDx() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.max(0, -(int) mDst.left);
    }

    public int getMinDx() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.min(0, getWidth() - (int) mDst.right);
    }

    public int getMaxDy() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.max(0, -(int) mDst.top);
    }

    public int getMinDy() {
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
            if (mScaleOffsetDirty) {
                return 0;
            }
        }
        return Math.min(0, getHeight() - (int) mDst.bottom);
    }

    public float getScale() {
        return mActualScale;
    }

    // If target is smaller then view, make it in screen center.
    // If target is larger then view, make it fill screen.
    private void adjustPosition() {
        final RectF dst = mDst;
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();
        final float targetWidth = dst.width();
        final float targetHeight = dst.height();

        if (targetWidth > viewWidth) {
            float fixXOffset = dst.left;
            if (fixXOffset > 0) {
                dst.left -= fixXOffset;
                dst.right -= fixXOffset;
            } else if ((fixXOffset = viewWidth - dst.right) > 0) {
                dst.left += fixXOffset;
                dst.right += fixXOffset;
            }
        } else {
            final float left = (viewWidth - targetWidth) / 2;
            dst.offsetTo(left, dst.top);
        }
        if (targetHeight > viewHeight) {
            float fixYOffset = dst.top;
            if (fixYOffset > 0) {
                dst.top -= fixYOffset;
                dst.bottom -= fixYOffset;
            } else if ((fixYOffset = viewHeight - dst.bottom) > 0) {
                dst.top += fixYOffset;
                dst.bottom += fixYOffset;
            }
        } else {
            final float top = (viewHeight - targetHeight) / 2;
            dst.offsetTo(dst.left, top);
        }
    }

    public void setScaleOffset(int scaleMode, int startPosition, float scaleValue) {
        mScaleMode = scaleMode;
        mStartPosition = startPosition;
        mAssignedScale = scaleValue;

        final int viewWidth = getWidth();
        final int viewHeight = getHeight();

        if (mImage == null || viewWidth <= 0 || viewHeight <= 0) {
            // Can't handle it now, pend it.
            mScaleOffsetDirty = true;
            return;
        }

        final int contentWidth = getContentWidth();
        final int contentHeight = getContentHeight();

        // Set scale
        final float targetWidth;
        final float targetHeight;
        switch (scaleMode) {
            case SCALE_ORIGIN:
                mActualScale = 1.0f;
                targetWidth = contentWidth;
                targetHeight = contentHeight;
                break;
            case SCALE_FIT_WIDTH:
                mActualScale = mWidthScale;
                targetWidth = viewWidth;
                targetHeight = contentHeight * mActualScale;
                break;
            case SCALE_FIT_HEIGHT:
                mActualScale = mHeightScale;
                targetWidth = contentWidth * mActualScale;
                targetHeight = viewHeight;
                break;
            case SCALE_FIT:
                mActualScale = mFitScale;
                targetWidth = contentWidth * mActualScale;
                targetHeight = contentHeight * mActualScale;
                break;
            case SCALE_FIXED:
            default:
                // Adjust scale, not too big, not too small
                mActualScale = Math.max(Math.min(scaleValue, mMaxScale), mMinScale);
                targetWidth = contentWidth * scaleValue;
                targetHeight = contentHeight * scaleValue;
                break;
        }

        // Set mDst.left and mDst.right
        final RectF dst = mDst;
        switch (startPosition) {
            case START_POSITION_TOP_LEFT:
                dst.left = 0;
                dst.top = 0;
                break;
            case START_POSITION_TOP_RIGHT:
                dst.left = viewWidth - targetWidth;
                dst.top = 0;
                break;
            case START_POSITION_BOTTOM_LEFT:
                dst.left = 0;
                dst.top = viewHeight - targetHeight;
                break;
            case START_POSITION_BOTTOM_RIGHT:
                dst.left = viewWidth - targetWidth;
                dst.top = viewHeight - targetHeight;
                break;
            case START_POSITION_CENTER:
            default:
                dst.left = (viewWidth - targetWidth) / 2;
                dst.top = (viewHeight - targetHeight) / 2;
                break;
        }

        // Set mDst.right and mDst.bottom
        dst.right = dst.left + targetWidth;
        dst.bottom = dst.top + targetHeight;

        // Adjust position
        adjustPosition();

        mScaleOffsetDirty = false;
        mPositionInRootDirty = true;
    }

    public void scroll(int dx, int dy, int[] remain) {
        // Only work after layout
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
        }
        if (mScaleOffsetDirty) {
            remain[0] = dx;
            remain[1] = dy;
            return;
        }

        final RectF dst = mDst;
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();
        final float targetWidth = dst.width();
        final float targetHeight = dst.height();

        if (targetWidth > viewWidth) {
            dst.left -= dx;
            dst.right -= dx;

            float fixXOffset = dst.left;
            if (fixXOffset > 0) {
                dst.left -= fixXOffset;
                dst.right -= fixXOffset;
                remain[0] = -(int) fixXOffset;
            } else if ((fixXOffset = viewWidth - dst.right) > 0) {
                dst.left += fixXOffset;
                dst.right += fixXOffset;
                remain[0] = (int) fixXOffset;
            } else {
                remain[0] = 0;
            }
        } else {
            remain[0] = dx;
        }
        if (targetHeight > viewHeight) {
            dst.top -= dy;
            dst.bottom -= dy;

            float fixYOffset = dst.top;
            if (fixYOffset > 0) {
                dst.top -= fixYOffset;
                dst.bottom -= fixYOffset;
                remain[1] = -(int) fixYOffset;
            } else if ((fixYOffset = viewHeight - dst.bottom) > 0) {
                dst.top += fixYOffset;
                dst.bottom += fixYOffset;
                remain[1] = (int) fixYOffset;
            } else {
                remain[1] = 0;
            }
        } else {
            remain[1] = dy;
        }

        // If image scrolled, need update position in root.
        if (dx != remain[0] || dy != remain[1]) {
            mPositionInRootDirty = true;
            invalidate();
        }
    }

    public void scale(float focusX, float focusY, float scale) {
        // Only work after layout
        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
        }
        if (mScaleOffsetDirty) {
            return;
        }

        // Check scale limit
        if ((mActualScale >= mMaxScale && scale >= 1.0f) || (mActualScale <= mMinScale && scale < 1.0f)) {
            return;
        }

        final float newScale = MathUtils.clamp(mActualScale * scale, mMinScale, mMaxScale);
        mActualScale = newScale;

        final RectF dst = mDst;
        final float left = (focusX - ((focusX - dst.left) * scale));
        final float top = (focusY - ((focusY - dst.top) * scale));
        dst.set(left, top,
                (left + (getContentWidth() * newScale)),
                (top + (getContentHeight() * newScale)));

        // Adjust position
        adjustPosition();

        mPositionInRootDirty = true;
        invalidate();
    }

    private void applyPositionInRoot() {
        final Rect validRect = mValidRect;
        final RectF dst = mDst;
        final RectF dstActual = mDstActual;
        final RectF srcActual = mSrcActual;

        dstActual.set(dst);
        getValidRect(validRect);
        if (dstActual.intersect(validRect.left, validRect.top, validRect.right, validRect.bottom)) {
            srcActual.left = MathUtils.lerp(mClipLeft, mClipRight,
                    MathUtils.norm(dst.left, dst.right, dstActual.left));
            srcActual.right = MathUtils.lerp(mClipLeft, mClipRight,
                    MathUtils.norm(dst.left, dst.right, dstActual.right));
            srcActual.top = MathUtils.lerp(mClipTop, mClipBottom,
                    MathUtils.norm(dst.top, dst.bottom, dstActual.top));
            srcActual.bottom = MathUtils.lerp(mClipTop, mClipBottom,
                    MathUtils.norm(dst.top, dst.bottom, dstActual.bottom));
        } else {
            // Can't be seen, set src and dst empty
            srcActual.setEmpty();
            dstActual.setEmpty();
        }

        mPositionInRootDirty = false;
    }

    @Override
    public void onRender(GLCanvas canvas) {
        final Texture texture = mImage;
        if (texture == null) {
            return;
        }

        if (mScaleOffsetDirty) {
            setScaleOffset(mScaleMode, mStartPosition, mAssignedScale);
        }

        if (mPositionInRootDirty) {
            applyPositionInRoot();
        }

        if (!mSrcActual.isEmpty()) {
            texture.draw(canvas, mSrcActual, mDstActual);
        }
    }

    @Override
    public void invalidateImageTexture(ImageTexture who) {
        invalidate();
    }
}
