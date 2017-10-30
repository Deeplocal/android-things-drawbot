#! /bin/bash

if [ $# -ne 1 ]
  then
    echo "Usage: ./update-calibration.sh /path/to/calibration.json";
    exit;
fi

file_name=calibration-$(date +%s).json;

echo "Moving $file_name to /sdcard/";
adb push $1 /sdcard/$file_name;

echo "Copying /sdcard/$file_name to /data/user/0/com.deeplocal.drawbot/files/$file_name";
adb shell run-as com.deeplocal.drawbot cp /sdcard/$file_name /data/user/0/com.deeplocal.drawbot/files/$file_name;

echo "Broadcasting intent to update calibration";
adb shell am broadcast -a com.deeplocal.drawbot.intent.UPDATE_CALIBRATION --es file_path "$file_name";