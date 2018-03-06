@echo on
set adb_command=C:\Users\Deeplocal\Documents\platform-tools\adb
set file_path=/storage/emulated/0/Android/data/com.deeplocal.drawbot/files/
IF EXIST "%1" (
	echo Moving %1 to %file_path%%1
	%adb_command% push %1 %file_path%%1
	echo "Broadcasting intent to update calibration";
	%adb_command% shell am broadcast -a com.deeplocal.drawbot.intent.UPDATE_CALIBRATION --es file_path "%1"
) ELSE (
	echo "Usage: update-calibration calibration-file.json"
)
