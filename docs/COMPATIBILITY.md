# Kompatibilitäts-Vertrag (Datenübernahme von Dictate 3.2.0)

Dieses Dokument friert **alle Identifier** ein, die die neue App lesen muss, damit
Bestandsnutzer beim Play-Store-Update ihre Daten behalten. **Nichts hiervon darf sich ändern.**

## Grundvoraussetzungen (sonst gehen Daten verloren)

- **`applicationId` bleibt `net.devemperor.dictate`** (das neue Florisboard-Derivat MUSS diese ID setzen,
  nicht `dev.patrickgold.florisboard`).
- **Gleicher Signing-Keystore** wie die bisherige Play-Store-App.
- **`versionCode` > 30** (bisher 30 = v3.2.0).

Solange das gilt, bleiben SharedPreferences-Datei und SQLite-DBs im App-Datenverzeichnis physisch erhalten.
Die neue App muss sie nur unter denselben Namen lesen. **Kein Export/Import nötig.**

## 1. SharedPreferences

- **Dateiname / Prefs-Name:** `net.devemperor.dictate` (Modus `MODE_PRIVATE`)
- Migrationsstrategie: einmalig beim ersten Start die alte XML lesen und in die neue
  Settings-Schicht (JetPref/DataStore) übernehmen (`SharedPreferences`-Direktzugriff, kein JetPref nötig zum Lesen).

### Schlüssel (alle mit Prefix `net.devemperor.dictate.`)

| Key | Typ | Default | Bedeutung |
|---|---|---|---|
| `user_id` | String | random | anonyme Nutzer-ID (Crashlytics/Feedback) |
| `onboarding_complete` | bool | false | Onboarding abgeschlossen |
| `rewording_enabled` | bool | true | KI-Umformulierung an |
| `auto_formatting_enabled` | bool | false | experimentelles Auto-Formatting |
| `input_languages` | StringSet | {detect,en} | gewählte Eingabesprachen |
| `input_language_pos` | int | 0 | aktive Sprache (Index) |
| `overlay_characters` | String | `()-:!?,.` | Enter-Langdruck-Sonderzeichen (max 8) |
| `auto_enter` | bool | false | nach Transkription Enter/Senden |
| `resend_button` | bool | false | Resend-Button anzeigen |
| `instant_recording` | bool | false | Tastatur startet sofort Aufnahme |
| `instant_output` | bool | true | Sofortausgabe vs. getippte Animation |
| `output_speed` | int | 5 | Tipp-Animationsgeschwindigkeit (1–10) |
| `audio_focus` | bool | true | Hintergrund-Audio pausieren |
| `use_bluetooth_mic` | bool | false | Bluetooth-Headset-Mikro |
| `vibration` | bool | true | Vibrations-Feedback |
| `theme` | String | system | system/light/dark |
| `accent_color` | int | -14700810 | Akzentfarbe (ARGB) |
| `animations` | bool | true | Tastendruck-Animationen |
| `app_language` | String | system | system/en/de/es/pt |
| `transcription_provider` | int | 0 | 0=OpenAI 1=Groq 2=Custom |
| `rewording_provider` | int | 0 | 0=OpenAI 1=Groq 2=Custom |
| `api_key` | String | — | **Legacy** globaler Key (Fallback) |
| `transcription_api_key` | String | — | aktiver Transkriptions-Key (gespiegelt) |
| `rewording_api_key` | String | — | aktiver Rewording-Key (gespiegelt) |
| `transcription_api_key_openai` / `_groq` / `_custom` | String | — | Keys je Provider |
| `rewording_api_key_openai` / `_groq` / `_custom` | String | — | Keys je Provider |
| `transcription_model` | String | — | **Legacy** Transkriptionsmodell |
| `transcription_openai_model` | String | gpt-4o-mini-transcribe | |
| `transcription_groq_model` | String | whisper-large-v3-turbo | |
| `transcription_custom_host` | String | — | eigener Endpunkt |
| `transcription_custom_model` | String | — | eigenes Modell |
| `rewording_model` | String | — | **Legacy** Rewording-Modell |
| `rewording_openai_model` | String | gpt-4o-mini | |
| `rewording_groq_model` | String | llama-3.3-70b-versatile | |
| `rewording_custom_host` | String | — | eigener Endpunkt |
| `rewording_custom_model` | String | — | eigenes Modell |
| `proxy_enabled` | bool | false | Proxy aktiv |
| `proxy_host` | String | — | `socks5|http://user:pass@host:port` |
| `style_prompt_selection` | int | 1 | 0=nichts 1=vordefiniert 2=eigen |
| `style_prompt_custom_text` | String | — | eigener Whisper-Style-Prompt |
| `system_prompt_selection` | int | 1 | 0=nichts 1=vordefiniert 2=eigen |
| `system_prompt_custom_text` | String | — | eigener Rewording-System-Prompt |
| `last_file_name` | String | audio.m4a | letzte Audiodatei im Cache |
| `transcription_audio_file` | String | — | zu transkribierende Datei (transient) |
| `last_version_code` | int | 0 | für Changelog-Anzeige |
| `flag_has_rated_in_playstore` | bool | false | Bewertungs-Flag |
| `flag_has_donated` | bool | false | Spenden-Flag |

## 2. SQLite-Datenbanken

### `prompts.db` (Version 2)
```sql
CREATE TABLE PROMPTS (
  ID INTEGER PRIMARY KEY,
  POS INTEGER,
  NAME TEXT,
  PROMPT TEXT,
  REQUIRES_SELECTION BOOLEAN,
  AUTO_APPLY BOOLEAN DEFAULT 0
);
```
Migration v1→v2: `ALTER TABLE PROMPTS ADD COLUMN AUTO_APPLY BOOLEAN DEFAULT 0`.

### `usage.db` (Version 2)
```sql
CREATE TABLE USAGE (
  MODEL_NAME TEXT PRIMARY KEY,
  AUDIO_TIME LONG,
  INPUT_TOKENS LONG,
  OUTPUT_TOKENS LONG,
  MODEL_PROVIDER LONG
);
```
Migration v1→v2: `ALTER TABLE USAGE ADD COLUMN MODEL_PROVIDER LONG DEFAULT 0`.

### Room-Hinweis
Beim Umstieg auf Room: gleiche DB-Dateinamen + Entities, die **exakt** auf diese Spalten/Typen mappen,
Startversion = 2, `exportSchema = true`, **niemals** `fallbackToDestructiveMigration` in Produktion.
Instrumented-Migrationstest mit echten Alt-DB-Fixtures als Pflicht-Sicherheitsnetz.

## 3. Cache
Audiodateien liegen im `cacheDir` (`audio.m4a` + importierte Dateien). Cache ist nicht migrationskritisch.
