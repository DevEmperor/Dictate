# Privacy Policy for Dictate Keyboard

**Effective date:** 15 June 2026
**Last updated:** 15 June 2026

This Privacy Policy explains how **Dictate Keyboard** (the "App", application ID
`net.devemperor.dictate`) handles your information. The App is developed and
maintained by **DevEmperor** (the "Developer", "we", "us"), the data controller
for the limited purposes described below.

If you have any questions about this policy, contact us at
**contact@devemperor.net**.

---

## 1. Summary (the short version)

- **We do not run any server and we do not collect, store, or receive any of
  your data.** The Developer has no backend, no database, and no analytics.
- Dictate Keyboard is a voice-to-text keyboard. To turn your speech into text it
  sends the audio you record to an **AI provider that you choose and configure**
  (for example OpenAI, Groq, or a self-hosted server). That transfer happens
  **directly from your device to that provider** — it never passes through us.
- Your API keys, prompts, and settings are stored **only on your device**.
- The App contains **no advertising, no tracking, no telemetry, and no
  crash-reporting SDKs**.
- As a keyboard, the App **does not log your keystrokes or collect what you type**
  in other apps. It only processes audio that you explicitly record by pressing
  the dictation button.

---

## 2. What information the App processes

### 2.1 Voice recordings
When you start a dictation, the App records audio using your device microphone.
The recording is written to a temporary, app-private cache file
(`dictate_audio.m4a`) on your device and is **deleted automatically** once the
transcription completes. To produce a transcript, this audio is uploaded to the
transcription provider you have selected.

If you enable the "resend / retry" convenience feature, the most recent recording
may be kept temporarily so it can be sent again; it is deleted when you start a
new dictation, when you clear it, or when the App removes it.

### 2.2 Text you transcribe or reword
The transcribed text — and, if you use the rewording / AI-formatting feature, the
text you ask the App to rephrase — is sent to the AI provider you have configured
so the requested result can be returned. The provider returns the text to your
device, where it is inserted into the field you are typing in.

### 2.3 API keys and provider configuration
To use a provider, you enter your own API key (or, for a local/self-hosted server
such as Ollama, a base URL). These credentials and provider settings are stored
**locally on your device** in the App's private storage. They are sent **only** to
the corresponding provider's API to authenticate your own requests, and are
**never** transmitted to the Developer.

### 2.4 Prompts and settings
Custom rewording prompts, style settings, language preferences, and other
configuration are stored **locally on your device** (in an app-private settings
store and a local SQLite database). They are not uploaded anywhere except as part
of a request to your chosen provider when relevant (e.g. a prompt you send for
rewording).

---

## 3. Third-party AI providers

To perform transcription and rewording, the App acts as a client to an
OpenAI-compatible API **that you select and authenticate with your own account**.
When you use such a feature, your audio and/or text is processed by that provider
under **their** privacy policy and terms — they act as independent data
controllers, not on our behalf. We have no access to, and no control over, the
data you send them or what they do with it.

Built-in providers the App can be configured to use include:

| Provider | Privacy policy |
| --- | --- |
| OpenAI | https://openai.com/policies/privacy-policy |
| Groq | https://groq.com/privacy-policy/ |
| OpenRouter | https://openrouter.ai/privacy |
| Together AI | https://www.together.ai/privacy |
| DeepInfra | https://deepinfra.com/privacy |
| Mistral AI | https://mistral.ai/terms/#privacy-policy |
| xAI (Grok) | https://x.ai/legal/privacy-policy |
| DeepSeek | https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html |
| Ollama (local) | Runs on your own device/server — no third party involved |

You may also configure a **custom OpenAI-compatible endpoint**. If you do, your
data is sent to whichever server you specify, and you are responsible for that
server's data handling.

**Please review the privacy policy of any provider before you use it.** Because
the data is sent with your own API key, the provider may retain or process it
according to your account terms with them (for example, to deliver the service or
for abuse monitoring).

---

## 4. Permissions and why they are used

| Permission | Purpose |
| --- | --- |
| `RECORD_AUDIO` | To capture your voice when you press the dictation button. |
| `INTERNET` | To send audio/text to the AI provider you configured and receive the result. |
| `MODIFY_AUDIO_SETTINGS` / `BLUETOOTH` | To route recording correctly, including through Bluetooth headsets. |
| `VIBRATE` | Optional haptic feedback. |
| `POST_NOTIFICATIONS` | To show status notifications (e.g. transcription progress) on Android 13+. |

The App requests the microphone permission only for dictation and uses it only
while you are actively recording.

---

## 5. Data storage and retention

- **On your device:** API keys, settings, and prompts remain on your device until
  you delete them in the App or uninstall the App. Temporary audio recordings are
  deleted automatically after transcription.
- **With your chosen provider:** any retention of audio or text is governed by
  that provider's policy, not by us.
- **With the Developer:** none — we store nothing.

To erase all locally stored data, clear the App's keys/settings in its settings
screen or uninstall the App.

---

## 6. We do not collect what you type

Although Dictate Keyboard is an input method (a keyboard), it does **not** record,
log, store, or transmit your general typing. Only audio you deliberately record
for dictation is processed, and only for the purpose of returning a transcript.

---

## 7. Data security

Data sent to AI providers is transmitted over encrypted HTTPS connections. Your
credentials and settings are kept in the App's private, sandboxed storage, which
on Android is not accessible to other apps. No method of transmission or storage
is 100% secure, but we limit risk by keeping all of your data on your device and
operating no server of our own.

---

## 8. Children's privacy

The App is not directed at children under the age of 13 (or the equivalent
minimum age in your jurisdiction), and we do not knowingly process data from
children. Note that the third-party AI providers may impose their own age
requirements.

---

## 9. International data transfers

The AI providers you choose may operate servers in other countries (for example
the United States). When you send audio or text to a provider, that data may be
processed in the country where the provider operates, under that provider's
policies.

---

## 10. Your choices and rights

- You decide whether to use any AI provider and which one.
- You can change or delete your API keys at any time in the App's settings.
- You can delete your custom prompts and reset settings in the App.
- You can uninstall the App to remove all locally stored data.
- For data already processed by a third-party provider, please exercise your
  rights directly with that provider, who is the controller for that data.

---

## 11. Changes to this policy

We may update this Privacy Policy from time to time. Material changes will be
reflected by updating the "Last updated" date above and publishing the new version
at this location. Continued use of the App after an update constitutes acceptance
of the revised policy.

---

## 12. Contact

If you have questions or requests regarding this Privacy Policy, contact:

**DevEmperor** — contact@devemperor.net

Project repository: https://github.com/DevEmperor/Dictate
