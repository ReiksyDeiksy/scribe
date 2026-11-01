# Private APK distribution

## Current update: patch notes, 1 November 2026

Version `0.1.3-test`, versionCode **4**, adds Settings > Patch notes and a short
latest-update window. Both languages/themes include a small history and steps
for sharing problem reports. The window appears once per version, when the app
is idle, including for installations that skipped the reporting update.

Every future user-visible update must add a concise English and Romanian note
and any needed usage steps. Keep earlier notes. Update `PatchNotes.java`, both
`patch_strings.xml` files and `app/build.gradle` together. See `AGENTS.md`.

The next update must use a versionCode greater than **4**. Install over the app
with the existing signing key; do not uninstall or clear data. See the latest
section in `VALIDATION.md` for the package path and checks.

Prepared package:
`artifacts/private-release/scribe-0.1.3-test-vc4-patch-notes-1b852ec7/`.
Send `scribe-0.1.3-test-vc4.apk` as a file; the installation guide and checksum
are beside it. All previous packages remain unchanged.

## Previous update: report sharing, 1 November 2026

`artifacts/private-release/scribe-0.1.2-test-vc3-reports-dfa18077/` contains
`scribe-0.1.2-test-vc3.apk`, its checksum, installation guide, release metadata,
signature verification and payload audit. Install over the existing app with the
same signing key. Do not uninstall or clear application data. This reporting
package uses versionCode **3**.

The new **Report a problem** action creates a local ZIP for explicit sharing
through WhatsApp or saving with Android's file picker. Audio/text are opt-in;
time-range audio is cropped. No server or automatic upload is used. See
`REPORTS.md` and the latest section of `VALIDATION.md` for scope and verification.

The existing theme/help updates are included. No private-device installation or
message delivery has been performed. The earlier package below remains unchanged.

Die Android-App wird direkt als signierte Release-APK an Mara weitergegeben.
Ein Play-Store-Eintrag, Firebase oder ein eigener Server ist dafür nicht nötig.
Die App enthält keinen automatischen Updater. Jede neue APK wird manuell als
Update installiert; die Oberfläche und die Offline-Verarbeitung bleiben gleich.

## Erste Weitergabe

Das geprüfte Paket vom 1. November 2026 liegt unter:

`artifacts/private-release/scribe-0.1.1-test-vc2-d06e3745/`

- `scribe-0.1.1-test-vc2.apk`: signierte App, inklusive Sprachmodell und
  persönlicher Hilfe unter Settings > Help / Setări > Ajutor.
- `INSTALLATION-MARA.txt`: englische Installations- und Updateanleitung.
- `SHA256SUMS.txt`: Prüfsumme der APK.
- `release.json`: Version, Paketname und öffentliche Signaturdaten.

Nur APK und Anleitung müssen privat übertragen werden, zum Beispiel als Datei
über einen vorhandenen Messenger, einen privaten Download oder per USB. Diese
lokale Paketvorbereitung versendet und veröffentlicht nichts. Eine direkte APK
beschränkt die Nutzung nicht technisch auf eine einzelne Person.

Voraussetzung ist Android 12 oder neuer auf einem unterstützten 64-Bit-Gerät.
Beim Öffnen der APK muss Android gegebenenfalls Installationen für die verwendete
Quell-App erlauben, etwa den Dateimanager. Danach lässt sich diese Berechtigung
wieder abschalten. Die genaue Menübezeichnung hängt von der Samsung-Version ab.
Bei einer anderen Sperrmeldung zuerst deren Wortlaut prüfen.

