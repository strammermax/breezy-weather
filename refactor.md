# Refactor opportunities — LiveWeatherApp

> Status: identified, not yet implemented.  
> Sorted rough-order-of-impact (biggest win first).

---

## 1. WeatherView lifecycle duplicated in HomeFragment + DetailsActivity

**Files**
- `ui/main/fragments/HomeFragment.kt` (~L119–148, L416–441)
- `ui/details/DetailsActivity.kt` (~L50–98)

**Issue**  
Both screens independently implement the same four-step WeatherView pattern:
1. `setGravitySensorEnabled(settingsManager.isGravitySensorEnabled)`
2. `setDoAnimate(isBackgroundAnimationEnabled())`
3. `setWeather(kind, daylight, isDarkMode)` → `clearWeatherViewBackground()` → `setDrawable(true)`
4. `onResume` → `setDrawable(true)` / `onPause` → `setDrawable(false)`

`isBackgroundAnimationEnabled()` is copy-pasted verbatim between the two files.

**Refactor**  
Extract a `WeatherBackgroundController(context, lifecycleOwner)` class that owns the `WeatherView`, attaches it to a given `ViewGroup`, and exposes a single `setWeather(kind, daylight)` method. Screens pass in their root container; the controller handles everything else. Both `HomeFragment` and `DetailsActivity` reduce to 2–3 lines each.

This is also the foundation for the planned "WeatherBackground module" (see session 2026-06-16).

---

## 2. drawCelestialBody / sunVisibility / moonVisibility duplicated in service + snapshot

**Files**
- `wallpaper/MaterialLiveWallpaperService.kt` `WeatherEngine` (~L1029–1120)
- `wallpaper/WallpaperSceneSnapshot.kt` (~L102–168)

**Issue**  
`drawCelestialBody`, `drawSun`, `sunVisibility`, `moonVisibility`, and `celestialProgress` exist in both places. The service version caches paint objects for performance; the snapshot version allocates fresh `Paint` each call. The arc-position math (`horizonY`, `peakY`, `sin(PI * progress)`) is identical.

`CelestialGlow` is already a shared helper for the sun glow itself — the surrounding logic (position, visibility, moon) is not yet shared.

**Refactor**  
Move the position+visibility+draw logic into `CelestialGlow` or a new `CelestialRenderer` object. The service passes its cached paints; the snapshot passes freshly allocated ones. Single source of truth for the arc math.

---

## 3. MaterialLiveWallpaperService.kt is 1 450 lines — god class

**File**  
`wallpaper/MaterialLiveWallpaperService.kt`

**Issue**  
`WeatherEngine` (the inner class) does:
- Draw loop coordination (canvas locking, frame rate)
- Parallax offset math
- Sky/photo layer lifecycle (`ensureForeground`, `buildPhotoForeground`, `buildSkyBackground`)
- Celestial body rendering (`drawCelestialBody`, `drawSun`)
- Day/night auto-switching (`refreshAutomaticDayNight`)
- Wallpaper snapshot capture (every 30 frames)
- Season grading
- Transition management
- `WallpaperColors` reporting

**Refactor**  
Split into focused collaborators:
| New class | Responsibility |
|---|---|
| `WallpaperLayerManager` | Sky + photo drawable lifecycle, bounds, parallax |
| `WallpaperFrameLoop` | Canvas lock/unlock, frame timing, snapshot counter |
| `CelestialRenderer` | drawCelestialBody (see item 2) |
| Keep in `WeatherEngine` | Orchestration only (≤ 300 lines) |

---

## 4. WallpaperSceneSnapshot.drawPhotoForeground vs. service buildPhotoForeground

**Files**
- `wallpaper/WallpaperSceneSnapshot.kt` `drawPhotoForeground()` (L170–181)
- `wallpaper/MaterialLiveWallpaperService.kt` `buildPhotoForeground()` (~L883–950)

**Issue**  
Both compute how to place the photo at the bottom of the screen (`PHOTO_HEIGHT_FRACTION`, scale-to-fit, center horizontally). The service bakes it into a `BitmapDrawable`; the snapshot draws it inline. The fraction constants and the scale math are nearly identical but live in two places.

