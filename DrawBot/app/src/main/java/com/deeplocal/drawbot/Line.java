package com.deeplocal.drawbot;

import android.util.Log;

import org.opencv.core.Point;

public class Line {

    private Point mPoint1, mPoint2;
    private double mSlope = Double.MIN_VALUE;
    private int mThickness = 2;

    public Line(Point p1, Point p2) {
        mPoint1 = p1;
        mPoint2 = p2;
    }

    public Line(Point p1, Point p2, int thickness) {
        mPoint1 = p1;
        mPoint2 = p2;
        mThickness = thickness;
    }

    public Point getPoint1() {
        return mPoint1;
    }

    public Point getPoint2() {
        return mPoint2;
    }

    public int getThickness() {
        return mThickness;
    }

    /*
     * 0 <= angle <= 90 moving clockwise from horizontal right
     * -90 < angle < 0 moving counter-clockwise from horizontal right
     * (in degrees)
     */
    public double getAngle() {

        if (mSlope != Double.MIN_VALUE) {
            return mSlope;
        }

        double theta = Math.toDegrees(Math.atan2(mPoint1.y - mPoint2.y, mPoint1.x - mPoint2.x));

        if (theta >= 180) {
            Log.d("abc", String.format("Angle = %f", theta));
            theta -= 180;
        }

        mSlope = theta;
        return theta;
    }

    public Point getCenter() {

        double centerX = (mPoint1.x + mPoint2.x) / 2;
        double centerY = (mPoint1.y + mPoint2.y) / 2;

        return new Point(centerX, centerY);
    }

    public double getLength() {
        return Math.sqrt(Math.pow(mPoint1.x - mPoint2.x, 2) + Math.pow(mPoint1.y - mPoint2.y, 2));
    }

    @Override
    public String toString() {
        return String.format("%s to %s", mPoint1.toString(), mPoint2.toString());
    }
}
