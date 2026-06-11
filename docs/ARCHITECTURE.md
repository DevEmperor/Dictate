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
7. **Feinschliff:** Glide-Typing-Evaluierung, Themes, Changelog/Update-Flow.
