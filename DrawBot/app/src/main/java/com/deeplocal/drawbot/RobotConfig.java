package com.deeplocal.drawbot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RobotConfig {

    // defaults
    public static final int DEFAULT_SLOPSTEPS_RIGHTFWD = 12;
    public static final int DEFAULT_SLOPSTEPS_RIGHTBACK = 3;
    public static final int DEFAULT_SLOPSTEPS_LEFTFWD = 12;
    public static final int DEFAULT_SLOPSTEPS_LEFTBACK = 3;
    public static final int DEFAULT_SPACINGADJUST_RIGHT = 0;
    public static final int DEFAULT_SPACINGADJUST_LEFT = 0;
    public static final int[] DEFAULT_SERVOPOS = { 115, 105, 80, 65 };

    // known UIDs
    private static final String SAMPLE_ROBOT_UID = "ba1d673987230e12";

    private String mUid;
    private static Map<String, Integer> mSlopStepsRightFwd;
    private static Map<String, Integer> mSlopStepsRightBack;
    private static Map<String, Integer> mSlopStepsLeftFwd;
    private static Map<String, Integer> mSlopStepsLeftBack;
    private static Map<String, Integer> mSpacingAdjustRight;   // tenths of mm
    private static Map<String, Integer> mSpacingAdjustLeft;    // tenths of  mm
    private static Map<String, ArrayList<Integer>> mServoPos;

    // statically init known UIDs with known values
    static {

        // init hashmaps
        mSlopStepsRightFwd = new HashMap<>();
        mSlopStepsRightBack = new HashMap<>();
        mSlopStepsLeftFwd = new HashMap<>();
        mSlopStepsLeftBack = new HashMap<>();
        mSpacingAdjustRight = new HashMap<>();
        mSpacingAdjustLeft = new HashMap<>();
        mServoPos = new HashMap<>();

        // put bench rig
        mSlopStepsRightFwd.put(SAMPLE_ROBOT_UID, 12);
        mSlopStepsRightBack.put(SAMPLE_ROBOT_UID, 3);
        mSlopStepsLeftFwd.put(SAMPLE_ROBOT_UID, 12);
        mSlopStepsLeftBack.put(SAMPLE_ROBOT_UID, 3);
        mSpacingAdjustRight.put(SAMPLE_ROBOT_UID, 0);
        mSpacingAdjustLeft.put(SAMPLE_ROBOT_UID, 0);
        ArrayList servoPosBR = new ArrayList();
        servoPosBR.add(115); // line thickness 0
        servoPosBR.add(105); // line thickness 1
        servoPosBR.add(80); // line thickness 2
        servoPosBR.add(65); // line thickness 3
        mServoPos.put(SAMPLE_ROBOT_UID, servoPosBR);
    }

    public RobotConfig(String uid) {

        // save uid
        mUid = uid;

        // apply defaults if uid is unknown
        if (mSlopStepsRightFwd.get(uid) == null) {

            mSlopStepsRightFwd.put(uid, DEFAULT_SLOPSTEPS_RIGHTFWD);
            mSlopStepsRightBack.put(uid, DEFAULT_SLOPSTEPS_RIGHTBACK);
            mSlopStepsLeftFwd.put(uid, DEFAULT_SLOPSTEPS_LEFTFWD);
            mSlopStepsLeftBack.put(uid, DEFAULT_SLOPSTEPS_LEFTBACK);
            mSpacingAdjustRight.put(uid, DEFAULT_SPACINGADJUST_RIGHT);
            mSpacingAdjustLeft.put(uid, DEFAULT_SPACINGADJUST_LEFT);
            ArrayList servoPos = new ArrayList();
            servoPos.add(DEFAULT_SERVOPOS[0]);
            servoPos.add(DEFAULT_SERVOPOS[1]);
            servoPos.add(DEFAULT_SERVOPOS[2]);
            servoPos.add(DEFAULT_SERVOPOS[3]);
            mServoPos.put(uid, servoPos);
        }
    }

    public int getSlopStepsRightFwd() {
        return mSlopStepsRightFwd.get(mUid);
    }

    public int getSlopStepsRightBack() {
        return mSlopStepsRightBack.get(mUid);
    }

    public int getSlopStepsLeftFwd() {
        return mSlopStepsLeftFwd.get(mUid);
    }

    public int getSlopStepsLeftBack() {
        return mSlopStepsLeftBack.get(mUid);
    }

    public int getSpacingAdjustRight() {
        return mSpacingAdjustRight.get(mUid);   
    }

    public int getSpacingAdjustLeft() {
        return mSpacingAdjustLeft.get(mUid);   
    }

    public int getServoPos(int pos) {
        ArrayList<Integer> servoPos = mServoPos.get(mUid);
        if ((servoPos == null) || (pos >= servoPos.size()))
            return 0;
        return servoPos.get(pos);
    }
}
