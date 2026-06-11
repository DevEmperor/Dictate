# Architektur & Integrationsplan: Dictate auf FlorisBoard-Basis

Analyse-Stand: FlorisBoard `main`, flach geklont nach `reference/florisboard/` (Apache-2.0).

## 1. Was FlorisBoard mitbringt (und wir übernehmen)

FlorisBoard ist eine moderne, **Kotlin + Jetpack-Compose** Multi-Modul-IME:

```
:app                      App + IME + Compose-Settings
:lib:android              Android-Hilfen
:lib:color                Farb-Utilities
:lib:compose              Compose-Bausteine
:lib:kotlin               reine Kotlin-Utilities
:lib:native              native Anteile (NLP / Performance)
:lib:snygg                Theming-/Styling-Engine ("Snygg")
```

Damit bekommst du **geschenkt**, was du nicht selbst bauen willst:
- Vollwertige Tipp-Tastatur mit **Layouts pro Sprache** (Subtypes), Symbol-/Zahlen-Ebenen, Popups, Akzente
- **Smartbar** (Vorschlagsleiste) + **QuickActions** (Gboard-artige Aktionsbuttons)
- **NLP** (`ime/nlp/latin`, `ime/nlp/han`) für Wortvorschläge
- **Mehrsprachen-Umschaltung** (Subtypes/Localization)
- **Emoji/Media-Panel**, **Clipboard-Manager**, **Gesten**
- **Theming-Engine** (Snygg) + Compose-Settings-Gerüst
- Crash-Utility, Setup-Flow, Spellchecker-Service

> Hinweis Glide/Swipe-Typing: Gesten-Infrastruktur ist vorhanden (`ime/text/gestures`),
> die Reife des Wort-Gliding ist separat zu evaluieren (`feat/add-latinime-word-prediction`-Branch
> zeigt aktive Arbeit an LatinIME-Wortvorhersage). Vor Release prüfen.

## 2. Die exakten Integrationspunkte für den Dictate-Teil

### 2a. Neuer IME-UI-Modus `DICTATE`
FlorisBoard schaltet seine Panels über einen Enum-Zustand:

- `ime/ImeUiMode.kt` → `enum { TEXT(0), MEDIA(1), CLIPBOARD(2) }`
- `ime/window/ImeWindow.kt:227–229` rendert je Modus ein Compose-Layout:
  ```kotlin
  ImeUiMode.TEXT      -> TextInputLayout()
  ImeUiMode.MEDIA     -> MediaInputLayout()
  ImeUiMode.CLIPBOARD -> ClipboardInputLayout()
  ```

**→ Integration:** `DICTATE(3)` ergänzen und in `ImeWindow` ein `DictateInputLayout()` rendern.
Das ist unser Diktat-/Rewording-Panel (Record-Button, Timer, Prompt-Leiste, Live-Prompt).
Es lebt sauber neben dem Tipp-Modus, statt ihn zu ersetzen.

### 2b. Mikro-Button als QuickAction (Smartbar)
`ime/smartbar/quickaction/` enthält das Aktionssystem (`QuickAction.kt`,
`QuickActionArrangement.kt`, `QuickActionButton.kt`, …). Hier registrieren wir eine
**Dictate-QuickAction** (Mikro-Icon), die `activeState.imeUiMode = DICTATE` setzt.
So erreicht der Nutzer das Diktat Gboard-typisch aus der normalen Tastatur.

### 2c. Eigenes Onboarding (gewünscht)
Das bestehende Setup ist minimal:
- `app/setup/SetupScreen.kt`
- `app/setup/NotificationPermissionState.kt`

**→ Wir ersetzen `SetupScreen` durch ein eigenes Dictate-Onboarding** (Berechtigungen,
Tastatur aktivieren, Provider/API-Key-Setup, Markenauftritt). Die Permission-State-Helfer
können wir weiterverwenden oder ersetzen.

### 2d. Einstellungen
Settings sind Compose + **JetPref-DataStore** (`app/AppPrefs.kt`, ~35k, `florisPreferenceModel`).
Unsere Dictate-spezifischen Settings (Provider, Prompts, Usage, System-Prompts) kommen als
**eigene Settings-Screens/-Prefs-Sektion** dazu. Die Routen liegen in `app/Routes.kt`.

## 3. Portierung der bestehenden Dictate-Logik (Java → Kotlin)

Aus dem alten Monolithen `DictateInputMethodService` (~2270 Z.) werden saubere Module:

| Alt (Dictate 3.2.0) | Neu (Modul) |
|---|---|
| Aufnahme + MediaRecorder + Bluetooth-SCO | `dictate/audio/RecordingController` |
| Whisper-/Transkriptions-Call | `dictate/provider/TranscriptionService` |
| GPT-/Rewording-Call + Queue + Auto-Apply | `dictate/provider/RewordingService` |
| Provider-Auswahl (heute `switch 0/1/2`) | `dictate/provider/ProviderRegistry` (s. u.) |
| `prompts.db` (SQLite) | Room-Entity `PromptEntity` + DAO |
| `usage.db` (SQLite) | Room-Entity `UsageEntity` + DAO |
| SharedPreferences | JetPref-Prefs + einmalige Migration (s. COMPATIBILITY.md) |
| Diktat-/Prompt-UI (XML) | `DictateInputLayout()` (Compose) |
| Onboarding (ViewPager) | eigenes Compose-Onboarding |
| How-To / Usage / Settings Activities | Compose-Screens in der Florisboard-Settings-Navigation |

