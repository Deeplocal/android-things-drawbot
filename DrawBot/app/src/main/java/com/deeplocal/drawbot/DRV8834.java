package com.deeplocal.drawbot;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class DRV8834 {

    public enum Direction {
        CLOCKWISE,
        COUNTERCLOCKWISE
    }

    private String mStepPin;
    private String mDirectionPin;
    private String mSleepPin;

    private Gpio mStepGpio;
    private Gpio mDirectionGpio;
    private Gpio mSleepGpio;

    public DRV8834(String stepPin, String directionPin, String sleepPin) {

        mStepPin = stepPin;
        mDirectionPin = directionPin;
        mSleepPin = sleepPin;
    }

    public void open() throws IOException {

        PeripheralManagerService manager = new PeripheralManagerService();

        mStepGpio = manager.openGpio(mStepPin);
        mStepGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

        mDirectionGpio = manager.openGpio(mDirectionPin);
        mDirectionGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

        mSleepGpio = manager.openGpio(mSleepPin);
        mSleepGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
    }

    public void close() throws IOException {

        if (mStepGpio != null) {
            mStepGpio.close();
            mStepGpio = null;
        }

        if (mDirectionGpio != null) {
            mDirectionGpio.close();
            mDirectionGpio = null;
        }

        if (mSleepGpio != null) {
            mSleepGpio.setValue(true); // release stepper
            mSleepGpio.close();
            mSleepGpio = null;
        }
    }

    public void setDirection(Direction direction) throws IOException {

        if (mDirectionGpio == null) {
            return;
        }

        mDirectionGpio.setValue(direction == Direction.COUNTERCLOCKWISE);
    }

    public void setSleep(boolean sleep) throws IOException {

        if (mSleepGpio == null) {
            return;
        }

        mSleepGpio.setValue(!sleep); // pin is active low
    }

    public void setStep(boolean value) throws IOException {

        if (mStepGpio == null) {
            return;
        }

        mStepGpio.setValue(value);
    }
}