Herstelleranleitung:
[Samsung: Installationen aus anderen Quellen](https://www.samsung.com/ae/support/mobile-devices/how-to-enable-permission-to-install-apps-from-unknown-source-on-my-samsung-phone/).

## Nächstes Update vorbereiten

1. In `app/build.gradle` den `versionCode` auf einen bisher nicht verwendeten,
   höheren Wert setzen; nach dem aktuellen Paket mindestens `5`. Einen passenden
   `versionName` vergeben. Immer kurze englische und rumänische Patch Notes samt
   nötiger Bedienhinweise ergänzen und die vorherigen Einträge behalten.
2. `applicationId 'ro.scribe.lectures'` und den bestehenden Signaturschlüssel
   beibehalten. Den privaten Ordner `.tools/signing/` samt Konfiguration
   verschlüsselt sichern. Er gehört nicht in Git oder ein Weitergabepaket.
3. Falls sich das Datenbankschema ändert, zuvor eine nicht destruktive Migration
   implementieren und mit bestehenden Testdaten prüfen. Die App-Versionsnummer
   ist unabhängig von der Datenbankversion.
4. Im Ordner `App` bauen und prüfen. Nach einem Fehler nicht weitergeben:

   ```powershell
   .\build.ps1 -Tasks @('testDebugUnitTest', 'lintRelease', 'assembleRelease')
   python .\tools\verify_apk.py
   if ($LASTEXITCODE -ne 0) { throw 'APK audit failed' }

   $env:JAVA_HOME = Join-Path (Get-Location) '.tools\jdk'
   & .\.tools\android-sdk\build-tools\35.0.0\apksigner.bat verify --verbose --print-certs .\app\build\outputs\apk\release\app-release.apk
   if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
   & .\.tools\android-sdk\build-tools\35.0.0\zipalign.exe -c -P 16 4 .\app\build\outputs\apk\release\app-release.apk
   if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed' }
   ```

   Nach einer Toolchain-Aktualisierung die Build-Tools-Pfade anpassen. Der
   Zertifikat-Fingerabdruck muss weiterhin diesem SHA-256-Wert entsprechen:

   `c42170e8641cdf6309457bfc4850169d94a0e05fac47a82ab6b088d73d8ac44d`

5. Die neue APK in einen neuen, eindeutig benannten Paketordner kopieren und
   ihren SHA-256-Wert festhalten. Alte Weitergabepakete unverändert aufbewahren.
6. Vor Weitergabe das Update über eine vorhandene Installation mit synthetischen
   Testdaten installieren. Aufnahme, Transkript, Kurszuordnung, Zeitmarken und
   Einstellungen müssen erhalten bleiben. Funktionsprüfungen passend zur
   Änderung ergänzen.
7. Mara öffnet die neue APK und bestätigt das Update. Die vorhandene App dabei
   nicht deinstallieren und ihre Daten nicht löschen. Bei einem Installations-
   oder Signaturkonflikt die Fehlermeldung prüfen, statt neu zu installieren.

Ein Update soll bestehende App-Daten erhalten; Deinstallation oder Löschen der
App-Daten entfernt dagegen lokal gespeicherte Inhalte. Diese App hat Androids
automatische Datensicherung deaktiviert. Wichtige Transkripte separat exportieren.

## Prüfung dieses Weitergabepakets

- Release-Build erfolgreich; 6 JVM-Tests ohne Fehler. Debug- und Release-Lint
  ohne Befund.
- Android-Regression: 9 Tests bestanden. Die endgültige signierte APK bestand
  den Hilfe-Test zusätzlich auf Tablet- und schmalem Telefonlayout mit größerer
  Schrift, jeweils in Englisch/Rumänisch und hellem/dunklem Design.
- Keine Em-Dashes in den englischen und rumänischen App-Texten oder in den
  kompilierten Ressourcen. Die persönliche Unterschrift steht in einer neuen Zeile.
- APK-Signatur neu geprüft: RSA 3072 Bit, APK Signature Scheme v2, bestehendes
  Zertifikat. Paketname und Versionswerte direkt aus der APK bestätigt.
- APK-Payload, Modell-Prüfsumme und 16-KB-Ausrichtung erneut geprüft.
- Kein INTERNET-Recht und keine privaten Medien oder Signaturschlüssel im Paket.
- Keine Installation, Weitergabe oder Updateprüfung auf Maras physischem Gerät
  in diesem Arbeitsschritt. Vor echten Vorlesungen sind ein kurzer Geräte- und
  anschließend ein längerer Aufnahme-/Transkriptionstest erforderlich.
