package com.deeplocal.drawbot;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Size;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity implements ImageReader.OnImageAvailableListener {

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d("abc", "OpenCV failed to load");
        }
    }

    private enum DrawMode {
        NOT_SET,
        NORMAL_KIOSK_0,
        NORMAL_KIOSK_1,
        NORMAL_KIOSK_2,
        RIGHT_TURN_TEST,
        LEFT_TURN_TEST,
        PRESSURE_TEST
    }

    private enum State {
        SETUP_NO_PRESSES,
        SETUP_MORE_PRESSES,
        NO_PHOTO,
        PROCESSING_PHOTO,
        WAITING_TO_DRAW,
        DRAWING,
        RESETTING
    }

    public static final String TAG = "drawbot";

    public static final String WIFI_SSID = "android-db";
    public static final String WIFI_KEY = "elatedvalley510";
    public static final String[] SERVER_IPS = { "192.168.1.10:8008", "192.168.1.11:8008", "192.168.1.12:8008" };

    public static int mKioskNum = 0;

    private static final String BUTTON_PIN_NAME = "GPIO_174"; // GPIO port wired to the button
    private static final int DEBOUNCE_MILLIS = 333;
    private static final boolean UPDATE_SCREEN = false;

    private Gpio mButtonGpio;
    private boolean mButtonDebouncing;

    private Handler mMainHandler;
    private Handler mBackgroundHandler;
    private CameraHandler mCameraHandler;
    private ImagePreprocessor mImagePreprocessor;

    private TextView mInfoTextView;
    private SeekBar mAlphaSb, mBetaSb;
    private ImageView mImageView1;

    private MovementControl mMovementControl;
    private PhysicalInterface mPhysicalInterface;
    private RobotConfig mRobotConfig;

    private RequestQueue mRequestQueue;
    private boolean mHasSentPing = false;
    private boolean mHasFoundServer = false;

    private DrawMode mDrawMode = DrawMode.NOT_SET;
    private State mState = State.SETUP_NO_PRESSES;
    private boolean mSetupComplete = false;

    private static final double DRAW_SCALE = 4;

    private ArrayList<Line> mDrawingLines;

    private double mAlpha = 1;
    private int mBeta = 0;

    private static final int MAX_MISS_FACES = 3;
    private int mNumNoFaces = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (UPDATE_SCREEN) {

            mInfoTextView = (TextView) findViewById(R.id.info_tv);
            mImageView1 = (ImageView) findViewById(R.id.main_imageview_1);
            mAlphaSb = ((SeekBar) findViewById(R.id.alpha_sb));
            mBetaSb = ((SeekBar) findViewById(R.id.beta_sb));

            mAlphaSb.setProgress((int) (mAlpha * 10));
            mAlphaSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    mAlpha = progress / 10d;
                    processPhoto(false);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            mBetaSb.setProgress(mBeta);
            mBetaSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    mBeta = progress;
                    processPhoto(false);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // initialize gpio input and set callback for falling edge
        try {

            PeripheralManagerService manager = new PeripheralManagerService();

            // button uses this as pullup
            Gpio buttonPullupGpio = manager.openGpio("GPIO_175");
            buttonPullupGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);

            mButtonGpio = manager.openGpio(BUTTON_PIN_NAME);
            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
            mButtonGpio.setActiveType(Gpio.ACTIVE_LOW);
            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
            mButtonGpio.registerGpioCallback(mGpioCallback);

        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pin", e);
        }

        // Process blocking I/O in the background
        HandlerThread backgroundThread = new HandlerThread("BackgroundThread");
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());

        // Process messages on the main thread
        mMainHandler = new Handler();

        // Initialize camera
        mImagePreprocessor = new ImagePreprocessor(MainActivity.this);
        mCameraHandler = CameraHandler.getInstance();
        try {
            mCameraHandler.initializeCamera(MainActivity.this, mBackgroundHandler, MainActivity.this);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not initialize camera. (Not connected?)", e);
        }

        mRobotConfig = RobotConfig.getInstance(this);
        mMovementControl = new MovementControl(mRobotConfig);

        mPhysicalInterface = new PhysicalInterface();
        mPhysicalInterface.writeLED(Color.WHITE);

        mDrawingLines = new ArrayList<>();

        mRequestQueue = Volley.newRequestQueue(this);
        connectToWifi();

        infoText("Ready");
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();

            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {

                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if ((info != null) && info.isConnected()) {

                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                    Log.d(TAG, String.format("Wifi connected to %s", wifiInfo.getSSID()));

                    if (wifiInfo.getSSID().equals(String.format("\"%s\"", WIFI_SSID))) {
                        Log.d(TAG, "Pinging network...");
                                networkPing();
                    }
                } else {
                    Log.d(TAG, "Wifi disconnected");
                }
            }
        }
    };

    private void connectToWifi() {

        String wifiConfigSsid = String.format("\"%s\"", WIFI_SSID);

        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) {

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            // return if already connected to this network
            if ((wifiInfo.getNetworkId() != -1) && (wifiInfo.getSSID().equals(wifiConfigSsid))) {
                Log.d(TAG, "Already connected to Wifi");
                networkPing();
                return;
            }
        }

        Log.d(TAG, "Attempting to connect to Wifi");

        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", WIFI_SSID);
        wifiConfig.preSharedKey = String.format("\"%s\"", WIFI_KEY);
        int netId = wifiManager.addNetwork(wifiConfig);

        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
    }

    private void networkPing() {


        if (mHasSentPing || !mSetupComplete)
            return;

        final String url = "http://" + SERVER_IPS[mKioskNum] + "/ping";

        Log.d(TAG, "PING !  " + url);

        StringRequest getRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "Response = " + response);
                        if (response.equals("pong")) {
                            mHasFoundServer = true;
                            Server.get("/reset", mRequestQueue);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "Request error = " + error.toString());
                    }
                }
        );

        mRequestQueue.add(getRequest);
        mHasSentPing = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mBroadcastReceiver);
    }

    private GpioCallback mGpioCallback = new GpioCallback() {

        @Override
        public boolean onGpioEdge(Gpio gpio) {

            Log.d(TAG, "Falling edge");

            if (mButtonDebouncing) {
                Log.d(TAG, "Button debouncing");
                return true;
            }
            mButtonDebouncing = true;

            infoText("Button press");

            switch (mState) {

                case SETUP_NO_PRESSES:

                    // update state
                    mState = State.SETUP_MORE_PRESSES;

                    // on the first button press, start a 5-second countdown
                    Log.d(TAG, "Setup: 5 seconds to enter kiosk number");

                    // runs at end of countdown
                    mBackgroundHandler.postDelayed(new Runnable() {

                        @Override
                        public void run() {

                            int upperBound = 1;
                            if (mDrawMode == DrawMode.NORMAL_KIOSK_1)
                                upperBound = 2;
                            else if (mDrawMode == DrawMode.NORMAL_KIOSK_2)
                                upperBound = 3;
                            else if (mDrawMode == DrawMode.RIGHT_TURN_TEST)
                                upperBound = 4;
                            else if (mDrawMode == DrawMode.LEFT_TURN_TEST)
                                upperBound = 5;
                            else if (mDrawMode == DrawMode.PRESSURE_TEST)
                                upperBound = 6;

                            // flash kioskNumber # times
                            mPhysicalInterface.holdLED(Color.BLACK, 1000);
                            for (int i = 0; i < upperBound; i++) {
                                mPhysicalInterface.flashLED(Color.BLUE, 200, Color.BLACK, 500);
                            }

                            mSetupComplete = true;

                            // end setup, ready for normal use
                            if ((mDrawMode == DrawMode.NORMAL_KIOSK_0) ||
                                    (mDrawMode == DrawMode.NORMAL_KIOSK_1) ||
                                    (mDrawMode == DrawMode.NORMAL_KIOSK_2)) {
                                networkPing();
                                mKioskNum = upperBound - 1;
                                mState = State.NO_PHOTO;
                                mPhysicalInterface.writeLED(Color.RED);
                            } else if (mDrawMode == DrawMode.RIGHT_TURN_TEST) {
                                squareTest(true);
                            } else if (mDrawMode == DrawMode.LEFT_TURN_TEST) {
                                squareTest(false);
                            } else if (mDrawMode == DrawMode.PRESSURE_TEST) {
                                pressureTest();
                            }
                        }
                    }, 5000);

                    // intentional no break here to fall through to count the press

                case SETUP_MORE_PRESSES:

                    if (mDrawMode == DrawMode.NORMAL_KIOSK_0)
                        mDrawMode = DrawMode.NORMAL_KIOSK_1;
                    else if (mDrawMode == DrawMode.NORMAL_KIOSK_1)
                        mDrawMode = DrawMode.NORMAL_KIOSK_2;
                    else if (mDrawMode == DrawMode.NORMAL_KIOSK_2)
                        mDrawMode = DrawMode.RIGHT_TURN_TEST;
                    else if (mDrawMode == DrawMode.RIGHT_TURN_TEST)
                        mDrawMode = DrawMode.LEFT_TURN_TEST;
                    else if (mDrawMode == DrawMode.LEFT_TURN_TEST)
                        mDrawMode = DrawMode.PRESSURE_TEST;
                    else if ((mDrawMode == DrawMode.PRESSURE_TEST) || (mDrawMode == DrawMode.NOT_SET))
                        mDrawMode = DrawMode.NORMAL_KIOSK_0;

                    infoText(String.format("Setup: draw mode = %s", mDrawMode));

                    // flash LED blue for feedback
                    mBackgroundHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            mPhysicalInterface.flashLED(Color.BLUE, 500, Color.WHITE, 500);
                        }
                    });

                    break;

                case NO_PHOTO:

                    mState = State.PROCESSING_PHOTO;

                    infoText("Taking photo");

                    // reset global alpha and beta
                    mAlpha = 1;
                    mBeta = 0;

                    // Camera processing is already set up for background
                    mPhysicalInterface.writeLED(Color.YELLOW);
                    mCameraHandler.takePicture();

                    break;

                case WAITING_TO_DRAW:

                    if (mDrawingLines.isEmpty()) {
                        Log.d(TAG, "No drawing lines");
                        stopDrawing();
                        break;
                    }

                    startDrawing();
                    break;

                case PROCESSING_PHOTO:
                case DRAWING:
                    // If button pressed while drawing, cancel
                    Log.d(TAG, "Canceling draw operation");
                    stopDrawing();
                    break;
                case RESETTING:
                    Log.d(TAG, String.format("Intentionally skipping mState = %s button press", mState));
                    break;

                default:
                    Log.d(TAG, String.format("No button action, state = %s", mState));
                    break;
                }

            mMainHandler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    mButtonDebouncing = false;
                    Log.d(TAG, "Button done debouncing");
                }
            }, DEBOUNCE_MILLIS);

            return super.onGpioEdge(gpio);
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.d(TAG, String.format("Gpio error = %d", error));
        }
    };

    @Override
    public void onImageAvailable(ImageReader reader) {

        Log.d(TAG, "onImageAvailable()");

        try (Image image = reader.acquireNextImage()) {
            mImagePreprocessor.preprocessImage(image);
        }

        processPhoto(true);
    }

    private void processPhoto(boolean autoLevels) {

        // sample: https://github.com/opencv/opencv/blob/master/samples/android/face-detection/src/org/opencv/samples/facedetect/FdActivity.java
        // sample: http://docs.opencv.org/trunk/d7/d8b/tutorial_py_face_detection.html

        Uri cameraImageUri = Uri.parse("file://" + MainActivity.this.getCacheDir().getAbsolutePath() + "/preview.png");
        Log.d(TAG, "Image URI = " + cameraImageUri);

        InputStream cameraImageStream;
        Mat cameraImageBuffer;

        // create a matrix from the photo
        try {
            cameraImageStream = getContentResolver().openInputStream(cameraImageUri);
            cameraImageBuffer = Utilities.streamToMat(cameraImageStream);
        } catch (IOException e) {
            Log.e(TAG, "Could not get camera image", e);
            return;
        }

        // decode the image as grayscale
        Mat grayImage = Highgui.imdecode(cameraImageBuffer, Highgui.CV_LOAD_IMAGE_GRAYSCALE);
        Log.d(TAG, String.format("Original photo hxw = %dx%d", grayImage.height(), grayImage.width())); // this image is 224x224

        // rotate image 90 counter-clockwise
        Point center = new Point(grayImage.cols() / 2, grayImage.rows() / 2);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, 90, 1);
        Mat rotatedGrayImage = new Mat(grayImage.size(), grayImage.type());
        Imgproc.warpAffine(grayImage, rotatedGrayImage, rotMat, grayImage.size());

        // apply initial alpha and beta and measure median
        Mat levelsMat = new Mat(rotatedGrayImage.size(), rotatedGrayImage.type());
        rotatedGrayImage.convertTo(levelsMat, -1, mAlpha, mBeta);

        // auto-brightness / contrast algorithm
        // apply levels shift until median is between lower and upper target
        int lowerTarget = 55, upperTarget = 200;
        int numIters = 0, bestMedian = LineAlgorithm.getMedian(levelsMat), bestBeta = mBeta;
        double bestAlpha = mAlpha;
        while (autoLevels && ((bestMedian < lowerTarget) || (bestMedian > upperTarget))) {

//            Log.d(TAG, String.format("trying alpha = %f, beta = %d", mAlpha, mBeta));
            rotatedGrayImage.convertTo(levelsMat, -1, mAlpha, mBeta); // -1 means same as input type

            int median = LineAlgorithm.getMedian(levelsMat);
            if (median < lowerTarget) {

                if (median > bestMedian) {
                    bestMedian = median;
                    bestAlpha = mAlpha;
                    bestBeta = mBeta;
                }

                if (mBeta < 25) {
                    mBeta += 5;
                } else {
                    mAlpha += 0.2;
                    mBeta = 0;
                }
            } else if (median > upperTarget) {

                if (median < bestMedian) {
                    bestMedian = median;
                    bestAlpha = mAlpha;
                    bestBeta = mBeta;
                }

                if (mBeta > 0) {
                    mBeta -= 5;
                } else {
                    mAlpha -= 0.2;
                    mBeta = 25;
                }
            } else {
                Log.d(TAG, "Reached target median");
                break;
            }

            // check if reached max number of iterations
            numIters++;
            if (numIters > 25) {
                Log.d(TAG, String.format("Using best alpha = %f, beta = %d", bestAlpha, bestBeta));
                mAlpha = bestAlpha;
                mBeta = bestBeta;
                break;
            }
        }

        // equalize histogram of original image
        Mat eqMat = new Mat(levelsMat.size(), levelsMat.type());
        Imgproc.equalizeHist(levelsMat, eqMat);

        if (UPDATE_SCREEN) {

            // update ui with alpha and beta
            mAlphaSb.setProgress((int) mAlpha * 10);
            mBetaSb.setProgress(mBeta);

            // create bitmap from original image
            final Bitmap originalBmp = Bitmap.createBitmap(eqMat.width(), eqMat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(eqMat, originalBmp);

            // update textview and imageview
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mInfoTextView.setText(String.format("alpha = %f, beta = %d", mAlpha, mBeta));
                    mImageView1.setImageBitmap(originalBmp);
                }
            });
        }

        // crop face from original image
        Mat faceMat = LineAlgorithm.cropFace(MainActivity.this, eqMat); // faceMat is type CvType.CV_8UC1

        // if it didn't find a face
        if (faceMat == null) {

            // increment number of no faces
            mNumNoFaces += 1;

            if (mHasFoundServer) {
                Server.networkLog("No faces", mRequestQueue);
            }

            infoText("No faces");

            // if we haven't reached max number of missed faces
            if (mNumNoFaces < MAX_MISS_FACES) {

                if (mHasFoundServer) {
                    Server.get("/error", mRequestQueue);
                }

                // flag as no photo error and stop
                mPhysicalInterface.writeLED(Color.RED);
                mState = State.NO_PHOTO;
                return;
            }

            // else assign face mat to adjusted original image and continue
            else {

                // crop sides
                Rect cropRect = new Rect(new Point(34, 0), new Point(190, eqMat.height()));
                Mat croppedMat = new Mat(eqMat, cropRect);

                // scale image
                double numDesiredRows = 40;
                double scaleFactor = numDesiredRows / croppedMat.rows();
                Log.d(TAG, String.format("scaleFactor = %f", scaleFactor));
                int newRows = (int) Math.floor(croppedMat.rows() * scaleFactor);
                int newCols = (int) Math.floor(croppedMat.cols() * scaleFactor);

                Mat scaleMat = new Mat(newRows, newCols, croppedMat.type());
                Imgproc.resize(croppedMat, scaleMat, new Size(newCols, newRows));
                Log.d(TAG, String.format("No face image size = %s", scaleMat.size().toString()));

                faceMat = scaleMat;
            }
        }

        // reset number of missed faces
        mNumNoFaces = 0;

        if (UPDATE_SCREEN) {

            // create bitmap from face image
            final Bitmap faceBmp = Bitmap.createBitmap(faceMat.width(), faceMat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(faceMat, faceBmp);

            // preview face image
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mImageView1.setImageBitmap(faceBmp);
                }
            });
        }

        // get drawing lines
        ArrayList<Line> copicLines = LineAlgorithm.getCopicLines(faceMat);