## 4. Provider-Erweiterung (mehr Anbieter, freie Wahl)

Statt fest verdrahteter Indizes:

```kotlin
interface LlmProvider {           // Rewording / Chat
    suspend fun complete(req: ChatRequest): ChatResult
}
interface TranscriptionProvider { // Speech-to-Text
    suspend fun transcribe(req: TranscriptionRequest): TranscriptionResult
}

// Preset = { id, name, baseUrl, authType, modelCatalog, caps }
object ProviderRegistry {
    val presets: List<ProviderPreset>   // OpenAI, Groq, OpenRouter, Together,
                                        // DeepInfra, Mistral, xAI, DeepSeek, Ollama (lokal) …
    val customProviders: ...            // beliebig viele eigene OpenAI-kompatible Endpunkte
}
```

- **Ein OpenAI-kompatibler Adapter** deckt die große Mehrheit ab (nur Base-URL + Modellliste + Key).
- **Spezial-Adapter** nur für abweichende APIs (z. B. Anthropic, Gemini; STT: Deepgram/AssemblyAI/ElevenLabs).
- Konkrete Modell-IDs/Preise werden beim Hinterlegen jeweils aktuell recherchiert, nicht hartkodiert geraten.

## 5. Build-/Branding-Anpassungen (Pflicht für eigenständige App)

- `applicationId` → `net.devemperor.dictate` (Datenvertrag!), Keystore = bisheriger Play-Store-Key.
- **Eigener App-Name, Icon, Branding** — Name/Logo „FlorisBoard" sind **nicht** mitlizenziert.
- Apache-2.0-Pflichten: Lizenzkopie + `NOTICE`/Attribution behalten, wesentliche Änderungen kennzeichnen.
- **Sub-Abhängigkeiten prüfen** (`reference/florisboard` Dependencies/`lib:native`/Wörterbücher) auf
  abweichende Lizenzen, bevor kommerziell veröffentlicht wird. (Keine Rechtsberatung.)

## 6. Empfohlene Umsetzungsreihenfolge

1. **Fundament:** Fork als Projektbasis übernehmen, Build grün bekommen, Branding/`applicationId` setzen.
2. **Datenschicht + Migration:** Room-Entities (prompts/usage) + Prefs-Migration **mit Alt-Fixtures-Tests**.
3. **Provider-Schicht:** Registry + OpenAI-kompatibler Adapter + 2–3 Presets, isoliert testbar.
4. **Dictate-Panel:** `ImeUiMode.DICTATE` + `DictateInputLayout()` + Mikro-QuickAction.
5. **Logik-Port:** Recording/Transcription/Rewording (inkl. Queue, Auto-Apply, Live-Prompt).
6. **Onboarding + Settings-Screens** (eigenes Branding).
7. **Feinschliff:** Themes, Changelog/Update-Flow. (Glide-Typing wird **nicht** eingebaut.)

## 7. Entscheidungen & Umsetzungsstand

### Entscheidung: Alt-DBs vorerst als raw SQLite (kein Room)
FlorisBoard bringt Room (2.8.4) mit, doch die **bestehenden** Dictate-DBs (`prompts.db`, `usage.db`)
deklarieren Spalten als `BOOLEAN`/`LONG` (SQLite-Affinität NUMERIC). Room erwartet `INTEGER` und
**validiert das Schema strikt über Typ-Affinitäten** – es würde die vorhandene Nutzer-DB ablehnen.
Daher werden beide DBs zunächst als `SQLiteOpenHelper` in Kotlin 1:1 portiert (gleicher Name, gleiche
Version 2, gleiches Schema) → **null Risiko** für Bestandsdaten. Ein späterer Umstieg auf Room ist
über eine bewusste `MIGRATION_2_3` möglich (neue Tabellen im Room-Format anlegen, Daten kopieren),
**erst mit Instrumented-Migrationstest** auf echter Alt-DB. Bis dahin kapseln Helper-Klassen die DBs.

### Glide-Typing: gestrichen
Auf Wunsch wird Glide-/Swipe-Typing **nicht** integriert. Vorhandene Gesten-Infrastruktur von
FlorisBoard kann später entschlackt werden.

