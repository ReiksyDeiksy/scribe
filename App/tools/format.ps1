$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourceFiles = @('main','test','androidTest') | ForEach-Object {
    Get-ChildItem -LiteralPath (Join-Path $projectRoot "app\src\$_\java") -Filter '*.java' -Recurse -File
} | Select-Object -ExpandProperty FullName
& (Join-Path $projectRoot '.tools\jdk\bin\java.exe') -jar (Join-Path $projectRoot '.tools\java-format.jar') --replace @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Formatting failed' }
