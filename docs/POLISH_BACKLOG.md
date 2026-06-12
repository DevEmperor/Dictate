# Dictate Keyboard – Polish Backlog (Nutzer-Durchgang)

Systematisch abzuarbeitende Punkte nach dem ersten Genymotion-Test (Android 13).
Reihenfolge = grobe Priorität; wird Punkt für Punkt erledigt und hier abgehakt.

## Grundsatz-Entscheidungen (festgelegt)
- **Namenskonvention:** Launcher-Icon-Label kurz **„Dictate"**; im Fließtext / Setup / About /
  Theme-Namen der volle Produktname **„Dictate Keyboard"**.
- **Version:** weiter mit **4.0.0** (`projectVersionName=4.0.0`). versionCode muss > 30 sein
  (Altprojekt war 30; geerbter Floris-`projectVersionCode=119` erfüllt das bereits – ggf. auf
  einen sauberen Wert wie 40 setzen, aber niemals ≤ 30).
- **Akzentfarbe (Dictate-Hellblau):** `#29B6F6` (Material Light Blue 400), dunkle Variante
  `#1C7EAB`. Keyboard-BG: dark `#1b1b1d`, light `#f1f1f1`.
- **Icon-Quelle:** Altprojekt `/mnt/veracrypt3/Developement/Android/Dictate/app/src/main/res/`
  (`mipmap-*/ic_launcher*.webp`, `ic_launcher_foreground/background` adaptive XML).

## Offene Punkte

- [x] **1. Branding-Sweep (alles → „Dictate Keyboard")**
  Restliche sichtbare „FlorisBoard"/„Floris"-Vorkommen (u. a. im **Setup** gesehen) konsequent
  ersetzen. Vollständiger Sweep über *alle* Resourcen (strings, arrays, about, themes-Assets) und
  ggf. Code. Launcher-Label „Dictate", Produktname im Text „Dictate Keyboard".

- [x] **2. Theme-Namen auf Dictate umstellen**
  Mitgelieferte Theme-Extensions / Stylesheet-Namen („FlorisBoard Day/Night" o. ä.) auf
  Dictate-Namen umbenennen.

- [x] **3. Alte FlorisBoard-Diktierfunktion entfernen**
  Eingebautes Mic-/Voice-Input-Feature (`KeyCode.VOICE_INPUT` + Smartbar-Action + zugehörige
  Strings/Icons) komplett raus – wird durch unsere eigene Dictate-Funktion ersetzt. Achtung:
  unseren eigenen `IME_UI_MODE_DICTATE`-Mic NICHT anfassen.

- [x] **4. Akzentfarbe = Dictate-Hellblau `#29B6F6`**
  Default-Accent/Primary der App **und** der Tastatur-Themes auf das Dictate-Blau setzen.

- [x] **5. App-Name „Dictate" + altes Dictate-Icon übernehmen**
  `floris_app_name`/Label final auf „Dictate"; altes Dictate-Icon aus dem Altprojekt kopieren und
  überall einsetzen (`@mipmap/floris_app_icon[_round]`, Setup-/About-Icon, adaptive Icon).

- [x] **6. Mic-Permission im Onboarding abfragen**
  `RECORD_AUDIO` im Setup-Flow aktiv anfragen (eigener Step), damit beim Diktieren kein
  Permission-Fehler auftritt.

- [x] **7. Glide-/Swipe-Typing & Gesten entfernen**
  Glide-Typing und Gesten-Infrastruktur ausbauen (zu viel Overhead für den Start). Gestrichen
  laut Architektur-Entscheidung.

- [x] **8. About-Section + Version 4.0.0**
  Alles in About auf „Dictate Keyboard"; Version 4.0.0 setzen.

- [ ] **9. (Später) Privacy Policy**
  Alte Policy: https://www.iubenda.com/privacy-policy/66430035. Neue, eigene Policy erstellen und
  im GitHub-Repo (DevEmperor/Dictate) ablegen; danach `florisboard__privacy_policy_url` darauf
  zeigen lassen. Claude soll beim Erstellen helfen.

## Bereits erledigt (Schritt 7, vorher)
- URLs auf `github.com/DevEmperor/Dictate` umgestellt; Crash-/About-Selbstreferenzen entbrandet.
- Changelog/„What's new"-Dialog nach Update.
- Dictate-Strings nach `strings.xml` lokalisiert (englische Basis).

## Notizen zur Umsetzung (dieser Durchgang)
- **#1/#8**: `app_name`=„Dictate" in allen ~40 Locales; Fließtext „Dictate Keyboard" (auch Übersetzungen
  per sed). Apache-Lizenz-Header „The FlorisBoard Contributors" bewusst belassen (Attribution-Pflicht).
  Version `projectVersionName=4.0.0` (versionCode 119 > altes 30 → In-Place-Update OK).
- **#4**: Akzent über Pref-Defaults `other__accent_color` + `theme__accent_color` = `#29B6F6` (App nutzt
  dynamisches `neutralDynamicColorScheme`, kein statisches Color.kt).
- **#5**: Icon zentral über `ic_app_icon_foreground` (Webp aus Altprojekt), `ic_app_icon_background`
  `#4F576D`, Monochrome = Dictate-Mikro. Greift für Launcher, IME-Picker, About und Splash zugleich.
- **#7**: Glide-Typing war upstream bereits deaktiviert (default=false, Settings auskommentiert
  „currently not available"). Gesten-**Settings-Screen** komplett entfernt (Tile + Route + Datei).
  Kern-Touch-Erkennung (`SwipeGesture`: Tap/Long-Press/Backspace-halten/Cursor per Space-Swipe) bleibt,
  da ihr Löschen das Tippen zerstören würde. Falls gewünscht: einzelne Swipe-Defaults (z. B.
  swipeUp=Shift, swipeDown=Hide, swipeLeft/Right=Subtype) können noch auf NO_ACTION gesetzt werden.
