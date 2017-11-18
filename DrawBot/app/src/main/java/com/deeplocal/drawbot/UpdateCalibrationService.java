package com.deeplocal.drawbot;

import android.app.IntentService;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

public class UpdateCalibrationService extends IntentService {

    public static final String FILE_PATH = "file_path";

    public UpdateCalibrationService() {
        super("UpdateCalibrationService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        // get file path from intent
        String filePath = intent.getStringExtra(FILE_PATH);

        // if not extra on the intent, assume default.json
        if (filePath == null) {
            filePath = "default.json";
            Log.d(MainActivity.TAG, "No file from intent, using default.json");
        }

        // if path not provided, assume app file directory
        if (!filePath.contains(File.separator)) {
            filePath = new File(getApplicationContext().getExternalFilesDir(null), filePath).getAbsolutePath();
            Log.d(MainActivity.TAG, "No file path from intent, using app external files directory");
        }

        try {

            // open file input stream
            File file = new File(filePath);
            FileInputStream fileInputStream = new FileInputStream(file);

            // buffered read from file and convert to string
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            String jsonString = sb.toString();

            // convert string to json object
            JSONObject json = new JSONObject(jsonString);

            // create hashmap to store tuning params
            HashMap<String, String> tuningParams = new HashMap<>();

            // add slop steps to hashmap
            JSONObject slopSteps = json.getJSONObject("slopSteps");
            tuningParams.put(RobotConfig.KEY_SLOP_FWD_R, slopSteps.getString("rightFwd"));
            tuningParams.put(RobotConfig.KEY_SLOP_BACK_R, slopSteps.getString("rightBack"));
            tuningParams.put(RobotConfig.KEY_SLOP_FWD_L, slopSteps.getString("leftFwd"));
            tuningParams.put(RobotConfig.KEY_SLOP_BACK_L, slopSteps.getString("leftBack"));

            // add spacing adjustments to hashmap
            JSONObject spacingAdjust = json.getJSONObject("spacingAdjust");
            tuningParams.put(RobotConfig.KEY_SPACING_R, spacingAdjust.getString("right"));
            tuningParams.put(RobotConfig.KEY_SPACING_L, spacingAdjust.getString("left"));

            // add lateral shift adjustments to hashmap
            JSONObject lateralShift = json.getJSONObject("lateralShift");
            tuningParams.put(RobotConfig.KEY_SHIFT_R, lateralShift.getString("right"));
            tuningParams.put(RobotConfig.KEY_SHIFT_L, lateralShift.getString("left"));

            // add servo positions to hashmap
            JSONArray servoPos = json.getJSONArray("servoPos");
            String tempServoPos = "";
            for (int i = 0; i < servoPos.length(); i++) {
                if (i != 0) {
                    tempServoPos += ",";
                }
                tempServoPos += servoPos.getString(i);
            }
            tuningParams.put(RobotConfig.KEY_SERVO_POS, tempServoPos);

            // update tuning params
            RobotConfig.getInstance(getApplicationContext()).updateCalibration(tuningParams);

            Log.d(MainActivity.TAG, "Updated calibration");

        } catch (FileNotFoundException e) {
            Log.e(MainActivity.TAG, "Could not find calibration file", e);
        } catch (IOException e2) {
            Log.e(MainActivity.TAG, "Could not read from file", e2);
        } catch (JSONException e3) {
            Log.e(MainActivity.TAG, "JSON error", e3);
        }
    }
}
