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

import android.support.annotation.NonNull;

public class SimpleAdapter extends GalleryView.Adapter {

    private final GalleryProvider mProvider;

    public SimpleAdapter(@NonNull GalleryProvider provider) {
        mProvider = provider;
    }

    @Override
    public void onBind(GalleryPageView view, int index) {
        mProvider.request(index);
        view.showInfo();
        view.setImage(null);
        view.setPage(index + 1);
        view.setProgress(GalleryPageView.PROGRESS_INDETERMINATE);
        view.setError(null, null);
    }

    @Override
    public void onUnbind(GalleryPageView view, int index) {
        mProvider.cancelRequest(index);
        view.setImage(null);
        view.setError(null, null);
    }

    @Override
    public String getError() {
        return mProvider.getError();
    }

    @Override
    public int size() {
        return mProvider.size();
    }
}
