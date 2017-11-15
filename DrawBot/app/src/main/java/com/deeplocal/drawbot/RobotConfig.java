package com.deeplocal.drawbot;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashMap;

public class RobotConfig {

    // Configuration store
    private static final String STORE_NAME = "robot-config";

    // Default configuration values
    private static final int DEFAULT_SLOPSTEPS_RIGHTFWD = 12;
    private static final int DEFAULT_SLOPSTEPS_RIGHTBACK = 3;
    private static final int DEFAULT_SLOPSTEPS_LEFTFWD = 12;
    private static final int DEFAULT_SLOPSTEPS_LEFTBACK = 3;
    private static final int DEFAULT_SPACINGADJUST_RIGHT = 0;
    private static final int DEFAULT_SPACINGADJUST_LEFT = 0;
    private static final String DEFAULT_SERVOPOS = "115,105,80,65";

    // Configuration store keys
    public static final String KEY_SLOP_FWD_R = "slop-steps-fwd-right";
    public static final String KEY_SLOP_BACK_R = "slop-steps-back-right";
    public static final String KEY_SLOP_FWD_L = "slop-steps-fwd-left";
    public static final String KEY_SLOP_BACK_L = "slop-steps-back-left";
    public static final String KEY_SPACING_R = "spacing-adjust-right";    // tenths of mm
    public static final String KEY_SPACING_L = "spacing-adjust-left";     // tenths of mm
    public static final String KEY_SERVO_POS = "servo-position";

    private static RobotConfig sInstance;
    public static synchronized RobotConfig getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RobotConfig(context);
        }

        return sInstance;
    }

    private SharedPreferences mConfigStore;

    private RobotConfig(Context context) {
        // Init the configuration store
        mConfigStore = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Apply a new set of tuning parameters to the robot configuration.
     * @param tuningParams Set of calibration parameters
     */
    public void updateCalibration(HashMap<String, String> tuningParams) {
        mConfigStore.edit()
                .putInt(KEY_SLOP_FWD_R, getNumericParam(KEY_SLOP_FWD_R, tuningParams))
                .putInt(KEY_SLOP_FWD_L, getNumericParam(KEY_SLOP_FWD_L, tuningParams))
                .putInt(KEY_SLOP_BACK_R, getNumericParam(KEY_SLOP_BACK_R, tuningParams))
                .putInt(KEY_SLOP_BACK_L, getNumericParam(KEY_SLOP_BACK_L, tuningParams))
                .putInt(KEY_SPACING_R, getNumericParam(KEY_SPACING_R, tuningParams))
                .putInt(KEY_SPACING_L, getNumericParam(KEY_SPACING_L, tuningParams))
                .putString(KEY_SERVO_POS, tuningParams.get(KEY_SERVO_POS))
                .apply();
    }

    private int getNumericParam(String key, HashMap<String, String> params) {
        return Integer.valueOf(params.get(key));
    }

    /**
     * Return the forward right slop parameter
     */
    public int getSlopStepsRightFwd() {
        return mConfigStore.getInt(KEY_SLOP_FWD_R, DEFAULT_SLOPSTEPS_RIGHTFWD);
    }

    /**
     * Return the backward right slop parameter
     */
    public int getSlopStepsRightBack() {
        return mConfigStore.getInt(KEY_SLOP_BACK_R, DEFAULT_SLOPSTEPS_RIGHTBACK);
    }

    /**
     * Return the forward left slop parameter
     */
    public int getSlopStepsLeftFwd() {
        return mConfigStore.getInt(KEY_SLOP_FWD_L, DEFAULT_SLOPSTEPS_LEFTFWD);
    }

    /**
     * Return the backward left slop parameter
     */
    public int getSlopStepsLeftBack() {
        return mConfigStore.getInt(KEY_SLOP_BACK_L, DEFAULT_SLOPSTEPS_LEFTBACK);
    }

    /**
     * Return the right spacing adjust parameter
     */
    public int getSpacingAdjustRight() {
        return mConfigStore.getInt(KEY_SPACING_R, DEFAULT_SPACINGADJUST_RIGHT);
    }

    /**
     * Return the left spacing adjust parameter
     */
    public int getSpacingAdjustLeft() {
        return mConfigStore.getInt(KEY_SPACING_L, DEFAULT_SPACINGADJUST_LEFT);
    }

    /**
     * Return the initial servo position for the given motor
     * @param pos motor id
     */
    public int getServoPos(int pos) {
        String positionSet = mConfigStore.getString(KEY_SERVO_POS, DEFAULT_SERVOPOS);
        String[] servoPos = TextUtils.split(positionSet, ",");
        if ((pos < 0) || (pos >= servoPos.length)) {
            return 0;
        }
        return Integer.valueOf(servoPos[pos]);
    }
}