$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$classes = Join-Path $repoRoot 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
$androidJar = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platforms/android-35/android.jar'
$source = Join-Path $PSScriptRoot 'MergeMadnessScreenshotCheck.java'
$fixtures = Join-Path $repoRoot 'app/src/test/resources/merge_madness'
$rawAssets = Join-Path $repoRoot 'app/src/main/res/raw'
$output = Join-Path $repoRoot 'app/build/tmp/merge-madness-screenshot-check'

if (-not (Test-Path -LiteralPath $classes)) {
    throw 'Compile the debug app first with gradlew assembleDebug.'
}
if (-not (Test-Path -LiteralPath $androidJar)) {
    throw 'Android platform android-35 is not installed.'
}
New-Item -ItemType Directory -Path $output -Force | Out-Null

$classpath = $classes + ';' + $androidJar
& javac -cp $classpath -d $output $source
if ($LASTEXITCODE -ne 0) {
    throw 'Could not compile the screenshot regression probe.'
}

$runtimeClasspath = $output + ';' + $classpath
& java '-Djava.awt.headless=true' -cp $runtimeClasspath `
    ua.borlock.autotap.MergeMadnessScreenshotCheck $fixtures $rawAssets
if ($LASTEXITCODE -ne 0) {
    throw 'Screenshot regression check failed.'
}
