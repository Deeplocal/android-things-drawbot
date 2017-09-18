package com.deeplocal.drawbot;

import org.opencv.core.Point;

import java.util.ArrayList;

public class SampleLines {

    public static ArrayList<Line> getSquare(int length, boolean rightTurn) {

        ArrayList<Line> lines = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            if (rightTurn) {
                lines.add(new Line(new Point(0, 0), new Point(length - 1, 0)));
                lines.add(new Line(new Point(length, 0), new Point(length, length - 1)));
                lines.add(new Line(new Point(length, length), new Point(1, length)));
                lines.add(new Line(new Point(0, length), new Point(0, 1)));
            } else {
                lines.add(new Line(new Point(0, 0), new Point(0, length - 1)));
                lines.add(new Line(new Point(0, length), new Point(length - 1, length)));
                lines.add(new Line(new Point(length, length), new Point(length, 1)));
                lines.add(new Line(new Point(length, 0), new Point(1, 0)));
            }
        }

        return lines;
    }
}
