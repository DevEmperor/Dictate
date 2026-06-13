<table>
  <tr>
    <td>
      <img src="https://github.com/DevEmperor/Dictate/blob/legacy-java/img/Icon_512x512_2_round.png?raw=true" alt="Dictate Keyboard logo" width="70">
    </td>
    <td>
      <h1>Dictate Keyboard</h1>
      <i>Speak. Transcribe. Reword. — your voice, turned into text by Whisper&nbsp;AI.</i>
    </td>
  </tr>
</table>

<p>
  <a href="https://play.google.com/store/apps/details?id=net.devemperor.dictate">
    <img alt="Get it on Google Play" height="60" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"/>
  </a>
  &nbsp;
  <a href="https://paypal.me/DevEmperor">
    <img alt="Donate with PayPal" height="38" src="https://www.paypalobjects.com/webstatic/en_US/i/buttons/PP_logo_h_150x38.png"/>
  </a>
</p>

<p>
  <img alt="License: Apache 2.0" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg">
  <img alt="Status: Work in progress" src="https://img.shields.io/badge/status-work%20in%20progress-orange.svg">
  <img alt="Built on FlorisBoard" src="https://img.shields.io/badge/built%20on-FlorisBoard-30B7E6.svg">
</p>

---

> ### 🚧 Work in progress — a complete rewrite
>
> **Dictate Keyboard is being rebuilt from the ground up.** The app you'll find in this
> repository today is a *brand-new* keyboard built on top of the excellent
> [**FlorisBoard**](https://github.com/florisboard/florisboard) engine — it replaces the
> original Java app that powered Dictate v1–v3.
>
> Why the rewrite? The original Dictate was an *overlay* on top of whatever keyboard you
> already used. The new Dictate is a **full, standalone keyboard**: you get a complete,
> privacy-respecting typing experience *and* one-tap Whisper dictation and GPT rewording,
> all in one place — with proper theming, gestures, clipboard tools and more.
>
> This means the codebase here is **not stable yet** and changes daily. If you just want to
> *use* the app, grab the stable release from [Google Play](https://play.google.com/store/apps/details?id=net.devemperor.dictate).
>
> The previous Java codebase is preserved on the [`legacy-java`](https://github.com/DevEmperor/Dictate/tree/legacy-java)
> branch and tagged as [`v3.2.0-final`](https://github.com/DevEmperor/Dictate/releases/tag/v3.2.0-final).

---

## ✨ What is Dictate?

**Dictate** is an easy-to-use keyboard for transcribing and dictating. It uses
[OpenAI Whisper](https://openai.com/index/whisper/) in the background, which delivers
extremely accurate results for
[many different languages](https://platform.openai.com/docs/guides/speech-to-text/supported-languages),
complete with punctuation — plus custom AI rewording powered by GPT models.

Instead of pecking at keys, just tap the microphone, speak, and watch your words appear as
clean, formatted text in any app. Need it more formal, translated, summarised, or
fixed-up? Hand the text to a rewording prompt and let the model do the work.

## 🎤 Features

- **Voice dictation with Whisper AI** — highly accurate speech-to-text in dozens of languages, with automatic punctuation.
- **AI rewording & rewriting** — turn a selection into something more formal, casual, translated, summarised, or anything you define with custom prompts.
- **Custom prompts & snippets** — build your own reword actions; reusable text snippets are inserted instantly without an API call.
- **Bring your own key** — works with your own OpenAI API key (and compatible endpoints), so you stay in control of usage and cost.
- **A real, full keyboard** *(courtesy of the FlorisBoard base):*
  - Huge variety of keyboard layouts and easy language/subtype switching
  - Full theme customization with day/night presets and automatic switching
  - Emoji keyboard, clipboard manager & cursor tools
  - One-handed / compact mode, gesture actions, customizable key sound & vibration
- **Privacy-respecting by design** — no tracking; your audio goes only to the AI provider you configure.

## 🧱 Built on FlorisBoard

Dictate Keyboard is a fork of [**FlorisBoard**](https://github.com/florisboard/florisboard),
an open-source, privacy-respecting keyboard created by
[Patrick Goldinger](https://github.com/patrickgold) and
[The FlorisBoard Contributors](https://github.com/florisboard/florisboard/graphs/contributors).
Their work provides the entire keyboard foundation — layouts, theming, gesture handling,
clipboard tools and the IME plumbing — on top of which Dictate adds its voice-dictation and
AI-rewording layer.

Huge thanks to the FlorisBoard team. FlorisBoard is licensed under the Apache License 2.0;
see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE) for full attribution.

## 📸 Screenshots

> _Coming soon — screenshots and a showcase video will be added once the rewrite is ready._

## 📲 Installation

**The app is available on [Google Play](https://play.google.com/store/apps/details?id=net.devemperor.dictate)**
(for a small fee that supports continued development), giving you easy installation and free
lifetime updates. Just tap the badge above or [this link](https://play.google.com/store/apps/details?id=net.devemperor.dictate).

> **Existing users:** the new keyboard keeps the same app identity and signing key, so when
> the rewrite ships as an update, **your settings carry over** — no reinstall, no lost
> configuration.

## 🛠️ Building from source

Dictate Keyboard is a standard Gradle Android project and **requires JDK 17**:

```bash
git clone https://github.com/DevEmperor/Dictate.git
cd Dictate
JAVA_HOME=/path/to/jdk-17 ./gradlew :app:assembleDebug
```

The resulting APK lands in `app/build/outputs/apk/debug/`.

## 🗺️ Roadmap

The rewrite is being ported feature-by-feature. A public roadmap will be published once the
foundation is stable — for now, follow along via the commit history and
[releases](https://github.com/DevEmperor/Dictate/releases).

## 🤝 Contributing

While the rewrite is in heavy flux, the repository **isn't accepting code contributions just
yet** — the architecture is still moving and a PR today may not apply tomorrow. The best way
to help right now is to **[open an issue](https://github.com/DevEmperor/Dictate/issues)** with
bug reports, ideas or feedback. Full contribution and community guidelines will be published
once things stabilise. Thank you for your patience! 🙏

## 📄 License & attribution

Dictate Keyboard is released under the terms of the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

- This project is a fork of **FlorisBoard** — Copyright © The FlorisBoard Contributors,
  licensed under Apache-2.0.
- See [`LICENSE`](LICENSE) for the full license text and [`NOTICE`](NOTICE) for required
  attribution notices.
- Speech recognition is powered by [OpenAI Whisper](https://openai.com/index/whisper/).

## ❤️ Support

If Dictate makes your day a little easier, you can support development by
[buying the app on Google Play](https://play.google.com/store/apps/details?id=net.devemperor.dictate)
or [donating via PayPal](https://paypal.me/DevEmperor). Every bit helps — thank you!
