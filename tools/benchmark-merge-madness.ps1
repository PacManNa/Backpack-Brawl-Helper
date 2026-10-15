param(
    [int]$Scenarios = 500,
    [int]$Workers = 0
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$classes = Join-Path $repoRoot 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
$androidJar = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platforms/android-35/android.jar'
$source = Join-Path $PSScriptRoot 'MergeMadnessStrategyBenchmark.java'
$output = Join-Path $repoRoot 'app/build/tmp/merge-madness-benchmark'

if (-not (Test-Path -LiteralPath $classes)) {
    throw 'Compile the app first with gradlew assembleDebug.'
}
if (-not (Test-Path -LiteralPath $androidJar)) {
    throw 'Android platform android-35 is not installed.'
}
New-Item -ItemType Directory -Path $output -Force | Out-Null

$classpath = $classes + ';' + $androidJar
& javac -cp $classpath -d $output $source
if ($LASTEXITCODE -ne 0) {
    throw 'Could not compile the strategy benchmark.'
}

$runtimeClasspath = $output + ';' + $classpath
& java -cp $runtimeClasspath ua.borlock.autotap.MergeMadnessStrategyBenchmark $Scenarios $Workers
if ($LASTEXITCODE -ne 0) {
    throw 'The strategy benchmark failed its acceptance criteria.'
}
