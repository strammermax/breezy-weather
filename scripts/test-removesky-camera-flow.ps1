param(
    [Parameter(Mandatory = $true)]
    [string]$ImagePath,

    [Parameter(Mandatory = $true)]
    [string]$Location,

    [Parameter(Mandatory = $true)]
    [double]$Latitude,

    [Parameter(Mandatory = $true)]
    [double]$Longitude,

    [string]$LocalProperties = (Join-Path $PSScriptRoot "..\local.properties"),

    [switch]$KeepDownloadedImage
)

$ErrorActionPreference = "Stop"
$baseUrl = "https://removesky.vanburik.info"
$apiBase = "$baseUrl/api/v1"
$resolvedImage = (Resolve-Path -LiteralPath $ImagePath).Path
$uploadImage = $resolvedImage
$normalizedImage = $null

function Read-LocalProperties([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -le 0) {
            continue
        }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

function Assert-Value([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw $Message
    }
}

function Normalize-JpegOrientation([string]$Path) {
    Add-Type -AssemblyName System.Drawing
    $image = [Drawing.Image]::FromFile($Path)
    try {
        if (-not ($image.PropertyIdList -contains 0x0112)) {
            return $null
        }
        $orientation = $image.GetPropertyItem(0x0112).Value[0]
        $rotation = switch ($orientation) {
            3 { [Drawing.RotateFlipType]::Rotate180FlipNone }
            6 { [Drawing.RotateFlipType]::Rotate90FlipNone }
            8 { [Drawing.RotateFlipType]::Rotate270FlipNone }
            default { return $null }
        }
        $image.RotateFlip($rotation)
        $target = Join-Path ([IO.Path]::GetTempPath()) ("removesky-oriented-" + [guid]::NewGuid() + ".jpg")
        $image.Save($target, [Drawing.Imaging.ImageFormat]::Jpeg)
        return $target
    } finally {
        $image.Dispose()
    }
}

$normalizedImage = Normalize-JpegOrientation $resolvedImage
if ($normalizedImage) {
    $uploadImage = $normalizedImage
}

$properties = Read-LocalProperties $LocalProperties
$headers = @{
    "User-Agent" = "LiveWallpaperWeather-IntegrationTest/1.0"
}

if ($properties["lww.cfaccess.id"] -and $properties["lww.cfaccess.secret"]) {
    $headers["CF-Access-Client-Id"] = $properties["lww.cfaccess.id"]
    $headers["CF-Access-Client-Secret"] = $properties["lww.cfaccess.secret"]
}
if ($properties["lww.removesky.apikey"]) {
    $headers["x-api-key"] = $properties["lww.removesky.apikey"]
}

Write-Host "1/4 Health check"
$health = Invoke-RestMethod -Uri "$apiBase/health" -Headers $headers -TimeoutSec 30
Assert-Value ($health.status -eq "ok") "RemoveSky health status is not ok."

Write-Host "2/4 Upload and process camera image (this may take about 40 seconds)"
$upload = Invoke-RestMethod `
    -Method Post `
    -Uri "$apiBase/upload" `
    -Headers $headers `
    -Form @{
        file = Get-Item -LiteralPath $uploadImage
        location = $Location
        lat = $Latitude.ToString([Globalization.CultureInfo]::InvariantCulture)
        lon = $Longitude.ToString([Globalization.CultureInfo]::InvariantCulture)
    } `
    -TimeoutSec 120

Assert-Value (-not [string]::IsNullOrWhiteSpace($upload.url)) "Upload response has no processed image URL."
Assert-Value (-not [string]::IsNullOrWhiteSpace($upload.location)) "Upload response has no location."

Write-Host "3/4 Download processed image"
$downloadPath = Join-Path ([IO.Path]::GetTempPath()) ("removesky-test-" + [guid]::NewGuid() + ".png")
$download = Invoke-WebRequest -Uri $upload.url -Headers $headers -OutFile $downloadPath -PassThru -TimeoutSec 60
Assert-Value ((Get-Item -LiteralPath $downloadPath).Length -gt 0) "Processed image download is empty."
$contentType = [string]$download.Headers["Content-Type"]
Assert-Value ($contentType.StartsWith("image/")) "Processed URL returned '$contentType' instead of an image."

Write-Host "4/4 Verify database record"
$encodedLocation = [Uri]::EscapeDataString([string]$upload.location)
$database = Invoke-RestMethod -Uri "$apiBase/db?location=$encodedLocation&limit=100" -Headers $headers -TimeoutSec 30
$matching = @($database.results) | Where-Object {
    $_.processed_url -eq $upload.url -and
    -not [string]::IsNullOrWhiteSpace([string]$_.location) -and
    -not [string]::IsNullOrWhiteSpace([string]$_.processed_image)
}
Assert-Value ($matching.Count -gt 0) "Processed image was not found in the database with location and image path."

Write-Host "PASS: upload, processed image, mandatory location and database record are valid."
Write-Host "Processed URL: $($upload.url)"
Write-Host "Resolved location: $($upload.location)"

if ($KeepDownloadedImage) {
    Write-Host "Downloaded image: $downloadPath"
} else {
    Remove-Item -LiteralPath $downloadPath -Force
}

if ($normalizedImage -and (Test-Path -LiteralPath $normalizedImage)) {
    Remove-Item -LiteralPath $normalizedImage -Force
}
