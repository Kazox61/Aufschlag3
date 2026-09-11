# KazUI

A Compose Multiplatform design system (Android / Desktop / iOS), originally derived from
[RikkaUI](https://github.com/rainxch/RikkaUI) and adapted into `com.kazox.ui`.

## Reusing this in another project

The package root is `com.kazox.ui` — deliberately **not** tied to any single app.
That is the whole point: you never rename anything when moving it.

To drop it into a new Compose Multiplatform project:

1. Copy the entire `kazui/` directory into the new project's root.
2. Add to `settings.gradle.kts`:
   ```kotlin
   include(":kazui")
   ```
3. Add to the consuming module's `build.gradle.kts`:
   ```kotlin
   commonMain.dependencies {
       implementation(projects.kazui)   // or implementation(project(":kazui"))
   }
   ```
4. Make sure the target project's `gradle/libs.versions.toml` defines the version-catalog
   keys `kazui/build.gradle.kts` references:
   - versions: `kotlin`, `composeMultiplatform`, `agp`, `android-compileSdk`, `android-minSdk`
   - plugins: `kotlinMultiplatform`, `androidMultiplatformLibrary`, `composeMultiplatform`, `composeCompiler`
   - libraries: `compose-runtime`, `compose-foundation`, `compose-ui`, `compose-components-resources`

   (If the new project has no version catalog, replace the `libs.*` references in
   `kazui/build.gradle.kts` with literal coordinates.)

Adjust the `iosArm64()` / `iosSimulatorArm64()` / `jvm()` targets in `kazui/build.gradle.kts`
to whatever the new project actually builds for.

### If you get tired of copying

Publish it once instead of copying it:

```kotlin
// kazui/build.gradle.kts
plugins { `maven-publish` }
group = "com.kazox"
version = "0.1.0"
publishing { repositories { mavenLocal() } }
```

Then `./gradlew :kazui:publishToMavenLocal` and depend on `com.kazox:kazui:0.1.0` from any
project on the machine. For sharing across machines, publish to GitHub Packages instead.

## Usage

```kotlin
KazTheme(
    palette = KazPalette.Zinc,
    accent = KazAccentPreset.Default,
    isDark = false,
    preset = KazStylePreset.Default,
) {
    Button(onClick = { }) { Text("Hello") }
}
```

Design tokens are read through `KazTheme.colors`, `KazTheme.typography`, `KazTheme.spacing`,
`KazTheme.shapes`, `KazTheme.motion`, `KazTheme.elevation`.

Custom fonts:

```kotlin
val family = rememberKazFontFamily(
    light = Res.font.inter_light,
    regular = Res.font.inter_regular,
    medium = Res.font.inter_medium,
    semiBold = Res.font.inter_semibold,
    bold = Res.font.inter_bold,
    extraBold = Res.font.inter_extrabold,
)
KazTheme(typography = kazTypography(family)) { /* ... */ }
```

## Structure

```
com.kazox.ui
├── foundation/            KazTheme, KazColors, KazTypography, KazSpacing,
│   │                      KazShapes, KazMotion, KazElevation, KazStyle, ColorScheme
│   └── modifier/          focusRing, minTouchTarget, keyboardScrollable, staggeredEnter
└── components/            41 components (button, card, dialog, input, toast, …)
```

## Notes

- The module is built with `explicitApi()`; every public declaration needs an explicit
  visibility modifier and return type.
- Compose dependencies are exposed via `api(...)` because the public surface returns
  Compose types (`Modifier`, `Color`, `ImageVector`, `TextStyle`).
- `KazIcons` ships 30 hand-built Lucide-style vectors. For a fuller icon set you would
  need a separate icon library.
