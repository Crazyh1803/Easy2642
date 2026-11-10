# Sets this PC up to build the signed release bundle (.aab) for Google Play.
# Safe to run again: it only does the steps that are still missing.
#
#   1. finds Java (Android Studio's bundled one, or JDK 17+)
#   2. finds or installs the Android SDK pieces the project needs
#   3. creates your upload keystore and keystore.properties (first run only)
#   4. builds app\build\outputs\bundle\release\app-release.aab

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Say($text)  { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host ""; Write-Host "STOPPED: $text" -ForegroundColor Red; exit 1 }
function JavaOk($dir) { $dir -and (Test-Path "$dir\bin\java.exe") -and (Test-Path "$dir\bin\keytool.exe") }

# ---------------------------------------------------------------- 1. Java
Say "Looking for Java"
$studioJbr = "$env:ProgramFiles\Android\Android Studio\jbr"
$JavaDir = $null
foreach ($candidate in @($studioJbr, "$env:LOCALAPPDATA\Programs\Android Studio\jbr", $env:JAVA_HOME)) {
    if (JavaOk $candidate) { $JavaDir = $candidate; break }
}
if (-not $JavaDir) {
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) {
        $found = Split-Path -Parent (Split-Path -Parent $onPath.Source)
        if (JavaOk $found) { $JavaDir = $found }
    }
}
if (-not $JavaDir) {
    if (Get-Command winget.exe -ErrorAction SilentlyContinue) {
        $answer = Read-Host "Android Studio is not installed. Install it now with winget? [y/N]"
        if ($answer -eq 'y') {
            winget install --id Google.AndroidStudio -e --accept-package-agreements --accept-source-agreements
            if (JavaOk $studioJbr) { $JavaDir = $studioJbr }
        }
    }
    if (-not $JavaDir) {
        Fail "Java was not found. Install Android Studio from https://developer.android.com/studio, open it once, then run this again."
    }
}
$env:JAVA_HOME = $JavaDir
$env:Path = "$JavaDir\bin;$env:Path"
$versionText = (cmd /c "`"$JavaDir\bin\java.exe`" -version 2>&1") -join "`n"
$major = 0
if ($versionText -match 'version "(\d+)') { $major = [int]$Matches[1] }
if ($major -lt 17) { Fail "Java $major found at $JavaDir, but version 17 or newer is needed. Installing Android Studio provides one." }
Write-Host "Using Java $major at $JavaDir"

# ---------------------------------------------------------------- 2. Android SDK
Say "Looking for the Android SDK"
$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (-not $Sdk) { $Sdk = "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path "$Sdk\platforms\android-35") -or -not (Test-Path "$Sdk\build-tools\34.0.0")) {
    Write-Host "Installing the SDK pieces this project needs into $Sdk (about 250 MB download)"
    $manager = "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
    if (-not (Test-Path $manager)) {
        New-Item -ItemType Directory -Force "$Sdk\cmdline-tools" | Out-Null
        $tmp = Join-Path $env:TEMP ("sdk-tools-" + [guid]::NewGuid())
        New-Item -ItemType Directory -Force $tmp | Out-Null
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile "$tmp\tools.zip"
        Expand-Archive "$tmp\tools.zip" -DestinationPath $tmp -Force
        if (Test-Path "$Sdk\cmdline-tools\latest") { Remove-Item -Recurse -Force "$Sdk\cmdline-tools\latest" }
        Move-Item "$tmp\cmdline-tools" "$Sdk\cmdline-tools\latest"
        Remove-Item -Recurse -Force $tmp
    }
    # Answer "y" to each licence prompt.
    (1..40 | ForEach-Object { 'y' }) | & $manager "platforms;android-35" "build-tools;34.0.0" "platform-tools" | Out-Null
}
if (-not (Test-Path "$Sdk\platforms\android-35")) { Fail "The Android SDK could not be installed at $Sdk." }
Write-Host "Using Android SDK at $Sdk"
# local.properties needs forward slashes (or doubled backslashes).
[IO.File]::WriteAllText("$Root\local.properties", "sdk.dir=" + ($Sdk -replace '\\', '/') + "`n")

# ---------------------------------------------------------------- 3. Upload key
if (Test-Path "$Root\keystore.properties") {
    Say "Signing key already set up (keystore.properties found)"
} else {
    Say "Creating your upload key"
    Write-Host "This key signs every version you upload to Google Play."
    Write-Host "  * Choose a password you will not lose, and store it in a password manager."
    Write-Host "  * Afterwards, back up upload-keystore.jks AND the password somewhere safe."
    Write-Host "    They are never committed to git."
    $Keystore = "$Root\upload-keystore.jks"
    if (Test-Path $Keystore) {
        Fail "upload-keystore.jks exists but keystore.properties does not. Restore keystore.properties from your backup, or move the old .jks away to create a new key."
    }
    $owner = Read-Host "Your name or company (shown inside the key) [Apps by Dan]"
    if (-not $owner) { $owner = "Apps by Dan" }
    $owner = $owner -replace '[,="\\+<>;#]', ''
    $pass = $null
    while (-not $pass) {
        $s1 = Read-Host "New keystore password (6+ characters)" -AsSecureString
        $s2 = Read-Host "Type it again" -AsSecureString
        $p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s1))
        $p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s2))
        if ($p1 -cne $p2) { Write-Host "They did not match. Try again."; continue }
        if ($p1.Length -lt 6) { Write-Host "Too short. Try again."; continue }
        if ($p1 -match '[\\ ]') { Write-Host "Avoid spaces and backslashes. Try again."; continue }
        $pass = $p1
    }
    $env:STOREPASS = $pass
    & "$JavaDir\bin\keytool.exe" -genkeypair -keystore $Keystore -storetype PKCS12 -alias upload `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass:env STOREPASS -keypass:env STOREPASS -dname "CN=$owner" | Out-Null
    $keytoolExit = $LASTEXITCODE
    Remove-Item Env:\STOREPASS
    if ($keytoolExit -ne 0 -or -not (Test-Path $Keystore)) { Fail "keytool could not create the keystore." }
    $props = "storeFile=upload-keystore.jks`nstorePassword=$pass`nkeyAlias=upload`nkeyPassword=$pass`n"
    [IO.File]::WriteAllText("$Root\keystore.properties", $props)
    Write-Host "Created upload-keystore.jks and keystore.properties"
}

# ---------------------------------------------------------------- 4. Build
Say "Building the signed release bundle (the first run downloads a lot; allow 5-15 minutes)"
& "$Root\gradlew.bat" --no-daemon :app:bundleRelease
if ($LASTEXITCODE -ne 0) { Fail "The build failed. Scroll up for the first error." }

$Aab = "$Root\app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $Aab)) { Fail "The build finished but $Aab was not produced." }
# jarsigner exits 0 for unsigned files too, so look for its "jar verified" line.
$verify = (cmd /c "`"$JavaDir\bin\jarsigner.exe`" -verify `"$Aab`" 2>&1") -join "`n"
if ($verify -notmatch 'jar verified') { Fail "The bundle was built but is not signed. Check keystore.properties." }

Say "Done"
Write-Host "Signed bundle:  $Aab"
Write-Host "Upload that file in the Play Console (Testing > Internal testing > Create new release)."
Write-Host ""
Write-Host "Before each later upload, raise versionCode (and versionName) in app\build.gradle.kts,"
Write-Host "then run this script again."
Write-Host ""
Write-Host "To work on the app, open this folder in Android Studio: $Root"
Start-Process explorer.exe "/select,`"$Aab`""
