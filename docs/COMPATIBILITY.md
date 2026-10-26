# Secret EQ — Kompatibilität & ehrliche Grenzen

## Was implementiert ist
- 32-Band-Datenmodell und Frequenzgang-Rechnung (Secret-Player-Mathe)
- Presets (Bass, Hardtekk, Druck, …)
- Persistenz
- Versuch: Android `Equalizer` + optional `BassBoost` / `LoudnessEnhancer` auf **Audio-Session 0**

## Was Session 0 bedeutet
Session 0 zielt auf den globalen Audio-Mix ab, **wenn** Hersteller und Android-Version das erlauben.
Viele Geräte (inkl. mancher Samsung-Builds) schränken globale AudioEffects ein.

## Mapping
Android-Equalizer hat typischerweise **5 Bänder**. Die 32-Band-Kurve wird auf diese Bänder projiziert.
Das ist **kein** bit-identischer 32-Band-PCM-DSP wie in Secret Player (ExoPlayer-AudioProcessor).

## Nicht behauptet / nicht getestet
| App / Pfad | Status |
|------------|--------|
| YouTube | **Noch nicht getestet** |
| YouTube Music | **Noch nicht getestet** |
| Browser | **Noch nicht getestet** |
| Bluetooth | **Noch nicht getestet** |
| Kabelfundhörer | **Noch nicht getestet** |

## Kein Root, kein Fake
Kein Root. Kein AudioPlaybackCapture als „System-EQ“. Kein Schalter, der Erfolg vortäuscht.

## Build
Siehe README. Referenz-Build ist der GitHub-Actions-Workflow `.github/workflows/build.yml`.
