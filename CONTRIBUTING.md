# Contributing to PurefusionIPTV-plugin-api

Thank you for helping improve the open plugin API.

## Scope

This repository contains **only** public contracts (`Plugin`, `MediaServerProvider`, `CloudStorageProvider`, `SyncProvider`).  

The PureFusion IPTV **application** is closed source and is not developed here.

## How to contribute

1. Open an [issue](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues) to discuss API changes before large PRs.
2. Fork and branch from `main`.
3. Keep binary compatibility in mind — breaking changes require a new **minor/major API tag** and release notes.
4. Open a pull request with a clear description and test notes (compile the library module).

## API change policy

- **Patch** (v1.0.x): bug fixes, documentation, non-breaking additions (default methods).
- **Minor** (v1.x.0): new interfaces or capabilities with defaults where possible.
- **Major** (v2.0.0): breaking signature or semantic changes.

## Code style

Match existing Kotlin style in `src/main/java/dev/eliminater/purefusioniptv/plugin/`.
