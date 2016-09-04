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

import com.hippo.glview.widget.GLProgressView;

/**
 * A LayoutManager that only show a GLProgressView in center.
 */
class WaitingLayoutManager extends StaticLayoutManager {

    private final GLProgressView mProgress;

    public WaitingLayoutManager(@NonNull GalleryView galleryView,
            int progressSize, int progressColor, int backgroundColor) {
        super(galleryView);

        final GLProgressView progress = new GLProgressView();
        progress.setColor(progressColor);
        progress.setBgColor(backgroundColor);
        progress.setIndeterminate(true);
        progress.setMinimumWidth(progressSize);
        progress.setMinimumHeight(progressSize);
        mProgress = progress;
    }

    @Override
    protected void addViews() {
        mGalleryView.addComponent(mProgress);
    }

    @Override
    protected void removeViews() {
        mGalleryView.removeComponent(mProgress);
    }

    @Override
    public void onFill() {
        placeCenter(mProgress);
    }
}
