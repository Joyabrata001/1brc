# =========================================================
# 1BRC DEV SCRIPT (Windows + PowerShell + JDK 21)
# =========================================================

# ---------------------------------------------------------
# Java Environment
# ---------------------------------------------------------
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# ---------------------------------------------------------
# Config
# ---------------------------------------------------------
$CLASS_NAME = "dev.morling.onebrc.CalculateAverage_joyab"
$SOURCE_FILE = "src/main/java/dev/morling/onebrc/CalculateAverage_joyab.java"

$JAR_NAME = "CalculateAverage_joyab.jar"

# Change if your measurements file is elsewhere
$MEASUREMENTS_FILE = "measurements.txt"

# JFR output
$JFR_FILE = "recording_temperature_parsing_loop_abandoned(1).jfr"

# ---------------------------------------------------------
# Clean previous JFR
# ---------------------------------------------------------
if (Test-Path $JFR_FILE) {
    Remove-Item $JFR_FILE
}

# ---------------------------------------------------------
# Incremental Compile
# ---------------------------------------------------------
Write-Host ""
Write-Host "=== COMPILING ===" -ForegroundColor Yellow

javac `
    -cp "target/classes" `
    -d "target/classes" `
    $SOURCE_FILE

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Compile failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Compilation successful." -ForegroundColor Green

# ---------------------------------------------------------
# Package JAR
# ---------------------------------------------------------
Write-Host ""
Write-Host "=== PACKAGING JAR ===" -ForegroundColor Yellow

jar `
    --create `
    --file=$JAR_NAME `
    --main-class=$CLASS_NAME `
    -C target/classes .

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "JAR packaging failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "JAR created successfully." -ForegroundColor Green

# ---------------------------------------------------------
# JVM Options
# ---------------------------------------------------------

# IMPORTANT:
# Start with these.
# Add/remove flags as you optimize.

$JAVA_OPTS = @(
    "-Xms2g",
    "-Xmx2g",

    # GC
    "-XX:+UseG1GC",

    # Flight Recorder
    "-XX:StartFlightRecording=filename=$JFR_FILE,settings=profile,dumponexit=true,jdk.ExecutionSample#enabled=true,jdk.ExecutionSample#period=1ms"

    # Helpful for benchmarking consistency
    "-XX:+UnlockExperimentalVMOptions"

    # Optional:
    # Uncomment later for experiments
    "-XX:+UnlockDiagnosticVMOptions"
    "-XX:+DebugNonSafepoints"

    # "-XX:+UseStringDeduplication",
    # "-XX:+AlwaysPreTouch",
    # "-XX:-TieredCompilation",
    # "-XX:+PrintCompilation"
)

# ---------------------------------------------------------
# Run
# ---------------------------------------------------------
Write-Host ""
Write-Host "=== RUNNING IMPLEMENTATION ===" -ForegroundColor Cyan
Write-Host ""

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

java $JAVA_OPTS `
    -jar $JAR_NAME `
    $MEASUREMENTS_FILE

$exitCode = $LASTEXITCODE

$stopwatch.Stop()

Write-Host ""
Write-Host "=== EXECUTION FINISHED ===" -ForegroundColor Cyan
Write-Host ("Time Taken: {0:N3} seconds" -f $stopwatch.Elapsed.TotalSeconds) -ForegroundColor Green

# ---------------------------------------------------------
# JFR Info
# ---------------------------------------------------------
if (Test-Path $JFR_FILE) {
    Write-Host ""
    Write-Host "JFR recording created:" -ForegroundColor Magenta
    Write-Host $JFR_FILE
    Write-Host ""
    Write-Host "Open it in Java Mission Control (JMC)." -ForegroundColor Magenta
}
else {
    Write-Host ""
    Write-Host "WARNING: JFR file not found!" -ForegroundColor Red
}

exit $exitCode