//        Utilities.printLineList("--- START COPIC LINES ---", copicLines, "--- END COPIC LINES ---");

        // add lines to move from center to starting point
        // (position at center facing top of page, turn left, move to edge, turn right, move to top, turn right, start drawing)
        int centerX = faceMat.width() / 2;
        int centerY = faceMat.height() / 2;
        Point centerBottom = new Point(centerX, faceMat.height());
        Point placementPoint = new Point(centerX, centerY+0.1);
        Point centerCenter = new Point(centerX, faceMat.height() / 2);
        Point leftCenter = new Point(0, centerY);
        Point startPoint = copicLines.get(0).getPoint1();
        copicLines.add(0, new Line(placementPoint, centerCenter, 0));
        copicLines.add(1, new Line(centerCenter, leftCenter, 0));
        copicLines.add(2, new Line(leftCenter, startPoint, 0));

        // save drawing lines and update state
        mDrawingLines.clear();
        mDrawingLines.addAll(copicLines);

        // send lines to server
        if (mHasFoundServer) {
            Server.sendLines(copicLines, mRequestQueue);
        }

        mState = State.WAITING_TO_DRAW;
        mPhysicalInterface.writeLED(Color.GREEN);

        // unset flag
        Log.d(TAG, "Successfully processed");
    }

    private void infoText(final String s) {

        Log.d(TAG, String.format("infoText = %s", s));

        if (!UPDATE_SCREEN)
            return;

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mInfoTextView.setText(s);
            }
        });
    }

    private void startDrawing() {

        mState = State.DRAWING;

        if (mHasFoundServer) {
            Server.get("/drawing", mRequestQueue);
        }

        // start drawing in 3 secs
        Log.d(TAG, "Drawing in 3 secs..");

        mPhysicalInterface.holdLED(Color.MAGENTA, 3000); // blocking

        // begin drawing
        Log.d(TAG, "Begin drawing");
        mPhysicalInterface.writeLED(Color.BLUE);

        // steppers sleep between use to save battery
        mMovementControl.sleepSteppers(false);

        // Queue up all the drawing ops
        for (int i = 0; i < mDrawingLines.size(); i++) {

            // Drawing op
            final Line current = mDrawingLines.get(i);
            final int currentIndex = i;
            final int nextIndex = i + 1;

            mBackgroundHandler.post(new Runnable() {

                @Override
                public void run() {
                    drawLine(current, nextIndex, mDrawingLines.size());
                }
            });

            if (nextIndex < mDrawingLines.size()) {

                // Pivoting op
                final Line next = mDrawingLines.get(nextIndex);
                mBackgroundHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        pivot(current, next, currentIndex);
                    }
                });
            } else {
                mBackgroundHandler.post(new Runnable() {

                    @Override
                    public void run() {

                        // todo: this should be stopDrawing()

                        if (mHasFoundServer) {
                            Server.get("/reset", mRequestQueue);
                        }
                    }
                });
            }
        }
    }

    private void stopDrawing() {

        Log.d(TAG, "Resetting");

        // Clear out the drawing ops queue
        mDrawingLines.clear();
        mBackgroundHandler.removeCallbacksAndMessages(null);

        // do not hold steppers to save battery
        mMovementControl.sleepSteppers(true);

        if (mHasFoundServer) {
            Server.get("/reset", mRequestQueue);
        }

        mState = State.NO_PHOTO;
        mPhysicalInterface.writeLED(Color.RED);
    }

    /*
     * This function is used for tuning the robot.
     * Adds 100 squares of length 25 to tune slop sleps.
     * @param rightTurn true for right-turn squares and false for left-turn squares
     */
    public void squareTest(boolean rightTurn) {

        int length = 20;
        mDrawingLines.clear();
        for (int i = 0; i < 100; i++) {
            if (rightTurn) {
                mDrawingLines.add(new Line(new Point(0, 0), new Point(length - 1, 0), 1));
                mDrawingLines.add(new Line(new Point(length, 0), new Point(length, length - 1), 2));
                mDrawingLines.add(new Line(new Point(length, length), new Point(1, length), 3));
                mDrawingLines.add(new Line(new Point(0, length), new Point(0, 1), 2));
            } else {
                mDrawingLines.add(new Line(new Point(0, 0), new Point(0, length - 1), 1));
                mDrawingLines.add(new Line(new Point(0, length), new Point(length - 1, length), 2));
                mDrawingLines.add(new Line(new Point(length, length), new Point(length, 1), 3));
                mDrawingLines.add(new Line(new Point(length, 0), new Point(1, 0), 2));
            }
        }

        mState = State.WAITING_TO_DRAW;
        mPhysicalInterface.writeLED(Color.GREEN);
    }

    /*
     * This function is used for tuning the robot.
     * Adds 100 rectangles with lines of increasing thicknesses to tune servo pressure.
     */
    public void pressureTest() {

        mDrawingLines.clear();

        for (int  i = 0; i < 100; i++) {
            mDrawingLines.add(new Line(new Point(0, 0), new Point(5, 0), 0));
            mDrawingLines.add(new Line(new Point(5, 0), new Point(10, 0), 1));
            mDrawingLines.add(new Line(new Point(10, 0), new Point(15, 0), 2));
            mDrawingLines.add(new Line(new Point(15, 0), new Point(20, 0), 3));

            mDrawingLines.add(new Line(new Point(20, 0), new Point(20, 5), 0));

            mDrawingLines.add(new Line(new Point(20, 5), new Point(15, 5), 0));
            mDrawingLines.add(new Line(new Point(15, 5), new Point(10, 5), 1));
            mDrawingLines.add(new Line(new Point(10, 5), new Point(5, 5), 2));
            mDrawingLines.add(new Line(new Point(5, 5), new Point(0, 5), 3));

            mDrawingLines.add(new Line(new Point(0, 5), new Point(0, 0), 0));
        }

        mState = State.WAITING_TO_DRAW;
        mPhysicalInterface.writeLED(Color.GREEN);
    }

    public void drawLine(Line current, int index, int count) {

        double scaledDistance;

        // drop pen
        int thickness = current.getThickness();
        mMovementControl.setMarkerPressure(thickness);
        if (thickness == 1) {
            mPhysicalInterface.writeLED(Color.YELLOW);
        } else if (thickness == 2) {
            mPhysicalInterface.writeLED(Color.rgb(255, 150, 0));
        } else if (thickness == 3) {
            mPhysicalInterface.writeLED(Color.RED);
        } else {
            mPhysicalInterface.writeLED(Color.CYAN);
        }

        // get line length and scale
        double distance = current.getLength();
        scaledDistance = distance * DRAW_SCALE;

        infoText(String.format("Drawing %f mm (line %d / %d)", scaledDistance, index, count));
        Log.d("oscar", String.format("DRAW LINE %s", current.toString()));

        // this function calls MainActivity.this.pivot() when steppers are finished
        mMovementControl.moveStraight(scaledDistance);
    }

    public void pivot(Line previous, Line current, final int currentLineIndex) {

        if (mHasFoundServer) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Server.get(String.format("/draw_line?line=%d", currentLineIndex - 1), mRequestQueue);
                }
            });
        }

        Point p1, p2, p3;

        // find angle between previous line and next line
        p1 = previous.getPoint1();
        p2 = current.getPoint1();
        p3 = current.getPoint2();

        double degrees = Utilities.calcDegrees(p1, p2, p3);

        // Log.d("fyi", String.format("degrees = %f", degrees));

        // skip turn if none required
        if (degrees == 0) {
            return;
        }

        // lift pen
        mMovementControl.setMarkerPressure(0);
        mPhysicalInterface.writeLED(Color.BLUE);

        infoText(String.format("Turning %f degrees", degrees));

        // this function calls MainActivity.this.drawNextLine() when steppers are finished
        mMovementControl.turn(degrees);
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (mPhysicalInterface != null) {
            mPhysicalInterface.writeLED(Color.BLACK); // off?
            mPhysicalInterface.close();
            mPhysicalInterface = null;
        }

        // close movement control objects
        if (mMovementControl != null) {
            mMovementControl.close();
        }

        if (mButtonGpio != null) {
            try {
                mButtonGpio.close();
                mButtonGpio = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close GPIO", e);
            }
        }

        // close camera resource
        if (mCameraHandler != null) {
            mCameraHandler.shutDown();
        }
    }
}
