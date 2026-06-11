# Dictate Keyboard (Neuaufbau) — Projektüberblick

Neue, komplett überarbeitete Version der Dictate-Tastatur: aus einer reinen Diktat-Tastatur
wird eine **vollwertige Tastatur** (Tippen, Layouts, Mehrsprachigkeit) **mit integriertem
KI-Diktat und KI-Umformulierung** und **frei wählbaren KI-Providern**.

> Eigenständiges Projekt. Das bestehende `../Dictate` (v3.2.0) bleibt unangetastet.

## Basis
Dieses Projekt ist ein **Derivat von FlorisBoard** (Apache-2.0) als Tipp-Engine.
Der Original-FlorisBoard-Quellbaum wurde übernommen; siehe `LICENSE` (Apache-2.0) und
`docs/UPSTREAM_README.md` für die ursprüngliche Projektbeschreibung.

> Hinweis: `AI_POLICY.md` ist die FlorisBoard-Contribution-Regel des Upstreams — sie betrifft
> Beiträge an deren Repo, nicht dieses eigenständige Derivat.

## Bereits erledigt (Schritt 1)
- FlorisBoard-Baum als Projektbasis übernommen (frisches Git, ohne Upstream-Historie).
- `applicationId` → `net.devemperor.dictate` (Datenvertrag, nahtloses Play-Store-Update).
- App-Name (`app_name`) → „Dictate".
- `local.properties` mit lokalem SDK-Pfad.

## Dokumente (`docs/`)
- **`docs/COMPATIBILITY.md`** — Datenvertrag: alle Prefs-Keys + DB-Schemata für die Datenübernahme.
- **`docs/ARCHITECTURE.md`** — FlorisBoard-Analyse, Integrationspunkte, Provider-Konzept, Roadmap.
- **`docs/UPSTREAM_README.md`** — Original-FlorisBoard-README (Attribution).

## Build
- JDK 17+ erforderlich (lokal: `/usr/lib/jvm/java-17-openjdk`), Gradle Wrapper 9.4.1.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug`
- Debug installiert als `net.devemperor.dictate.debug` (Suffix), Release als `net.devemperor.dictate`.

## Roadmap (Stand)
Siehe `docs/ARCHITECTURE.md` §6. Nächste Schritte nach grünem Build:
Datenschicht + Migration → Provider-Registry → Dictate-IME-Modus → Logik-Port → eigenes Onboarding.
