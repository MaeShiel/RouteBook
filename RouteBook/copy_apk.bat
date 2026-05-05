@echo off
setlocal
set "SRC=C:\Users\Maeshiel\Documents\Routebook\RouteBook\app\build\outputs\apk\debug\app-debug.apk"
set "DEST=C:\Users\Maeshiel\Documents\Routebook\RouteBook\RouteBook.apk"

if exist "%SRC%" (
    copy /Y "%SRC%" "%DEST%"
    echo APK copied and renamed to %DEST%
) else (
    echo Source APK not found: %SRC%
)
endlocal
