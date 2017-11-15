package com.deeplocal.drawbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateCalibrationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // get file path for original intent
        String inFilePath = intent.getStringExtra(UpdateCalibrationService.FILE_PATH);

        // create intent with file path and start service
        Intent serviceIntent = new Intent(context, UpdateCalibrationService.class);
        serviceIntent.putExtra(UpdateCalibrationService.FILE_PATH, inFilePath);
        context.startService(serviceIntent);
    }
}
