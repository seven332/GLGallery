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
 * Created by Hippo on 9/12/2016.
 */

import com.hippo.glview.view.GLRoot;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

public abstract class Postman extends GLRoot.Handler {

    private static final int INIT_SIZE = 5;

    private final List<Integer> mMethodList = new ArrayList<>(INIT_SIZE);
    private final List<Object[]> mArgsList = new ArrayList<>(INIT_SIZE);
    private final List<Integer> mMethodListTemp = new ArrayList<>(INIT_SIZE);
    private final List<Object[]> mArgsListTemp = new ArrayList<>(INIT_SIZE);

    void postMethod(int method, Object... args) {
        synchronized (this) {
            mMethodList.add(method);
            mArgsList.add(args);
        }
        request();
    }

    protected abstract void handleMethod(int method, Object... args);

    @Override
    public void onHandle(GL10 gl) {
        final List<Integer> methodList = mMethodListTemp;
        final List<Object[]> argsList = mArgsListTemp;
        synchronized (this) {
            if (mMethodList.isEmpty()) {
                return;
            }
            methodList.addAll(mMethodList);
            argsList.addAll(mArgsList);
            mMethodList.clear();
            mArgsList.clear();
        }

        for (int i = 0, n = methodList.size(); i < n; i++) {
            final int method = methodList.get(i);
            final Object[] args = argsList.get(i);
            handleMethod(method, args);
        }

        methodList.clear();
        argsList.clear();
    }
}
