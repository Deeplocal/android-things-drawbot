package com.deeplocal.drawbot;

import android.content.Context;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;

public class LineAlgorithm {

    private static final String TAG = "abc";

    /*
     * Crop image to only show head.
     */
    public static Mat cropFace(Context context, Mat grayMat) {

        CascadeClassifier faceClassifier = Utilities.loadFaceClassifier(context);

        // detect faces and log
        MatOfRect faces = new MatOfRect();
        faceClassifier.detectMultiScale(grayMat, faces);
        Rect[] facesArray = faces.toArray();

        if (facesArray.length == 0) {
            Log.d(TAG, "No faces");
            return null;
        }

        for (int i = 0; i < facesArray.length; i++) {
            Log.d(TAG, "Face from " + facesArray[i].tl() + " to " + facesArray[i].br());
        }

        // get scaled rect for crop
        Rect cropRect = new Rect(facesArray[0].tl(), facesArray[0].br());
        Rect tempRect = Utilities.verticalScale(cropRect, 1.4);
        Rect scaledRect = Utilities.horizontalScale(tempRect, 1);

        // check if scaled rect is out of image bounds
        if (scaledRect.tl().x < 0) {
            Point p = new Point(0, scaledRect.tl().y);
            scaledRect = new Rect(p, scaledRect.br());
        }
        if (scaledRect.br().x > grayMat.cols()) {
            Point p = new Point(grayMat.cols(), scaledRect.br().y);
            scaledRect = new Rect(scaledRect.tl(), p);
        }
        if (scaledRect.tl().y < 0) {
            Point p = new Point(scaledRect.tl().x, 0);
            scaledRect = new Rect(p, scaledRect.br());
        }
        if (scaledRect.br().y > grayMat.rows()) {
            Point p = new Point(scaledRect.br().x, grayMat.rows());
            scaledRect = new Rect(scaledRect.tl(), p);
        }

        Log.d(TAG, String.format("grayImage: rows = %d, cols = %d", grayMat.rows(), grayMat.cols()));
        Log.d(TAG, String.format("scaledRect: tl = (%f, %f), br = (%f, %f)", scaledRect.tl().x, scaledRect.tl().y, scaledRect.br().x, scaledRect.br().y));

        // crop grayscale image
        Mat croppedMat = new Mat(grayMat, scaledRect);

        // equalize histogram of original image
        Mat eqFaceMat = new Mat(croppedMat.size(), croppedMat.type());
        Imgproc.equalizeHist(croppedMat, eqFaceMat);

        // scale image
        double numDesiredRows = 40;
        double scaleFactor = numDesiredRows / croppedMat.rows();
        Log.d(TAG, String.format("scaleFactor = %f", scaleFactor));
        int newRows = (int) Math.floor(croppedMat.rows() * scaleFactor);
        int newCols = (int) Math.floor(croppedMat.cols() * scaleFactor);

        Mat scaleMat = new Mat(newRows, newCols, croppedMat.type());
        Imgproc.resize(eqFaceMat, scaleMat, new Size(newCols, newRows));
        Log.d(TAG, String.format("Got face image size = %s", scaleMat.size().toString()));

        return scaleMat;
    }


    public static int getMedian(Mat mat) {

        ArrayList<Mat> listOfMat = new ArrayList<>();
        listOfMat.add(mat);
        MatOfInt channels = new MatOfInt(0);
        Mat mask = new Mat();
        Mat hist = new Mat(256, 1, CvType.CV_8UC1);
        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat ranges = new MatOfFloat(0, 256);

        Imgproc.calcHist(listOfMat, channels, mask, hist, histSize, ranges);

        double t = mat.rows() * mat.cols() / 2;
        double total = 0;
        int med = -1;
        for (int row = 0; row < hist.rows(); row++) {
            double val = hist.get(row, 0)[0];
            if ((total <= t) && (total + val >= t)) {
                med = row;
                break;
            }
            total += val;
        }

//        Log.d(TAG, String.format("getMedian() = %d", med));

        return med;
    }

