# Secret EQ

Eigenständiger Android-Equalizer, extrahiert aus dem 32-Band-EQ-System von Secret Player.

## Build

### GitHub Actions (empfohlen)
Pushen → Tab **Actions** → Workflow **Build APK**. Nach dem Lauf unter **Artifacts**
`SecretEQ-debug-apk` herunterladen (enthält `app-debug.apk`).
Manuell starten: Actions → Build APK → **Run workflow**.

Der Workflow installiert Gradle 8.5 + JDK 17 selbst, es wird **kein** `gradlew` benötigt.

### Lokal
Voraussetzungen: JDK 17, Android SDK (API 34), Gradle 8.5.

```bash
gradle :app:assembleDebug
```

Optional: `gradle wrapper --gradle-version 8.5` erzeugt `gradlew` (dann die Wrapper-Dateien mit einchecken).

## Module
- `dsp/` — 32 Bänder, Biquad-Frequenzgang, Presets
- `system/` — Session-0 AudioEffect-Engine + Foreground-Service
- `data/` — SharedPreferences Persistenz
- UI — Compose, dunkles Lila (`MainActivity.kt`)

## Wichtig
Systemweite Wirkung ist **geräteabhängig** und hier **nicht auf dem S20 FE nachgewiesen**.
Siehe `docs/COMPATIBILITY.md`.