**Refactor**  
Add a `WallpaperPhotoLayout` object with a single `drawPhoto(canvas, width, height, photo)` method (and matching constants). Both callers delegate to it.

---

## 5. DetailsScreen.kt LaunchedEffect does too much (snapshot + state update in one block)

**File**  
`ui/details/DetailsScreen.kt` (~L196–250)

**Issue**  
The single `LaunchedEffect(loc.weather, pagerPage)` block:
1. Resolves sun/moon intervals
2. Picks the half-day and weatherKind
3. Calls `detailsViewModel.updateBackground()` (drives WeatherView animation)
4. Loads the cached photo bitmap (IO)
5. Renders a full-screen `WallpaperSceneSnapshot` bitmap (CPU-heavy)
6. Sets it as the window background

Steps 3 and 5–6 are independent. If the photo hasn't changed (only the day changed), step 4 wastes an IO read. Step 5 blocks the coroutine for ~50–100 ms on mid-range devices.

**Refactor**  
Split into two `LaunchedEffect`s:
- `LaunchedEffect(loc.weather, pagerPage)` → steps 1–3 only (fast, synchronous)
- `LaunchedEffect(loc.weather, pagerPage, photo)` → steps 4–6 (IO + render), with `photo` cached via `remember` keyed on `WallpaperRepository` hash so it's only reloaded when the file changes

---

## 6. `isBackgroundAnimationEnabled()` copy-pasted

**Files**
- `ui/main/fragments/HomeFragment.kt` L119–124
- `ui/details/DetailsActivity.kt` L93–98

**Issue**  
Identical 4-line function in both files.

**Refactor**  
Move to `SettingsManager` as an extension function or to the `WeatherBackgroundController` from item 1:
```kotlin
fun SettingsManager.isBackgroundAnimationEnabled(context: Context): Boolean = ...
```

---

## 7. WallpaperRepository wraps WallpaperImageStore — thin layer worth reviewing

**Files**
- `wallpaper/photo/WallpaperRepository.kt`
- `wallpaper/photo/WallpaperImageStore.kt`

**Issue**  
`WallpaperRepository` mainly delegates to `WallpaperImageStore` for `cachedPhotoPath` + wraps `BitmapFactory.decodeFile`. Several callers (`MaterialLiveWallpaperService`, `DetailsScreen`, `LiveWallpaperConfigActivity`) instantiate both independently. `WallpaperImageStore` is instantiated at least 5 times across the codebase without DI.

**Refactor**  
Inject `WallpaperRepository` via Hilt everywhere it's needed; remove direct `WallpaperImageStore` construction outside `WallpaperRepository` itself. This removes the need for callers to know about `cachedPhotoPath` directly.

---

## 8. WallpaperSnapshot singleton — thread-safety is @Volatile only

**File**  
`wallpaper/WallpaperSnapshot.kt`

**Issue**  
`@Volatile var bitmap` guarantees visibility but not atomicity of the write. A recycle-on-write strategy is explicitly avoided (comment: "GC handles them"). This is fine for now, but if a second writer is ever added the current pattern is fragile.

**Refactor** (low urgency)  
Replace with `AtomicReference<Bitmap?>` to make the swap atomic and self-documenting:
```kotlin
val bitmap = AtomicReference<Bitmap?>(null)
```

---

## Summary table

| # | Impact | Effort | Files touched |
|---|--------|--------|---------------|
| 1 | WeatherBackgroundController | High | HomeFragment, DetailsActivity + new class |
| 2 | CelestialRenderer | Medium | MaterialLiveWallpaperService, WallpaperSceneSnapshot, CelestialGlow |
| 3 | Split WeatherEngine god class | High | MaterialLiveWallpaperService + 2–3 new classes |
| 4 | WallpaperPhotoLayout | Low | WallpaperSceneSnapshot, MaterialLiveWallpaperService |
| 5 | Split DetailsScreen LaunchedEffect | Low | DetailsScreen only |
| 6 | isBackgroundAnimationEnabled() | Trivial | HomeFragment, DetailsActivity |
| 7 | Hilt for WallpaperRepository | Medium | 5+ files |
| 8 | AtomicReference in WallpaperSnapshot | Trivial | WallpaperSnapshot only |