    public static ArrayList<Line> getCopicLines(Mat faceMat) {

//        Log.d(TAG, String.format("fyi faceMat has %d rows and %d columns", faceMat.rows(), faceMat.cols()));

        double maxVal = Float.NEGATIVE_INFINITY;
        double minVal = Float.POSITIVE_INFINITY;

        ArrayList<Line> lineList = new ArrayList<>();

        // pre-process to get max & min
        for (int row = 0; row < faceMat.rows(); row++) {
            for (int col = 0; col < faceMat.cols(); col++) {

                // get gray value [0..255]
                double[] pixel = faceMat.get(row, col);
                double val = pixel[0];

                // update image max & min
                minVal = Math.min(minVal, val);
                maxVal = Math.max(maxVal, val);
            }
        }

        /*
         *   |       |       |       |       |
         *   | Bin 0 | Bin 1 | Bin 2 | Bin 3 |
         *   |_______|_______|_______|_______|
         *   A       B       C       D       E
         */

        // calculate bin divider positions
        double div_A = minVal;
        double div_E = maxVal;
        double div_C = (div_A + div_E) / 2;
        double div_B = (div_A + div_C) / 2;
        double div_D = (div_C + div_E) / 2;


        // for each pixel
        for (int row = 0; row < faceMat.rows(); row++) {

            int startCol;
            int endCol;
            int incrementCol;

            // move left to right for even numbered rows
            if (row % 2 == 0) {
                startCol = 0;
                endCol = faceMat.cols();
                incrementCol = 1;
            }
            else {  // move right to left for odd numbered rows
                startCol = faceMat.cols() - 1;
                endCol = -1;
                incrementCol = -1;
            }

            // will be used to generate the end cap line
            int lastEndX = 0;

            // this loop ~should~ run forward for even rows, and backwards for odd rows
            for (int col = startCol; col != endCol; col += incrementCol) {

                // but just to make sure
//                Log.d(TAG, String.format("fyi getCopicLines() is procressing row %d, col %d", row, col));

                // get gray value [0..255]
                double[] pixel = faceMat.get(row, col);
                double val = pixel[0];

                // calculate which bin
                int bin = -1;
                if      (div_A <= val && val < div_B) bin = 3;
                else if (div_B <= val && val < div_C) bin = 2;
                else if (div_C <= val && val < div_D) bin = 1;
                else if (div_D <= val && val <= div_E) bin = 0;

                // check if not in a bin
                if (bin < 0 || bin > 3) {
                    Log.d(TAG, String.format("fyi bins are leaky"));
                    bin = 0;
                }

                // make a line
                int lineWeight = bin;

                double startX;
                double endX;
                if (incrementCol < 0) { // moving Right to Left (backwards)
                    startX = col + 1;
                    endX = col;
                } else  {               // moving Left to Right (forwards)
                    startX = col;
                    endX = col+1;
                }

                double startY = row;
                double endY = row;

                Point startPoint = new Point(startX, startY);
                Point endPoint = new Point(endX, endY);
                lineList.add(new Line(startPoint, endPoint, lineWeight));

//                Log.d(TAG, String.format("fyi added a line from (%f, %f) to (%f, %f) w/ weight %d", startX, startY, endX, endY, lineWeight));

                lastEndX = (int) endX;
            }

            // end-cap
            if (row < faceMat.rows()-1) {
                double startX = lastEndX;
                double endX = lastEndX;
                double startY = row;
                double endY = row+1;
                Point startPoint = new Point(startX, startY);
                Point endPoint = new Point(endX, endY);
                int lineWeight = 0;
                lineList.add(new Line(startPoint, endPoint, lineWeight));
//                Log.d(TAG, String.format("fyi added a line from (%f, %f) to (%f, %f) w/ weight %d (end-cap)", startX, startY, endX, endY, lineWeight));
            }
        }

        return lineList;
    }
}
