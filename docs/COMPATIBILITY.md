# Secret EQ — Technische Analyse & ehrliche Grenzen

Zielgeräte: Samsung Galaxy S20 FE / S22+ · One UI · **kein Root, kein Shizuku, kein ADB**

## Warum GRID Autosport erkannt wird, der Klang aber unverändert bleibt

| Schritt | Ergebnis |
|---------|----------|
| Accessibility erkennt Paket | ✅ funktioniert |
| Gaming-Profil wird geladen | ✅ funktioniert |
| Session-0-Equalizer / DynamicsProcessing erzeugt | oft ✅ (Objekt existiert) |
| Audio des Spiels läuft durch diese Effektkette | ❌ häufig **nein** |

**Ursache:** Moderne Spiele (Unity, OpenSL ES, AAudio, HW-Offload) erzeugen eigene Audio-Sessions und senden oft **keinen** `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`. Viele Samsung-Builds wenden globale Session-0-Insert-Effekte auf solche Pfade nicht an. Hardware-Offload umgeht die Software-Effektkette komplett.

Das ist **kein Bug in Secret EQ**, sondern eine Android-/OEM-Architekturgrenze für normale Drittanbieter-Apps.

## Was implementiert ist (maximal ohne Root)

1. **DynamicsProcessing (API 28+)** auf Session 0 — bis zu 10 konfigurierbare Bänder (Pre/Post-EQ), bessere Abbildung der 32-Band-Kurve als der klassische 5-Band-Equalizer.
2. **Klassischer Equalizer + BassBoost + LoudnessEnhancer** als Fallback auf Session 0.
3. **Broadcast-Listener** für `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` — hängt Effekte an kooperative Player (manche Musik-Apps).
4. **AccessibilityService** nur für App-Erkennung und Profilumschaltung (kein Audio).
5. **Getrennte Statusanzeigen** A) Erkennung vs. B) Audioverarbeitung — kein Fake-„System-EQ aktiv“.

## Abbildung 32 → System-Bänder

- DynamicsProcessing: typisch 10 Bänder — 32-Band-Frequenzgang wird auf diese Center-Frequenzen projiziert.
- Classic Equalizer: typisch 5 Bänder — gleiche Projektion.
- Das ist **kein** bit-identischer 32-Band-PCM-DSP wie in Secret Player (ExoPlayer-AudioProcessor).

## Erwartete Wirkung pro Quelle (ohne Root)

| Quelle | App-Erkennung | Profil-Switch | Echte Klangänderung |
|--------|---------------|---------------|---------------------|
| System-Musikplayer / kooperative Player | ja | ja | oft möglich (Session-Broadcast oder Session 0) |
| Manche Browser | ja | ja | ungewiss |
| YouTube | ja | ja | oft **nicht** (eigene Pipeline) |
| GRID Autosport / Unity-Spiele | ja | ja | **meist nicht** |
| Streaming mit DRM / Offload | ja | ja | meist nicht |
| Bluetooth / Kabel / Lautsprecher | n/a | n/a | nur wenn der Mix die Effektkette durchläuft |

## Was Android einer normalen App verweigert

- Zuverlässiges Anhängen an beliebige fremde Audio-Sessions
- Erzwingen von Session-0-Effekten gegen OEM-Policy
- Abfangen + Echtzeit-DSP + Wiedergabe fremder App-Audio mit niedriger Latenz ohne Capture-Consent und ohne dass Spiele Capture blocken
- Hersteller-SoundAlive / Dolby steuern

**Root / Magisk-Module / Shizuku** können mehr — sind laut Auftrag **nicht erlaubt**.

## Konflikte (Samsung)

- Sound Assistant, UHQ-Upscaler, Dolby Atmos, andere EQ-Apps können Session 0 blockieren oder priorisieren.
- Empfehlung zum Test: andere Klang-Apps deaktivieren, UHQ auf „nur Bit-Upscaling“, Gerät neu starten.

## Absolutes Verbot in dieser App

- Keine Erfolgsmeldung „System-EQ aktiv“, nur weil ein Effekt-Objekt erzeugt wurde.
- Keine Behauptung, GRID würde verarbeitet, solange nur Erkennung + Profilwechsel laufen.
