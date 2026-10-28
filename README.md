# Video Editing

A free, open-source, offline video editor for Android. Everything — trimming, overlays, audio mixing and final encoding — runs locally on the device with FFmpeg, with no accounts, no watermarks and no network access.

| | |
|---|---|
| **Application ID** | `com.videosequencer` |
| **Version** | `1.0-beta7` |
| **Min SDK** | 36 |
| **Target SDK** | 36 |
| **Compile SDK** | 37 |
| **Language / JDK** | Kotlin 2.4.21, Java 17 |
| **License** | MIT |

---

## Features

**Editing**
- **Trim** — remove unwanted parts from the start or end of a clip with a real-time timeline control.
- **Merge** — combine multiple clips into one continuous sequence with drag-to-rearrange.
- **Speed** — fine-grained speed control with a custom slider.
- **Reverse** — reverse video playback.
- **Crop** — custom cropping and aspect-ratio adjustment.
- **Transitions** — apply transitions with animated previews in the toolbar.
- **Adjust & Filters** — brightness, contrast, saturation and color filters.
- **Canvas Background** — blurred background or solid color for mismatched aspect ratios.
- **Freehand Drawing** — draw on top of the video with configurable brush color and stroke.
- **Snapshots** — save frame grabs directly from the editor.

**Overlays**
- **Text, stickers, images, GIFs and video overlays**, including continuous media looping.
- **Keyframes** — animate overlays across the screen.
- **Masking** — rectangle, ellipse, split, shutter, heart and star masks.
- **Chroma Key** — remove green-screen backgrounds from overlays.
- **Layer Management** — reorder overlays to control what renders on top.
- **Custom Fonts** — import `.ttf` / `.otf` files for text overlays.
- **Subtitles** — import `.srt` files with interactive, touch-based positioning and resizing.

**Audio**
- Import music or audio tracks, record voice-overs, apply ducking and fades, amplify up to 200%, or mute the original audio.
- **Audio Export** — render the full audio mix as a standalone MP3.

**Workflow**
- **Project Save & Import** — non-destructive projects stored as `.lcprj` files.
- **Proxy Generation** — background proxies keep heavy footage responsive while editing.
- **Screen Recording** — capture the screen from the app or from a Quick Settings tile.
- **Fullscreen Preview** — expanded timeline and overlay controls.
- **Adaptive Themed Icon** — monochrome icon for Android 13+ system themes.
- **Hardware-accelerated export** — `h264_mediacodec` encoding with automatic software fallback, plus accurate FFmpeg progress reporting.

## Tech Stack

- **ExoPlayer 2.19.1** — preview playback.
- **FFmpeg Kit** (full-gpl, 2.1.0) — rendering and media processing.
- **Jetpack** — AppCompat, ConstraintLayout, RecyclerView, Lifecycle/ViewModel, Core KTX.
- **Material Components 1.14.0**, **Lottie 6.7.1**, **Core Splashscreen 1.0.1**, **Gson 2.14.0**, **LocalBroadcastManager 1.1.0**.
- **Android Gradle Plugin 9.4.1**, **Gradle 9.8.0**.

## Requirements

- Android Studio (or a command-line Android SDK) with **SDK 36+** platform installed.
- **JDK 17**.
- A device or emulator running **Android 16 (API 36)** or newer.

The app declares no `INTERNET` permission — the network permissions injected by ExoPlayer are explicitly removed in the manifest.

## Build

Point Gradle at your SDK by creating `local.properties` in the project root (or by exporting `ANDROID_HOME`):

```properties
sdk.dir=/path/to/Android/Sdk
```

Then build with the bundled wrapper:

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (unsigned)
./gradlew :app:assembleRelease
```

APKs are written to `app/build/outputs/apk/<variant>/`.

The build produces **per-ABI APKs** (no universal APK):

| ABI | Version code offset |
|---|---|
| `armeabi-v7a` | +0 |
| `arm64-v8a` | +1 |
| `x86_64` | +2 |

The base version code defaults to `10` and can be overridden with `-PversionCode=<n>`.

## Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`, `READ_MEDIA_AUDIO` | Access media on Android 13+. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Partial media access. |
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | Legacy storage access (API ≤ 32 / ≤ 28). |
| `RECORD_AUDIO` | Voice-over recording. |
| `POST_NOTIFICATIONS` | Export and background progress notifications. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Export, proxy generation and screen recording services. |

## Project Structure

```
app/src/main/java/com/videosequencer/
├── MainActivity.kt, VideoEditingActivity.kt, ProjectImportActivity.kt, ErrorDisplayActivity.kt
├── commands/      Undo/redo edit commands
├── customviews/   Timeline, overlays, masks, crop and drawing views
├── models/        Project and edit-operation models
├── services/      FFmpeg export, proxy generation, screen recording
├── utils/         Audio analysis, subtitles, fonts, serialization, error codes
└── viewmodels/    Editor state holders
```

## Contributing

Issues and pull requests are welcome. Please keep changes focused, follow the existing Kotlin style, and make sure the project builds with `./gradlew :app:assembleDebug` before submitting.

## License

Released under the MIT License — see [LICENSE](LICENSE) for details.
