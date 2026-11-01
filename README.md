# Secret EQ

32-Band-Equalizer mit automatischer **Gaming-Erkennung** und Profilumschaltung.

## Spiele → Gaming-Profil
- System-Flag `FLAG_IS_GAME` / Kategorie Spiel  
- Built-in (GRID: `com.feralinteractive.gridas`, …)  
- Manuelle Liste + **Spiele scannen**  
- Heuristik für typische Spiele-Pakete  

## Build
```bash
gradle :app:assembleDebug
```
GitHub Actions → Artifact. ZIP am PC entpacken.

## Module
`accessibility/` · `profile/` (GameRegistry) · `system/` (Engine, Service, Tile, Boot) · `dsp/`
