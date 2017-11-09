package com.deeplocal.drawbot;

import android.util.Log;

import com.google.android.things.contrib.driver.apa102.Apa102;

import java.io.IOException;

import android.graphics.Color;

public class PhysicalInterface {

    private static final String TAG = "drawbot";

    public Apa102 mApa102;

    public PhysicalInterface() {

        try {
            mApa102 = new Apa102("SPI3.0", Apa102.Mode.BGR);
        } catch (IOException e) {
            Log.e(TAG, "LED setup failed", e);
        }
    }

    public void writeLedRGB(int red, int green, int blue) {
        int[] colors = new int[] {Color.rgb(red, green, blue)};
        try {
            mApa102.write(colors);
        } catch (IOException e) {
            Log.e(TAG, "Error writing color to LED strip", e);
        }
    }

    public void writeLED(int ledColor) {
        int[] colors = new int[] {ledColor};
        try {
            mApa102.write(colors);
        } catch (IOException e) {
            Log.e(TAG, "Error writing color to LED strip", e);
        }
    }

    /**
     * Blocking I/O method to hold the LED on for a specific
     */
    public void holdLED(int color, int duration) {
        writeLED(color);
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Log.e(TAG, "LED could not sleep", e);
        }
    }

    /**
     * Blocking I/O method to blink the LED for a specific duration
     */
    public void flashLED(int onColor, int onDuration, int offColor, int offDuration) {
        holdLED(onColor, onDuration);
        holdLED(offColor, offDuration);
    }

    public void close() {

        Log.d(TAG, "Closing LED interface...");

        if (mApa102 != null) {
            try {
                mApa102.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing LED interface", e);
            } finally {
                mApa102 = null;
            }
        }
    }
}
