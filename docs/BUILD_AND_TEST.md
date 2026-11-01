# Build- und Testbericht

## Build
```bash
gradle :app:assembleDebug
# oder GitHub Actions → Artifact SecretEQ-debug-apk
```

**Hinweis:** GitHub-Artifact-ZIPs manchmal unvollständig heruntergeladen → am PC entpacken, nur die APK aufs Gerät.

## Was sich in dieser Version geändert hat

- SystemEqEngine: DynamicsProcessing (10 Bänder) + Classic EQ + Session-Broadcast
- Ehrlicher ProcessStatus (effectCreated / effectEnabled / externalEffectProven)
- UI: getrennte Anzeige A) App-Erkennung und B) Audioverarbeitung
- GRID-Pakete in Gaming-Liste
- Dokumentation der Android-Grenze für Spiele

## Pflichttests auf dem Gerät

1. **System-Musikplayer** — starkes Preset vs. Flat. Wenn hier kein Unterschied hörbar ist, greift Session 0 auf dem Gerät nicht.
2. **GRID Autosport** — erwartet: erkannt + Gaming-Profil, Klang oft **unverändert** (dokumentiert).
3. **Brave / Chrome** — ungewiss.
4. **YouTube** — oft unverändert.
5. Andere EQ-Apps / Samsung SoundAlive aus, neu starten, erneut testen.

## Bewertungsschema

| Ergebnis | Bedeutung |
|----------|-----------|
| nachweislich funktionierend | Hörbarer Unterschied Flat vs. extremes Profil |
| implementiert, nicht getestet | Code vorhanden, Gerätetest ausstehend |
| aufgrund Android nicht möglich | Session/Offload blockiert — kein Fake |

Ein Profilwechsel in der Notification allein ist **kein** erfolgreicher Audio-Test.
