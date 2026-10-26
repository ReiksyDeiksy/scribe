param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = Join-Path $PSScriptRoot '.tools\jdk'
$env:ANDROID_HOME = Join-Path $PSScriptRoot '.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.tools\gradle-home'
$env:ANDROID_USER_HOME = Join-Path $PSScriptRoot '.tools\android-user'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot '.tools\gradle-8.11.1\bin\gradle.bat') --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
