<#
.SYNOPSIS
    Builds a signed release APK, tags the current commit, and publishes a GitHub release
    with the APK attached so testers can download it directly (no Play Store needed).

    Doesn't push a "new version available" notification -- the app notifies itself locally
    the moment someone who's actually installed the update opens it (see
    Notifications.sendVersionInstalledNotification / MainActivity's lastSeenAppVersion check),
    instead of blasting everyone the instant this script runs, before anyone has the update.

.PARAMETER Flavor
    Product flavor to build ("basic" or "freenet"). Defaults to "basic" (same flavor
    published to the Play Store), so the GitHub download matches what testers already use.

.PARAMETER Draft
    Create the GitHub release as a draft instead of publishing it immediately.

.PARAMETER PlayTrack
    Google Play test track for the basic flavor: "internal", "alpha" (closed), or
    "beta" (open). Use "all" to publish to all three test tracks, excluding production.
    When omitted for a basic release, the script asks interactively. Freenet releases
    are never published to Google Play.

.EXAMPLE
    ./scripts/release.ps1
    ./scripts/release.ps1 -PlayTrack alpha
    ./scripts/release.ps1 -PlayTrack all
    ./scripts/release.ps1 -Flavor freenet -Draft
#>
param(
    [ValidateSet("basic", "freenet")]
    [string]$Flavor = "basic",
    [ValidateSet("internal", "alpha", "beta", "all")]
    [string]$PlayTrack,
    [switch]$Draft
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location $repoRoot

if ($Flavor -eq "basic" -and -not $PlayTrack) {
    Write-Host "Choose the Google Play track:" -ForegroundColor Cyan
    Write-Host "  1. internal - trusted internal testers"
    Write-Host "  2. alpha    - closed testing"
    Write-Host "  3. beta     - open testing"
    Write-Host "  4. all      - all test tracks (no production)"
    $trackChoice = Read-Host "Track [1]"
    $PlayTrack = switch ($trackChoice) {
        "2" { "alpha" }
        "3" { "beta" }
        "4" { "all" }
        default { "internal" }
    }
}

if (-not (Test-Path "keystore.properties")) {
    Write-Warning "keystore.properties not found - the APK will be built UNSIGNED. Aborting."
    exit 1
}

$status = git status --porcelain
if ($status) {
    Write-Error "Working tree is not clean. Commit or stash your changes before releasing:`n$status"
    exit 1
}

git fetch origin --tags | Out-Null
$branchStatus = git status -sb
if ($branchStatus -notmatch "\[.*ahead") {
    # ok: up to date or behind is fine to check separately
}
$behind = git rev-list --count HEAD..origin/main
if ($behind -gt 0) {
    Write-Host "Local main is $behind commit(s) behind origin/main; updating with fast-forward ..." -ForegroundColor Cyan
    git pull --ff-only origin main
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Could not fast-forward local main. Resolve the branch difference before releasing."
        exit 1
    }
}

Write-Host "Updating the in-app release history ..." -ForegroundColor Cyan
python "$repoRoot/scripts/generate_release_notes.py"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Could not generate the in-app release history."
    exit 1
}
git diff --quiet -- app/src/main/assets/release-notes.json
$releaseNotesChanged = $LASTEXITCODE -ne 0
if ($releaseNotesChanged) {
    git add app/src/main/assets/release-notes.json
    git commit -m "Update app information"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Could not commit the updated in-app release history."
        exit 1
    }
    git push origin HEAD
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Could not push the updated in-app release history."
        exit 1
    }
}

$buildType = "release"
$capFlavor = $Flavor.Substring(0,1).ToUpper() + $Flavor.Substring(1)
$assembleTask = "assemble$capFlavor$($buildType.Substring(0,1).ToUpper() + $buildType.Substring(1))"

Write-Host "Building $assembleTask ..." -ForegroundColor Cyan
& "$repoRoot/gradlew.bat" $assembleTask
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
    exit 1
}

$outputDir = "app/build/outputs/apk/$Flavor/$buildType"
$metadataFile = Join-Path $outputDir "output-metadata.json"
if (-not (Test-Path $metadataFile)) {
    Write-Error "Could not find $metadataFile - build output layout may have changed."
    exit 1
}

$metadata = Get-Content $metadataFile -Raw | ConvertFrom-Json
$element = $metadata.elements[0]
$versionName = $element.versionName
$apkFile = Join-Path $outputDir $element.outputFile

if (-not (Test-Path $apkFile)) {
    Write-Error "Built APK not found at $apkFile"
    exit 1
}

$tag = "v$versionName" + $(if ($Flavor -ne "basic") { "-$Flavor" } else { "" })

if (git tag -l $tag) {
    Write-Error "Tag $tag already exists. Bump the version (commit something) before releasing again."
    exit 1
}

Write-Host "Tagging $tag on $(git rev-parse --short HEAD) ..." -ForegroundColor Cyan
git tag -a $tag -m "Release $tag"
git push origin $tag

$releaseArgs = @(
    "release", "create", $tag,
    $apkFile,
    "--repo", "strammermax/breezy-weather",
    "--title", $tag,
    "--generate-notes"
)
if ($Draft) { $releaseArgs += "--draft" }

Write-Host "Publishing GitHub release $tag with $apkFile ..." -ForegroundColor Cyan
& gh @releaseArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Could not create the GitHub release."
    exit 1
}

if ($Flavor -eq "basic") {
    Write-Host "Starting Play Store publication to '$PlayTrack' ..." -ForegroundColor Cyan
    gh workflow run "Publish to Play Store" `
        --repo "strammermax/breezy-weather" `
        --ref $tag `
        -f "track=$PlayTrack"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Could not start the Play Store workflow for track '$PlayTrack'."
        exit 1
    }
    Write-Host "Play Store workflow started for '$PlayTrack'." -ForegroundColor Green
}

Write-Host "Done: https://github.com/strammermax/breezy-weather/releases/tag/$tag" -ForegroundColor Green
