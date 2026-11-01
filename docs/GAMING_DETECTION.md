# Gaming-Erkennung

## Ziel
Jedes erkannte Spiel → **Gaming-Profil** automatisch.

## GRID Autosport
Paket: `com.feralinteractive.gridas` (Built-in)

## Erkennungssignale (OR)
1. Manuelle Nutzerliste  
2. Built-in-Allowlist  
3. `ApplicationInfo.FLAG_IS_GAME` / `CATEGORY_GAME`  
4. Heuristik (Paket/Label/Publisher)

## Dienste
- AccessibilityService: Vordergrund-App (auch Vollbild)
- GameRegistry: isGame() + autoRegisterInstalledGames()
- UsageStats: optionaler Test/Fallback
- EqService: Profil anwenden + RuntimeStatus/Notification
- BootReceiver: Service nach Neustart
- QS-Tile: EQ an/aus

## UI
- Spiele scannen  
- Spiel hinzufügen  
- Status: Spiel JA/NEIN + Grund + Profil GAMING
