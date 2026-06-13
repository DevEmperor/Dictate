# Dictate – Porting Roadmap (Alt → Neu)

Stand: 2026-06-12. Abgleich des alten Projekts (`/Android/Dictate`, Java, eigener
Mini-Keyboard-Service) mit dem neuen FlorisBoard-Fork (`/Android/DictateKeyboard`, Kotlin).

Legende: ✅ fertig · 🟡 teilweise · 🔲 offen · ❌ Empfehlung: weglassen

---

## 0. Aktueller Stand neu (Kurzfassung)
Im neuen Projekt funktioniert bisher nur der **Basis-Diktierflow**:
Mic-Button (Smartbar) → aufnehmen → nochmal tippen → transkribieren → Text einfügen.
Provider/Key/Model kommen aus `prefs.dictate` (einmalige Migration aus den alten
SharedPreferences). Die DB-Helfer für **Prompts** und **Usage** sind als Code portiert,
aber **nirgends verdrahtet** (keine UI, kein Flow). Alles rund um **Rewording, Sprachen,
Usage-Anzeige, Aufnahme-Komfort** fehlt noch.

---

## 1. Transkription & Aufnahme (Core)

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 1.1 | Tap-Record → Tap-Send → Commit | ✅ | `DictateController` + `RecordingController` |
| 1.2 | Aufnahme-Timer (mm:ss) | ✅ | neue Smartbar-UI |
| 1.3 | Puls-Animation beim Aufnehmen | 🟡 | aktuell roter Punkt; alt war pulsierender Mic-Button |
| 1.4 | Transkribier-Animation | 🟡 | drehendes Sync-Icon; ggf. schöner machen |
| 1.5 | **Pause/Resume** der Aufnahme | ✅ | Pause-Button rechts in der Smartbar; Timer friert ein |
| 1.6 | **Abbrechen/Trash** der Aufnahme | ✅ | Trash-Button links in der Smartbar (`cancelRecording`) |
| 1.7 | **Audio-Focus** (Hintergrund-Audio pausieren) | ✅ | Pref `dictate__audio_focus` (default an); Fokusverlust → Pause |
| 1.8 | **Bluetooth-Mikrofon (SCO)** inkl. Warte-/Timeout-Logik | ✅ | `BluetoothMicRouter`; Pref `dictate__use_bluetooth_mic` (API31+ setCommunicationDevice, 26–30 SCO+Broadcast) |
| 1.9 | **Bildschirm wachhalten** während Aufnahme | ✅ | `view.keepScreenOn` während Recording; Pref `dictate__keep_screen_awake` (default an) |
| 1.10 | **Instant Recording** (Aufnahme startet beim Öffnen) | ✅ | Hook in `onStartInputView`; Pref `dictate__instant_recording` (default aus) |
| 1.11 | **Retry-Logik** (3× mit 3s Delay bei transienten Fehlern) | ✅ | im `OpenAiCompatibleClient` vorhanden; jetzt mit Indikator (CloudOff + „Versuch n") |
| 1.12 | **Spezifische Fehlermeldungen** (API-Key, Quota, Größe, Format, Timeout, Netz) | 🟡 | `DictateApiException.Kind` klassifiziert bereits; eigene Texte je Kind noch offen |
| 1.13 | Aufnahme-Format AAC/m4a 64kbps/44.1kHz | ✅ | im `RecordingController` prüfen, ob identisch |

---

## 2. Eingabesprachen

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 2.1 | **Multi-Select Eingabesprachen** (~55 Sprachen) | ✅ | `DictateLanguages` + eigener Settings-Subscreen (`DictateLanguagesScreen`), gespeichert als CSV in `prefs.dictate.inputLanguages` |
| 2.2 | **Sprache umschalten** (durch ausgewählte cyclen) | ✅ | Sprach-Chip in der Aufnahmeleiste: Tippen = cyclen, Langdruck = Popup; `activeInputLanguage` persistiert |
| 2.3 | Sprache am Button anzeigen | ✅ | Chip zeigt Kürzel (DE/EN) bzw. Globus für „Auto"; bewusst **keine Flaggen** (Android rendert sie nicht zuverlässig, Sprache≠Land) |
| 2.4 | **Style-Prompt pro Sprache** (Punktuation/Großschreibung) | 🔲 | `getPunctuationPromptForLanguage`, 55 Sprachen — bewusst ausgeklammert, kommt später |

> 2.1–2.3 portiert. `language` geht jetzt an Whisper (Auto-Detect = nicht gesendet). Der Sprach-Chip
> erscheint nur **während der Aufnahme** (links neben Pause), damit das Keyboard sonst nicht überladen wird.
> 2.4 (Style-Prompt) bleibt für später.

> Nebenbei in diesem Block erledigt: **Akzentfarbe entkoppelt** — die Standard-Keyboard-Themes hatten
> `--primary` fix auf `#30b7e6`; jetzt folgen Enter-Key, Mic & Akzentelemente `prefs.theme.accentColor`
> (Default Hellblau) via `dynamic-light/dark-color(...)`. Dark-Mode-Rollen (primary/inversePrimary)
> korrigiert. Das Mic ist jetzt ein **FAB** (Akzent-Kreis, `var(--primary)`/`var(--on-primary)`, 6dp Margin).

---

## 3. Datei-Transkription

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 3.1 | **Audio/Video-Datei transkribieren** (File-Picker) | ✅ | **Long-Press auf das Mic** (nur im Idle) öffnet `FileTranscriptionActivity` (Trampolin, da der IME nicht selbst picken kann) → `ACTION_OPEN_DOCUMENT` (`audio/*`,`video/*`) → kopiert in `cacheDir/dictate_pending/`. `DictateController.consumePendingFileTranscription` (aus `onStartInputView` **und** `onWindowShown`) claimt die Datei und fügt den Text ins Feld ein (geteilte `transcribe()`-Pipeline, respektiert die aktive Sprache) |
| 3.2 | 25-MB-Größenlimit-Check | ✅ | Provider-abhängig: OpenAI/Groq 25 MB (Client-Vorprüfung mit Toast), `custom` kein Client-Limit (Server entscheidet). 25 MB bleibt der korrekte kleinste gemeinsame Nenner; bei `gpt-4o-transcribe` ist oft die **Dauer** (~25 Min) die bindende Grenze, die serverseitig via `DictateApiException` gemeldet wird |

> **Block-Notiz (2026-06-13, Abschnitt 3):** Datei-Transkription per Long-Press-Mic umgesetzt und auf
> physischem Gerät (Samsung A55) getestet. `stopAndTranscribe` refaktoriert: gemeinsame
> `transcribe(context, file)`-Pipeline für Aufnahme **und** Datei. Long-Press im `QuickActionButton` via
> `AwaitPointerEventScope.withTimeout` (restricted-scope-kompatibel, **nicht** `withTimeoutOrNull`).
> **Handoff dateibasiert** statt über eine Pref: der IME-Prozess wird gekillt, während der Picker im
> Vordergrund ist – nur eine Cache-Datei überlebt das. Datei landet in `cacheDir/dictate_pending/`, wird
> beim Aufgreifen *geclaimt* (herausverschoben) → kein Doppel-Trigger, idempotent über beide Hooks.
> **Wichtige Falle:** `FileTranscriptionActivity` darf **kein** `android:noHistory` haben – sonst wird sie
> zerstört, sobald der Picker erscheint, und das `OpenDocument`-Result kommt nie an. Sprach-Chip nutzt
> jetzt `SnyggIconButton` (gleiche Ripple-Animation wie Cancel/Pause).

---

## 4. Rewording / GPT  ⭐ (großes Feature)

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 4.1 | Rewording an/aus | 🔲 | Pref `rewording_enabled` |
| 4.2 | **Eigene Prompts (CRUD)** | 🟡 | `PromptsDatabaseHelper` portiert, **nicht verdrahtet**; keine Edit/Overview-UI |
| 4.3 | **Prompt-Leiste im Keyboard** (horizontale Grid) | 🔲 | alt: `PromptsKeyboardAdapter` |
| 4.4 | Prompt „benötigt Auswahl" vs. frei | 🔲 | `requiresSelection` |
| 4.5 | **Auto-Apply-Prompts** (nach Transkription automatisch) | 🔲 | `getAutoApplyIds` |
| 4.6 | **Prompt-Queue** (mehrere Prompts während Aufnahme verketten) | 🔲 | `queuedPromptIds` |
| 4.7 | **Live/Instant-Prompt** (aufnehmen → direkt an GPT) | 🔲 | id `-1` |
| 4.8 | Add/Edit-Prompt-Screens | 🔲 | alt: `PromptEditActivity`, `PromptsOverviewActivity` |
| 4.9 | **Auto-Formatting** (gesprochene Formatierbefehle → Markdown) | 🔲 | `AUTO_FORMATTING_PROMPT`, Pref `auto_formatting_enabled` |
| 4.10 | **System-Prompt** Rewording (nichts/predefined/custom) | 🔲 | `system_prompt_selection` |
| 4.11 | **Style-Prompt** Transkription (nichts/predefined/custom) | 🔲 | `style_prompt_selection` (siehe 2.4) |
| 4.12 | „Alles auswählen"-Toggle als Prompt | 🟡 | jetzt als Quick-Action `CLIPBOARD_SELECT_ALL` abgedeckt |

> Das ist der größte Brocken. Reihenfolge-Vorschlag: erst API/Provider (Abschnitt 5)
> sauber zweigleisig, dann Prompt-Datenmodell + Settings-UI, dann Keyboard-Leiste,
> dann Queue/Auto-Apply/Live, zuletzt Auto-Formatting.

---

## 5. Provider / API-Konfiguration

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 5.1 | Provider-Presets | ✅+ | neu **deutlich mehr**: OpenAI, Groq, OpenRouter, Together, DeepInfra, Mistral, xAI, DeepSeek, Ollama, Custom |
| 5.2 | Custom Host + Custom Model | ✅ | `customBaseUrl` |
| 5.3 | **Getrennte Provider für Transkription & Rewording** | 🔲 | neu nur **ein** Provider |
| 5.4 | **Getrennte API-Keys pro Provider** (openai/groq/custom …) | 🔲 | neu nur **ein** `apiKey` |
| 5.5 | **Model-Auswahl per Dropdown** (bekannte Modelle je Provider) | 🟡 | neu nur Freitext-Modellfeld; `ProviderModels` existiert |
| 5.6 | **Proxy** (http/socks5 mit Auth) | 🔲 | Pref `proxy_enabled`/`proxy_host`, `applyProxy` |

> Entscheidung nötig: Brauchst du im neuen Konzept weiterhin **zwei getrennte**
> Provider/Keys (Transkription vs. Rewording)? Das verdoppelt die Settings-Komplexität.

---

## 6. Usage / Kosten-Tracking

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 6.1 | Usage-DB (Audiozeit, Tokens, Kosten je Modell/Provider) | 🟡 | `UsageDatabaseHelper` portiert, **nicht befüllt** |
| 6.2 | Preis-Berechnung je Modell | 🟡 | `DictatePricing` portiert, nicht genutzt |
| 6.3 | **Usage-Screen** (Liste + geschätzte Kosten) | 🔲 | alt: `UsageActivity` |
| 6.4 | Kosten-Summary in Settings | 🔲 | „Estimated cost: X $" |

> Hängt davon ab, ob du Kosten-Transparenz weiter anbieten willst. Preistabellen
> veralten schnell (Pflegeaufwand).

---

## 7. Tastatur-Aktionen / Editing

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 7.1 | Backspace mit Hold-to-repeat (Beschleunigung) | ✅ | FlorisBoard nativ |
| 7.2 | Space-Swipe = Cursor bewegen | ✅ | FlorisBoard nativ |
| 7.3 | Undo/Redo/Cut/Copy/Paste | ✅ | als Smartbar-Quick-Actions (Reihenfolge gesetzt) |
| 7.4 | Emoji-Picker | ✅ | FlorisBoard nativ |
| 7.5 | Zahlen-Panel (autom. bei Zahlenfeldern) | ✅ | FlorisBoard nativ (numerische Layouts) |
| 7.6 | Kontext-Enter-Icon (send/search/done/newline) | ✅ | FlorisBoard nativ |
| 7.7 | Keyboard zurückwechseln | ✅ | „switch back" implementiert |
| 7.8 | **Swipe-Backspace = Wörter markieren** | ❌ | Custom-Geste; FlorisBoard löst Selektion anders → weglassen |
| 7.9 | **Overlay-Zeichen** auf Enter-Long-Press (8 Zeichen) | ❌ | FlorisBoard hat native Popup-Keys → weglassen, Pref `overlay_characters` entfällt |

---

## 8. Theming / Erscheinungsbild

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 8.1 | Theme system/light/dark | ✅ | FlorisBoard nativ (viel mächtiger) |
| 8.2 | Akzentfarbe | ✅ | `accentColor` #30B7E6 + Theme-Stylesheets |
| 8.3 | Animationen an/aus | ✅ | FlorisBoard nativ |
| 8.4 | App-Sprache (In-App-Locale) | ✅ | FlorisBoard nativ |

---

## 9. Onboarding & Info

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 9.1 | Onboarding (Welcome/Permission/API-Key) | ✅ | Setup-Screens: EnableIme → SelectIme → GrantMic → Notification → FinishUp |
| 9.2 | Update-Hinweis | ✅ | `ChangelogDialog` (alt: im Keyboard) |
| 9.3 | **How-To-Guide** | 🔲 | alt: `HowToActivity`; ggf. als docs/Wiki statt In-App |
| 9.4 | Feedback-E-Mail | 🟡 | FlorisBoard About prüfen |
| 9.5 | GitHub-Link | ✅ | Strings rebranded |
| 9.6 | Cache leeren | 🔲 | minor |
| 9.7 | **Rate-App-Prompt** (nach 3 min) | ❌ | weglassen / später |
| 9.8 | **Donate-Prompt** (nach 10 min, PayPal) | ❌ | weglassen / später |
| 9.9 | **Firebase Crashlytics** + zufällige User-ID | ❌ | FlorisBoard hat eigenes Crash-Handling → Firebase weglassen |

---

## 10. Ausgabe-Verhalten / Sonstiges

| # | Feature | Status | Anmerkung |
|---|---------|--------|-----------|
| 10.1 | **Auto-Enter** nach Transkription | 🔲 | Pref `auto_enter` |
| 10.2 | **Instant-Output vs. getipptes Ausgeben + Geschwindigkeit** (Schreibmaschine) | 🔲 | Pref `instant_output`/`output_speed` |
| 10.3 | **Resend-Button** (letzte Audio erneut senden) | 🔲 | Pref `resend_button` |
| 10.4 | **Auto-Keyboard-Wechsel** nach Transkription (Long-Press Record) | 🔲 | `autoSwitchKeyboard` |
| 10.5 | Haptik/Vibration bei Tasten | ✅ | FlorisBoard nativ |

---

## Empfohlene Reihenfolge (Vorschlag)

1. **Aufnahme-Komfort** (1.5 Pause, 1.6 Abbrechen-UI, 1.7 Audio-Focus, 1.9 Wachhalten,
   1.12 Fehlermeldungen, 1.11 Retry) — kleine, hochwertige Verbesserungen am bestehenden Flow.
2. **Eingabesprachen + Style-Prompt** (Abschnitt 2) — großer Qualitätssprung bei Erkennung.
3. **Provider zweigleisig + Model-Dropdowns + Proxy** (Abschnitt 5) — Fundament für Rewording.
4. **Rewording-System** (Abschnitt 4) — größter Block, in Teilschritten.
5. **Usage-Anzeige** (Abschnitt 6) — optional, je nach Wunsch.
6. **Datei-Transkription** (Abschnitt 3), **Ausgabe-Optionen** (10.1–10.4), **How-To** (9.3).

## Weglassen (überflüssig im neuen Konzept)
- 7.8 Swipe-Backspace-Wortselektion, 7.9 Overlay-Zeichen (FlorisBoard kann beides nativ besser)
- 9.7 Rate-Prompt, 9.8 Donate-Prompt, 9.9 Firebase Crashlytics + User-ID
