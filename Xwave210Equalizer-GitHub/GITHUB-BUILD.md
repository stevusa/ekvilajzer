# GitHub cloud build — Xwave 210 Equalizer

Ovaj paket je podešen da GitHub Actions kompajlira APK umesto tvog laptopa.

## Najjednostavniji postupak

1. Na GitHub-u napravi novi prazan repository, npr. `Xwave210Equalizer`.
2. Raspakuj ovaj ZIP.
3. Uploaduj SAV sadržaj foldera `Xwave210Equalizer-GitHub` u root repozitorijuma.
   Bitno: `.github/workflows/build-apk.yml` mora ostati u toj putanji.
4. Commituj fajlove.
5. Otvori karticu `Actions`.
6. Izaberi workflow `Build Xwave 210 APK`.
7. Klikni `Run workflow`.
8. Kada build bude zelen, otvori taj workflow run.
9. Dole pod `Artifacts` preuzmi `Xwave210-Equalizer-APK`.
10. Raspakuj artifact ZIP; unutra je `Xwave210-Equalizer.apk`.

Workflow se takođe automatski pokreće kada pushuješ izmene na `main` ili `master`.

## Ako build padne

Otvori crveni workflow run, zatim korak koji je crven, i kopiraj ceo tekst greške.
