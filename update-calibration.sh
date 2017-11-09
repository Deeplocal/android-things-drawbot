#! /bin/bash

if [ $# -ne 1 ]
  then
    echo "Usage: ./update-calibration.sh /path/to/calibration.json";
    exit;
fi

file_path=/storage/emulated/0/Android/data/com.deeplocal.drawbot/files/;
file_name=calibration-$(date +%s).json;

echo "Moving $file_name to $file_path";
adb push $1 $file_path$file_name;

echo "Broadcasting intent to update calibration";
adb shell am broadcast -a com.deeplocal.drawbot.intent.UPDATE_CALIBRATION --es file_path "$file_name";
