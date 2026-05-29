# Project Notes

## Target Platform

This is an Android project targeting **API 19 (KitKat)**. Avoid using APIs above API 19 without adding appropriate compatibility notes or checks.

## Architecture

- **UI:** Android Views (XML layouts) — Jetpack Compose requires API 21+ and is not compatible with the API 19 target.
- **Architecture pattern:** MVVM (ViewModel + LiveData) — current Android best practice for View-based apps.
- **Local storage:** Room (SQLite ORM).
