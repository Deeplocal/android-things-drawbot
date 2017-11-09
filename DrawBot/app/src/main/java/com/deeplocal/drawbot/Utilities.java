package com.deeplocal.drawbot;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class Utilities {

    private static final String TAG = "abc";

     public static Mat streamToMat(InputStream stream) throws IOException {
        byte[] data = new byte[1024];
        MatOfByte chunk = new MatOfByte();
        MatOfByte buf = new MatOfByte();
        int read;
        while ((read = stream.read(data)) > 0) {
            chunk.fromArray(data);
            Mat subchunk = chunk.submat(0, read, 0, 1);
            buf.push_back(subchunk);
        }
        return buf;
    }

    public static CascadeClassifier loadFaceClassifier(Context c) {

        CascadeClassifier classifier;

        try {

            InputStream is = c.getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = c.getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());

            cascadeDir.delete();

            if (!classifier.empty()) {
                Log.d(TAG, "Loaded face classifier");
                return classifier;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not load face classifier", e);
        }

        Log.d(TAG, "Failed to load face classifier");
        return null;
    }

    // scales equally from center
    public static Rect verticalScale(Rect original, double factor) {

        double height = original.br().y - original.tl().y;
        double newHeight = height * factor;
        double newTop = original.tl().y - ((newHeight - height) / 2);
        double width = original.br().x - original.tl().x;

        return new Rect((int) original.tl().x, (int) newTop, (int) width, (int) newHeight);
    }

    // scales equally from center
    public static Rect horizontalScale(Rect original, double factor) {

        double width = original.br().x - original.tl().x;
        double newWidth = width * factor;
        double newLeft = original.tl().x - ((newWidth - width) / 2);
        double height = original.br().y - original.tl().y;

        return new Rect((int) newLeft, (int) original.tl().y, (int) newWidth, (int) height);
    }

    // assumes points use java coordinate system (origin is top left and increases left and down)
    // returns angle between lines (p1 > p2) and (p2 > p3)
    // https://www.mathsisfun.com/algebra/trig-cosine-law.html
    // https://stackoverflow.com/questions/22668659/calculate-on-which-side-of-a-line-a-point-is
    public static double calcDegrees(Point p1, Point p2, Point p3) {

        double lenA = Math.sqrt(Math.pow((p1.x - p2.x), 2) + Math.pow((p1.y - p2.y), 2));
        double lenB = Math.sqrt(Math.pow((p2.x - p3.x), 2) + Math.pow((p2.y - p3.y), 2));
        double lenC = Math.sqrt(Math.pow((p1.x - p3.x), 2) + Math.pow((p1.y - p3.y), 2));

        double val = (Math.pow(lenA, 2) + Math.pow(lenB, 2) - Math.pow(lenC, 2)) / (2d * lenA * lenB);
        double rad = Math.acos(val);
//        double deg = rad * 180d / Math.PI;
        double deg = Math.toDegrees(rad);

        double turnDegrees = 180 - deg;

        double leftOrRight = (p2.x - p1.x) * (p3.y - p1.y) - (p3.x - p1.x) * (p2.y - p1.y);
        if (leftOrRight < 0) {
//            Log.d(TAG, "Point to left of line");
            turnDegrees *= -1;
        } else if (leftOrRight == 0) {
            Log.d(TAG, "Point on line");
            turnDegrees = 0;
        } else {
//            Log.d(TAG, "Point to right of line");
        }

        return turnDegrees;
    }

    public static void printLineList(String startString, ArrayList<Line> lines, String endString) {
        Log.d(TAG, startString);

        for (int i = 0; i < lines.size(); i++) {
            Log.d(TAG, String.format("Line %d: %s", i + 1, lines.get(i).toString()));
        }
        Log.d(TAG, endString);
    }

    public static String toJsonString(ArrayList<Line> lines) {
        String s = "{\"lines\":[";
        for (int i = 0; i < lines.size(); i++) {
            Point from = lines.get(i).getPoint1();
            Point to = lines.get(i).getPoint2();
            s += String.format("{\"from\":[%d,%d],\"to\":[%d,%d],\"weight\":%d}", (int) from.x, (int) from.y, (int) to.x, (int) to.y, lines.get(i).getThickness());
            if (i < (lines.size() - 1)) {
                s += ",";
            }
        }
        s += "]}";
        return s;
    }
}
