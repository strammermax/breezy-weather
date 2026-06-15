# ACT-008 visual regression baselines

Reference images for `WallpaperVisualRegressionTest`
(`app/src/androidTest/kotlin/org/breezyweather/wallpaper/WallpaperVisualRegressionTest.kt`).

## Running the tests

```
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
./gradlew :app:connectedBasicDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.breezyweather.wallpaper.WallpaperVisualRegressionTest
```

Requires a running emulator or device (API 21+). AGSL effect passes only run on API 33+
(Android 13); below that, `WallpaperWeatherEffectRenderer` automatically uses its Canvas
fallback, so running the suite on both an API 33+ and an API <33 image exercises both
render paths (ACT-008 section 10).

## Fixed inputs (ACT-008 section 9)

- Surface size: 1080 x 2280.
- Quality profile: Balanced for all scenarios.
- 30 fixed 33ms update steps are applied before drawing, for deterministic particle/cloud/drop
  settling.
- `sunriseMillis` = 06:00, `sunsetMillis` = 21:00, `moonriseMillis` = 20:00,
  `moonsetMillis` = 07:00 (offsets within a day, in milliseconds).
- Per-scenario weather kind, `daylight` and seed are listed in the `scenarios` list in
  `WallpaperVisualRegressionTest.kt`.

## Reduced scope

`WallpaperSceneRenderer`'s sky/sun/moon drawing is an intentionally simplified placeholder
(flat gradient + a single circle depending on `daytime`), not a copy of
`MaterialLiveWallpaperService`'s production celestial positioning. These tests therefore focus
on the ACT-003..ACT-007 effect layers (clouds, precipitation, fog/haze, glass-rain, stars,
quality profiles), not on exact sun/moon screen position.

## Pulling generated images / updating baselines

Reviewed baseline PNGs are packaged from:

```
app/src/androidTest/assets/wallpaper-regression-baseline/
```

Generated images are written under the instrumentation target app's external files directory:

```
adb pull /storage/emulated/0/Android/data/com.livewallpaperweather.debug/files/wallpaper-regression test-artifacts/regression-baseline
```

- A missing packaged baseline is a test failure; clean devices never bless current output.
- To intentionally generate review candidates, add
  `-Pandroid.testInstrumentationRunnerArguments.generateBaselines=true` to the command above.
- To update a baseline, pull the generated PNGs, review them, and
  copy the approved files into the Android-test assets directory above.
- A mismatch (similarity < 0.95) also saves `<name>_diff.png` next to the generated image for
  manual inspection.
