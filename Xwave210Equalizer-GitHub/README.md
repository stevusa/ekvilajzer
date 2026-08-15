# Xwave 210 Equalizer — GitHub Cloud Build

Android TV equalizer projekat sa GitHub Actions cloud buildom.

Glavno uputstvo: `GITHUB-BUILD.md`

Workflow:
`.github/workflows/build-apk.yml`

Posle uspešnog builda GitHub Actions čuva:
`Xwave210-Equalizer.apk`

Napomena: uspešan APK build ne garantuje da će Xwave firmware dozvoliti globalni
AudioEffect session 0. To se može proveriti tek kada aplikaciju instaliraš na TV Box.
