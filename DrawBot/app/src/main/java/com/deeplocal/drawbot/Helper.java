/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.deeplocal.drawbot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.Log;
import android.view.animation.TranslateAnimation;

import junit.framework.Assert;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class Helper {

    public static final int IMAGE_SIZE = 224;
    public static final String TAG = "ImageHelper";

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     */
    public static void saveBitmap(Context c, final Bitmap bitmap) {

        final File file = new File(c.getCacheDir(), "preview.png");

        Log.d(TAG, String.format("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), file.getAbsolutePath()));

        if (file.exists()) {
            file.delete();
        }

        try  {
            FileOutputStream fs = new FileOutputStream(file);
            BufferedOutputStream out = new BufferedOutputStream(fs);
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
            Log.w(TAG, "Could not save image for debugging. " + e.getMessage());
        }
    }

    public static void cropAndRescaleBitmap(final Bitmap src, final Bitmap dst, int sensorOrientation) {
        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        if (sensorOrientation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(sensorOrientation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }
}
