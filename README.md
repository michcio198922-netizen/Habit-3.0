# Nawyki — aplikacja Android

Prosta aplikacja do kontrolowania dobrych i złych nawyków.

## Co działa
- dodawanie własnych dobrych i złych nawyków,
- oznaczanie dnia jako **Sukces** albo **Porażka**,
- ponowne kliknięcie aktywnego statusu usuwa oznaczenie,
- seria kolejnych udanych dni,
- podgląd ostatnich 7 dni,
- liczba sukcesów z zaznaczonych dni,
- filtrowanie: Wszystkie / Dobre / Złe,
- usuwanie nawyków,
- zapis lokalny w pamięci telefonu (SharedPreferences), bez konta i internetu.

## Uruchomienie
1. Otwórz folder projektu w aktualnym Android Studio.
2. Pozwól Android Studio pobrać wymagane SDK/Gradle.
3. Uruchom aplikację na telefonie z włączonym debugowaniem USB albo na emulatorze.

Projekt używa:
- Android Gradle Plugin 9.4.0,
- Compose Compiler / Kotlin plugin 2.2.10,
- Gradle 9.6.0,
- compileSdk / targetSdk 37,
- Jetpack Compose BOM 2026.09.00,
- minSdk 26.

## APK
W Android Studio wybierz **Build > Build APK(s)**. Debugowy APK znajdziesz zwykle w:
`app/build/outputs/apk/debug/app-debug.apk`.

## Ważne
W paczce jest kod projektu. Jeśli Android Studio zgłosi brak pliku `gradle-wrapper.jar`, wybierz w IDE konfigurację Gradle 9.6.0 albo wygeneruj wrapper poleceniem `gradle wrapper --gradle-version 9.6.0` w systemie z zainstalowanym Gradle.
