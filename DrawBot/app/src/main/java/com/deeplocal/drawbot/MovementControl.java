package com.deeplocal.drawbot;

import android.util.Log;

import com.google.android.things.contrib.driver.pwmservo.Servo;
import com.polidea.androidthings.driver.steppermotor.Direction;
import com.polidea.androidthings.driver.steppermotor.driver.StepDuration;
import com.polidea.androidthings.driver.uln2003.driver.ULN2003;
import com.polidea.androidthings.driver.uln2003.driver.ULN2003Resolution;

import java.io.IOException;

public class MovementControl {

    private static final String TAG = "drawbot";

    // Tunable Parameters - Distance and Turning
    private static final double STEPS_PER_MM  = 2.721485;  // straight-line conversion
    private static final double STEPS_PER_DEG = 2.923;     // point-turn conversion

    private static final String[] leftMotorPins = { "GPIO_10", "GPIO_35", "GPIO_33", "GPIO_128" };
    private static final String[] rightMotorPins = { "GPIO_32", "GPIO_34", "GPIO_37", "GPIO_39" };
    private static final String penServoPin = "PWM2";

    private ULN2003 mLeftStepper;
    private ULN2003 mRightStepper;
    private Servo mPenServo;

    private MainActivity mMainActivity;
    private RobotConfig mRobotConfig;

    public MovementControl(MainActivity mainActivity, RobotConfig robotConfig) {

        mMainActivity = mainActivity;
        mRobotConfig = robotConfig;

        mLeftStepper = new ULN2003(leftMotorPins[0], leftMotorPins[1], leftMotorPins[2], leftMotorPins[3]);
        mLeftStepper.open();
        mRightStepper = new ULN2003(rightMotorPins[0], rightMotorPins[1], rightMotorPins[2], rightMotorPins[3]);
        mRightStepper.open();

        try {
            mPenServo = new Servo(penServoPin);
            mPenServo.setPulseDurationRange(1, 2); // according to your servo's specifications
            mPenServo.setAngleRange(0, 180);       // according to your servo's specifications
            mPenServo.setEnabled(true);
            setMarkerPressure(0);
        } catch (IOException e) {
            Log.e(TAG, "Could not init pen servo", e);
        }
    }

    // distance in mm
    public void moveStraight(double distance, boolean isDrawing) {

        int minSpeed = 4000000;
        int maxSpeed = 500000;
        int rampRate = 20000;

        int steps = (int) (distance * STEPS_PER_MM);

//        constantMotion(steps, isDrawing, Direction.COUNTERCLOCKWISE, Direction.CLOCKWISE);
        smoothMotion(steps, ULN2003Resolution.HALF, Direction.COUNTERCLOCKWISE, Direction.CLOCKWISE, minSpeed, maxSpeed, rampRate);

//        Log.d(TAG, "Done moving straight");

        if (isDrawing) {
            mMainActivity.pivot();
        }
    }

    public void turn(double turnDegrees, boolean isDrawing) {

        int minSpeed = 4200000;
        int maxSpeed = 400000;
        int rampRate = 30000;

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Log.e(TAG, "Turn could not sleep", e);
        }

        Direction leftDirection, rightDirection;

        int steps = (int) Math.abs(turnDegrees * STEPS_PER_DEG * 2);
        Log.d(TAG, String.format("Num steps = %d for %f degrees", steps, turnDegrees));

        StepDuration stepDuration = new StepDuration(0, minSpeed);

        // left turn
        if (turnDegrees < 0) {
            
            // slop steps backwards
            mLeftStepper.setDirection(Direction.CLOCKWISE);
            for (int i = 0; i < mRobotConfig.getSlopStepsLeftBack(); i++) {
                mLeftStepper.performStep(stepDuration);
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, "Turn could not sleep", e);
            }

            // pivot turn
            leftDirection = Direction.CLOCKWISE;
            rightDirection = Direction.CLOCKWISE;
            smoothMotion(steps, ULN2003Resolution.FULL, leftDirection, rightDirection, minSpeed, maxSpeed, rampRate);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, "Turn could not sleep", e);
            }

            // slop steps forwards
            mLeftStepper.setDirection(Direction.COUNTERCLOCKWISE);
            for (int i = 0; i < mRobotConfig.getSlopStepsLeftFwd(); i++) {
                mLeftStepper.performStep(stepDuration);
            }
        }

        // right turn
        else {

            // slop steps backwards
            mRightStepper.setDirection(Direction.COUNTERCLOCKWISE);
            for (int i = 0; i < mRobotConfig.getSlopStepsRightBack(); i++) {
                mRightStepper.performStep(stepDuration);
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, "Turn could not sleep", e);
            }

            // pivot turn
            leftDirection = Direction.COUNTERCLOCKWISE;
            rightDirection = Direction.COUNTERCLOCKWISE;
            smoothMotion(steps, ULN2003Resolution.FULL, leftDirection, rightDirection, minSpeed, maxSpeed, rampRate);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e(TAG, "Turn could not sleep", e);
            }

            // slop steps forwards
            mRightStepper.setDirection(Direction.CLOCKWISE);
            for (int i = 0; i < mRobotConfig.getSlopStepsRightFwd(); i++) {
                mRightStepper.performStep(stepDuration);
            } 
        }

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Log.e(TAG, "Turn could not sleep", e);
        }

