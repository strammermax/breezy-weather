<#
.SYNOPSIS
    Builds a signed release APK, tags the current commit, and publishes a GitHub release
    with the APK attached so testers can download it directly (no Play Store needed).

.PARAMETER Flavor
    Product flavor to build ("basic" or "freenet"). Defaults to "basic" (same flavor
    published to the Play Store), so the GitHub download matches what testers already use.

.PARAMETER Draft
    Create the GitHub release as a draft instead of publishing it immediately.

.EXAMPLE
    ./scripts/release.ps1
    ./scripts/release.ps1 -Flavor freenet -Draft
#>
param(
    [ValidateSet("basic", "freenet")]
    [string]$Flavor = "basic",
    [switch]$Draft
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location $repoRoot

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
    Write-Error "Local main is $behind commit(s) behind origin/main. Pull first."
    exit 1
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

Write-Host "Done: https://github.com/strammermax/breezy-weather/releases/tag/$tag" -ForegroundColor Green
