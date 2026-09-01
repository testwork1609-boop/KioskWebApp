# KioskWebApp — instrukcja budowy APK przez GitHub Actions

Ten projekt zawiera kompletną aplikację Android (Kotlin + Jetpack Compose)
realizującą wszystkie funkcje z Twojej specyfikacji: kreator pierwszego
uruchomienia, zapamiętywanie konfiguracji, pełnoekranowy WebView, Kiosk
(Lock Task) Mode, gest administratora, panel administracyjny, obsługę
utraty połączenia, auto-odświeżanie i autostart po restarcie.

**Nie musisz nic kompilować lokalnie.** Budowanie APK odbywa się w chmurze,
na serwerach GitHub — Ty tylko pobierasz gotowy plik.

## Krok po kroku

### 1. Załóż repozytorium na GitHub

1. Wejdź na [github.com](https://github.com) i zaloguj się (lub załóż darmowe konto).
2. Kliknij **New repository**, nadaj nazwę np. `kiosk-webapp`, ustaw je jako
   **Private** lub **Public** (bez znaczenia dla działania), NIE zaznaczaj
   „Add a README" (żeby repo było puste).
3. Kliknij **Create repository**.

### 2. Wgraj pliki projektu

Najprościej przez przeglądarkę, bez terminala i bez Gita:

1. Na stronie swojego nowego, pustego repozytorium kliknij link
   **„uploading an existing file"**.
2. Przeciągnij tam **całą zawartość** tego folderu (wszystkie pliki i
   podfoldery: `app/`, `.github/`, `build.gradle.kts`, `settings.gradle.kts`,
   `gradle.properties`, `README.md`).
3. Na dole kliknij **Commit changes**.

(Alternatywnie, jeśli znasz Git: `git init`, `git add .`, `git commit -m "init"`,
`git remote add origin <adres-twojego-repo>`, `git push -u origin main`.)

### 3. Uruchom budowanie APK

Samo wgranie plików do gałęzi `main`/`master` **automatycznie uruchomi**
budowanie (dzięki plikowi `.github/workflows/build.yml`).

Możesz też uruchomić je ręcznie:
1. Wejdź w zakładkę **Actions** w swoim repozytorium.
2. Wybierz workflow **„Build KioskWebApp APK"**.
3. Kliknij **Run workflow** → **Run workflow**.

Budowanie trwa zwykle 3–6 minut.

### 4. Pobierz gotowy plik APK

1. Wejdź w zakładkę **Actions**, kliknij na ukończone (zielony ✓) uruchomienie.
2. Na dole strony, w sekcji **Artifacts**, zobaczysz:
   - **KioskWebApp-debug-apk** — gotowy, **od razu instalowalny** plik
     (podpisany automatycznym kluczem debugowym Androida). **To jest ten,
     którego chcesz użyć.**
   - **KioskWebApp-release-unsigned-apk** — wersja release, ale
     **niepodpisana** (Android odmówi jej instalacji, dopóki nie zostanie
     podpisana własnym kluczem — potrzebne tylko, jeśli planujesz publikację
     w sklepie lub dystrybucję na większą skalę).
3. Pobierz **KioskWebApp-debug-apk** (plik `.zip` — rozpakuj go, w środku
   znajdziesz `app-debug.apk`). Możesz go dowolnie zmienić nazwę na
   `KioskWebApp.apk`.

### 5. Zainstaluj na tablecie

1. Przenieś `KioskWebApp.apk` na tablet (USB, e-mail, dysk w chmurze).
2. W ustawieniach tabletu włącz **„Instaluj z nieznanych źródeł"** dla
   aplikacji, z której otwierasz plik (np. Menedżer plików).
3. Otwórz plik APK i zainstaluj.

## Pierwsze uruchomienie

Aplikacja poprosi o adres strony WWW i hasło administratora (kreator
opisany w specyfikacji). Od tego momentu uruchamia się automatycznie na
podanej stronie, w trybie pełnoekranowym.

## Ważne — dwa poziomy trybu kiosk

- **Bez Device Owner (domyślnie):** aplikacja blokuje Home/Recents przez
  mechanizm „przypinania ekranu" (Lock Task bez allow-listy). Działa dobrze
  jako kiosk, ale przy pierwszym użyciu system pokaże jednorazowy komunikat
  wyjaśniający.
- **Z Device Owner (pełny, „silent" kiosk):** brak jakichkolwiek komunikatów
  systemowych, pełna blokada na poziomie systemu. Wymaga jednorazowej
  konfiguracji na **świeżo zresetowanym** tablecie (bez żadnego konta
  Google dodanego na urządzeniu):

  ```
  adb shell dpm set-device-owner com.kioskwebapp.app/.KioskDeviceAdminReceiver
  ```

  Uruchom to polecenie z komputera podłączonego przez USB (z włączonym
  debugowaniem USB na tablecie), zaraz po zainstalowaniu APK, zanim
  dodasz jakiekolwiek konto na urządzeniu.

## Panel administracyjny

Gest: **5 szybkich kliknięć w prawy górny róg ekranu** (w ciągu 3 sekund) →
prośba o hasło administratora → panel z opcjami: zmiana adresu, zmiana
hasła, interwał odświeżania, przeładowanie strony, wyjście z Kiosk Mode,
restart aplikacji, reset konfiguracji.

## Struktura projektu

```
KioskWebApp/
├── .github/workflows/build.yml     ← budowanie APK w chmurze (GitHub Actions)
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/kioskwebapp/app/
│       │   ├── MainActivity.kt      ← lifecycle, Lock Task, fullscreen
│       │   ├── KioskUi.kt           ← kreator, WebView, panel admina
│       │   ├── PrefsManager.kt      ← szyfrowany zapis konfiguracji
│       │   ├── BootReceiver.kt      ← autostart po restarcie
│       │   └── KioskDeviceAdminReceiver.kt
│       └── res/...
├── build.gradle.kts
└── settings.gradle.kts
```
