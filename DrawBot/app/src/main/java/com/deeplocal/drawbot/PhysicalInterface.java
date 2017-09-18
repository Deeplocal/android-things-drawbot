package com.deeplocal.drawbot;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.contrib.driver.apa102.Apa102;

import java.io.IOException;

import android.graphics.Color;

public class PhysicalInterface {

    private static final String TAG = "xyz";

    public Apa102 mApa102;
    public Gpio mPullupGpio;

    public PhysicalInterface() {

        try {
            mApa102 = new Apa102("SPI3.0", Apa102.Mode.BGR);
        } catch (IOException e) {
            Log.e(TAG, "LED setup failed", e);
        }

        // enable the pull-up source  for the  button (GPIO_175)
        try {
            PeripheralManagerService manager = new PeripheralManagerService();
            mPullupGpio = manager.openGpio("GPIO_175");
            mPullupGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            mPullupGpio.setActiveType(Gpio.ACTIVE_HIGH);
            mPullupGpio.setValue(true);
        } catch (IOException e) {
             Log.w(TAG, "Unable to access GPIO", e);
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

    public void close() {

        Log.d(TAG, "Closing LED interface...");

        if (mApa102 != null) {
            try {
                mApa102.close();
            } catch (IOException e) {
                Log.d(TAG, "Error closing LED interface");
                e.printStackTrace();
            }
        }

        if (mPullupGpio != null) {
            try {
                mPullupGpio.close();
                mPullupGpio = null;
            } catch (IOException e) {
                Log.d(TAG, "Unable to close pullup GPIO");
                e.printStackTrace();
            }
        }
    }
}