### Entscheidung: `lib:native` (Rust) entfernt
FlorisBoards `:lib:native` ist in `main` nur ein **Dummy-Gerüst** (`lib.rs` exportiert einzig
`dummyAdd → dummy::addnumbers`, einziger Konsument war ein `flogError`-Smoke-Test in
`FlorisApplication`). Es ist Vorarbeit für künftige NLP-/Wortvorhersage, die in `main` noch nicht
aktiv ist. Da Dictate es nicht braucht (und Glide/Vorhersage gestrichen sind) **und** sein CMake
zwingend eine `rustup`-Toolchain + Android-Rust-Targets verlangt, wurde das Modul **komplett
entfernt**: gelöscht `lib/native/` + `libnative/` (Dummy-Crate), entfernt `include(":lib:native")`
(settings.gradle.kts), `implementation(projects.lib.native)` (app/build.gradle.kts) sowie die 3
Referenzen in `FlorisApplication.kt` (Import, `System.loadLibrary("fl_native")`, `dummyAdd`-Log).
**Folge: voller APK-Build ohne jede Rust-Toolchain.** Falls künftig Upstream-NLP gewünscht wird,
holt man das Modul bewusst aus FlorisBoard zurück. Reversibel via git.

### Umsetzungsstand
- [x] **Schritt 1** – Fork als Basis, `applicationId = net.devemperor.dictate` (Release ohne Suffix; Debug `.debug`), App-Name. **Voller Debug-APK-Build grün** (JDK 17, 112 Tasks, 32 MB `app-debug.apk`, 0 Fehler) nach Entfernen des Rust-Dummy-Moduls `:lib:native` (s. Entscheidung oben). Damit baut das Projekt **ohne Rust-Toolchain**.
- [~] **Schritt 2** – Datenschicht (Kotlin):
  - [x] `dictate/data/prompts` – `PromptModel`, `PromptsDatabaseHelper`
  - [x] `dictate/data/usage` – `UsageModel`, `UsageDatabaseHelper`, `DictatePricing`
  - [x] `dictate/data/prefs` – `DictateLegacyPreferences`, `DictateLegacySettings` (Reader der Alt-Prefs)
  - [ ] Instrumented-Migrationstests mit Alt-Fixtures (sobald Build/CMake läuft)
  - [ ] Einmaliger Prefs-Import in die neue (JetPref-)Settings-Schicht (Teil von Schritt 6)
- [x] **Schritt 3** – Provider-Schicht (`dictate/provider/`, reines Kotlin):
  - [x] Domänenmodelle (`ProviderModels`), Interfaces (`Providers`: `LlmProvider`/`TranscriptionProvider`)
  - [x] `OpenAiCompatibleClient` (OkHttp + kotlinx.serialization): Chat, Transkription (Multipart), `listModels`, Retry, Proxy, Fehler-Klassifizierung (`DictateApiException`)
  - [x] `ProviderConfig` + `ProxyConfig` (Proxy-Parsing aus `DictateUtils` portiert)
  - [x] `ProviderRegistry` mit Presets: OpenAI, Groq, **OpenRouter**, Together, DeepInfra, Mistral, xAI, DeepSeek, Ollama (lokal) + Custom-Factory
  - [x] OkHttp 4.12.0 zu Version-Catalog + App-Deps hinzugefügt
  - [ ] Optionale Spezial-Adapter für native Anthropic-/Gemini-APIs (vorerst via OpenRouter erreichbar)
  - [ ] Legacy-Provider-Index → Preset-Id-Mapping in der Settings-Migration (Schritt 6): `0→openai`, `1→groq`, `2→custom`
  - [ ] Unit-Tests (Proxy-Parsing, Fehler-Klassifizierung) + Live-Smoke-Test (sobald Build läuft)
- [x] **Schritt 4** – Dictate als eigener IME-Modus (Build grün, inkrementell 2m):
  - [x] `KeyCode.IME_UI_MODE_DICTATE = -214` + `TextKeyData.IME_UI_MODE_DICTATE` (SYSTEM_GUI) + interne-Codes-Liste
  - [x] `ImeUiMode.DICTATE(3)`; `KeyboardManager.handleKeyCode` schaltet darauf; `ImeWindow` rendert `DictateInputLayout()`
  - [x] `dictate/ui/DictateInputLayout.kt` – UI-Gerüst (Zurück-zu-TEXT-Button, Mikro-Toggle mit Status; Aufnahmelogik = Schritt 5). Nutzt vorerst die gestylten `media-*`-Snygg-Elemente.
  - [x] Mikro-**QuickAction**: Icon (`Icons.Default.Mic` in `ComputingEvaluator`), Display-Name/Tooltip + Strings (`quick_action__ime_ui_mode_dictate[__tooltip]`), und als **Sticky-Action** der Smartbar (`QuickActionArrangement.Default`; System-`VOICE_INPUT` nun in dynamicActions). Auch in `PopupUiController.ExceptionsForKeyCodes`.
  - [ ] Eigene `dictate-*`-Theme-Elemente + lokalisierte Strings (mit Schritt 6)
- [ ] **Schritt 5** – Logik-Port (Recording/Transcription/Rewording)
- [ ] **Schritt 6** – eigenes Onboarding + Settings-Screens
- [ ] **Schritt 7** – Feinschliff
