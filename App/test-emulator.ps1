param([string]$Serial = 'emulator-5580')
$ErrorActionPreference = 'Stop'
$adb = Join-Path $PSScriptRoot '.tools\android-sdk\platform-tools\adb.exe'
$emulated = & $adb -s $Serial shell getprop ro.kernel.qemu
if ($emulated.Trim() -ne '1') { throw 'This automated test script only targets an Android emulator.' }
& $adb -s $Serial install -r (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
& $adb -s $Serial install -r (Join-Path $PSScriptRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { throw 'Instrumentation installation failed' }
& $adb -s $Serial shell pm grant ro.scribe.lectures android.permission.RECORD_AUDIO
& $adb -s $Serial shell pm grant ro.scribe.lectures android.permission.POST_NOTIFICATIONS
$result = & $adb -s $Serial shell am instrument -w ro.scribe.lectures.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'instrumentation.log')
$result
if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($result -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED|shortMsg=') { throw 'Android instrumentation tests failed; see instrumentation.log' }
