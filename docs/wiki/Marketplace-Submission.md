# Marketplace Submission

1. Obtain the developer ID and namespace assigned to your verified author account. Keep that identity, the signed manifest, listing and archive in agreement.
2. Complete the [listing card](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/marketplace/LISTING_GUIDE.md). Include display publisher, descriptions, category, release version and code, API level, host/Android bounds, capabilities, icon, one to eight screenshots with alt text, and an HTTPS support URL. Supply a changelog for updates and separate rights disclosure. Replace example artwork with licensed assets and genuine captures.
3. Generate and inspect the local listing with `node tools/marketplace.cjs prepare`, `node tools/marketplace.cjs validate`, and `node tools/marketplace.cjs preview`. The helper checks local PNGs and manifest binding. The portal validates uploaded assets and package independently. The editable draft and local preview do not submit anything.
4. Enter the listing manually in the [developer portal](https://purefusioniptv.com/developer/) and upload the developer-signed archive and artwork. Follow the [submission workflow](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/docs/SUBMISSION.md) for verified intake and independent review. Release availability depends on publisher eligibility and the approved audience.
5. Eligible users install from the host Marketplace. The TV verifies the authorized download, signatures, package identity and compatibility, trust/admission and entitlement. Activation and consent are separate from approval.

For an update, keep the plugin ID, developer and key. Increase `versionCode` beyond the published maximum, set the new version, regenerate the listing and submit the release for review. A newer approved release can become the catalog's current version. The portal marks a rejected or changes-requested submission `superseded` when a revision is created. Existing installations are governed separately by signed trust policy. Hiding affects catalog visibility. Signed revocation can block affected installs or stop running releases when the host enforces it.

Public third-party executable distribution is gated. Coordinate an authorized review/device test path before expecting an approved package to reach a TV.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