//        Log.d(TAG, "Done turning");

        if (isDrawing) {
            mMainActivity.drawNextLine();
        }
    }

    public void constantMotion(int numSteps, ULN2003Resolution res, Direction leftDirection, Direction rightDirection, int stepDelay) {

        mLeftStepper.setDirection(leftDirection);
        mRightStepper.setDirection(rightDirection);
        mLeftStepper.setResolution(res);
        mRightStepper.setResolution(res);

        int stepCount = 0; // total steps moved
        StepDuration stepDuration = new StepDuration(0, stepDelay);
        while (stepCount < numSteps) {
            mLeftStepper.performStep(stepDuration);
            mRightStepper.performStep(stepDuration);
            stepCount++;
        }
    }

    public void smoothMotion(int numSteps, ULN2003Resolution res, Direction leftDirection, Direction rightDirection, int stepDelaySlowest, int stepDelayFastest, int rampRate) {

        mLeftStepper.setDirection(leftDirection);
        mRightStepper.setDirection(rightDirection);
        mLeftStepper.setResolution(res);
        mRightStepper.setResolution(res);

        int stepCount = 0;      // total steps moved
        int stepDelay = stepDelaySlowest; 
        StepDuration stepDuration = new StepDuration(0, stepDelay);

        /******  RAMP-UP / ACCELERATION  ******/
        while(stepDelay > stepDelayFastest) {

            stepDuration = new StepDuration(0, stepDelay);
            
            // move a single step
            mLeftStepper.performStep(stepDuration);
            mRightStepper.performStep(stepDuration);
            stepCount++;
//            Log.d(TAG, "stepCount =  " +  stepCount + " stepDelay = " + stepDelay);
            if (stepCount > numSteps/2) break;

            // bump up the speed a bit
            stepDelay -= rampRate;
        }

        // when to begin decceleration
        int startDeccel = numSteps - stepCount;

//        Log.d(TAG, "Constant rate...");

        /******  CONSTANT RATE  ******/
        stepDuration = new StepDuration(0, stepDelay);
        while (stepCount < startDeccel) {
            mLeftStepper.performStep(stepDuration);
            mRightStepper.performStep(stepDuration);
            stepCount++;
//            Log.d(TAG, "stepCount =  " +  stepCount + " stepDelay = " + stepDelay);
        }

//        Log.d(TAG, "Deccelerating...");

        /******  RAMP-DOWN / DECCELERATION  ******/
        while (stepDelay < stepDelaySlowest) {
            stepDuration = new StepDuration(0, stepDelay);
            mLeftStepper.performStep(stepDuration);
            mRightStepper.performStep(stepDuration);
            stepCount++;
//            Log.d(TAG, "stepCount =  " +  stepCount + " stepDelay = " + stepDelay);
            if (stepCount >= numSteps) break;

            // slow down a bit
            stepDelay += rampRate;
        }

        // finish any last steps at slowest speed
        stepDuration = new StepDuration(0, stepDelay);
        while (stepCount < numSteps) {
            mLeftStepper.performStep(stepDuration);
            mRightStepper.performStep(stepDuration);
            stepCount++;
//            Log.d(TAG, "stepCount =  " +  stepCount + " stepDelay = " + stepDelay);
        }
    }

    public void setMarkerPressure(int level) {
        Log.d(TAG, String.format("setMarkerPressure(%d); (pos=%d)", level, mRobotConfig.getServoPos(level)));
        movePen(mRobotConfig.getServoPos(level));
    }

    public void movePen(int angle) {
        try {
            mPenServo.setAngle(angle);
        } catch (IOException e) {
            Log.e(TAG, "Could not set angle on pen servo", e);
        }
    }

    public void close() {

        if ((mLeftStepper != null) && (mRightStepper != null)) {
            mLeftStepper.close();
            mRightStepper.close();
        }

        if (mPenServo != null) {
            try {
                mPenServo.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close pen servo", e);
            } finally {
              mPenServo = null;
            }
        }
    }
}
