# Getting started

1. **Download the public Plugin API.** Clone `https://github.com/Eliminater74/PurefusionIPTV-plugin-api.git` and use a revision containing `template/` and `SandboxSurfaceV1.kt`. The v1.0.2 source release predates the starter and isolated extensions. If you need subtitle support, also check for `SandboxSubtitleV1.kt`. Pin your chosen revision. Source substitution resolves artifact coordinate `dev.eliminater.purefusioniptv:plugin-api:1.0.0`; no private Maven repository is needed.
2. **Open `template/` in Android Studio.** Install Platform 36 and Build Tools 36.0.0. Configure a Gradle-supported JDK 17+. Set `ANDROID_HOME` to your SDK; with `local.properties`, both template and SDK need a location, so environment configuration is simplest. Gradle downloads dependencies on first use. AGP 9.2.1 includes Kotlin support; do not apply Kotlin Android separately.
3. **Customize identity.** The developer portal supplies your developer ID and namespace. For local work a synthetic identity is fine. Replace every argument:

   ```sh
   node tools/marketplace.cjs init dev.pf_YOUR_NAMESPACE.myplugin "My TV Plugin" "My Studio" pf_YOUR_ID "What my plugin does"
   ```

   Windows also supports `tools/init-plugin.ps1 -PluginId ... -PluginName ... -Publisher ... -DeveloperId ... -Description ...`. The helper changes manifest/listing together, keeps exclusive `.init-backup` originals and refuses a customized project. It does not enroll or claim an ID. Manual edits remain supported. `dev.pf_example.starter` is an example, never a real submission identity. Kotlin namespace/class can stay `dev.example.purefusionstarter`; these implementation names are independent of plugin ID. If renamed, update namespace in `plugin/build.gradle.kts`, source/test packages, service registration and manifest `entryPoint` together.
4. **Update functionality, description and capabilities.** Edit `manifest.json` and `StarterPlugin.kt`. Default requests are `UI_HOME_EXTENSION` and `PLUGIN_SCOPED_STORAGE`. Optional HTTP requires adding `NETWORK_EXTERNAL`, a newly signed/reviewed release, and TV origin consent. Never add Android INTERNET permission to bypass the broker.
5. **Build and test**, from this directory:

   ```sh
   bash gradlew :plugin:testDebugUnitTest :packaging:test :plugin:assembleRelease
   node --test tools/test/workflow.test.cjs
   ```

   Windows: `.\gradlew.bat` with the same tasks. Tests use an in-memory host broker. Reports: `plugin/build/reports/tests/testDebugUnitTest/index.html` and `packaging/build/reports/tests/test/index.html`. Requirements: [official AGP compatibility table](https://developer.android.com/build/releases/agp-9-2-0-release-notes).
6. **Create or reuse your developer key.** Set `PUREFUSION_PFP_SIGNING_DIR` to a new absolute directory outside the whole repository. PowerShell: `$env:PUREFUSION_PFP_SIGNING_DIR = Join-Path $env:USERPROFILE '.purefusion-signing/myplugin'`. POSIX: `export PUREFUSION_PFP_SIGNING_DIR="$HOME/.purefusion-signing/myplugin"`. Run `:packaging:createSigningKey` once with the wrapper. For an existing key, point at its directory and run `:packaging:checkSigningKey`; do not regenerate it.
7. **Package.** `bash gradlew :packaging:packageSigned` (Windows: `.\gradlew.bat :packaging:packageSigned`). Output: `packaging/build/signed/<pluginId>-<version>.pfp`. The adjacent `manifest.json` is the exact generated signed manifest. Root build inputs are not that manifest.
8. **Verify.** `bash gradlew :packaging:verifySignedPfp`. Optionally pass `-PpfpFile=/absolute/path/plugin.pfp`. With signing environment set, this additionally requires that exact public key. Without it, verification establishes certificate/signature self-consistency, not ownership. Current project identity is also compared with the archive. See [Signing](docs/SIGNING.md).
9. **Complete listing and artwork.** Edit `marketplace/listing-template.json`; replace every placeholder, including alt text. Replace PNGs in `marketplace/artwork/` with your icon and genuine screen capture (keep filenames for the helper). Set a real HTTPS support link. Run:

   ```sh
   node tools/marketplace.cjs prepare
   node tools/marketplace.cjs validate
   node tools/marketplace.cjs preview
   ```

   Open `marketplace/preview/card.html`. `prepare` updates generated `listing.local.json`, never the editable template; it synchronizes manifest identity and computes artwork descriptors. Uncustomized drafts intentionally fail `validate`. Preview works with placeholders; replace them before real submission.
10. **Submit for review.** Sign into [the developer portal](https://purefusioniptv.com/developer/). Enter fields manually using the [Listing Guide](marketplace/LISTING_GUIDE.md), upload signed package/artwork and supply rights disclosure. The current portal has no JSON import control. Namespace/key ownership, verification, independent review, publisher eligibility and audience policy remain server decisions. Public third-party executable distribution is gated; do not assume approval or availability.

## Copy into your own repository

A copied template discovers an inner `plugin-api/` or sibling `../plugin-api` public checkout. You can also use `-PpluginApiDir=/absolute/path/to/sdk`. Pin a public revision containing these contracts. No Core files are needed. Keys must stay outside the entire Git checkout, including when this project is nested within a larger repository.

## On-TV testing prerequisite

Current Core has only the Sports Hub surface launch slot; arbitrary third-party surface discovery is not available. This starter builds/tests/signs independently, but a TV screen run needs an authorized host launch integration as well as real Marketplace approval, admission and consent. Local JVM tests are the available developer loop here; do not claim TV acceptance from them or take another plugin's ID.
