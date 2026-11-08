# Submission, review and updates

Use [the developer portal](https://purefusioniptv.com/developer/) with an enabled verified-email author account. Its assigned developer ID and namespace must match the manifest/listing. A display publisher or local signature cannot claim another identity. First verified submission establishes ownership/key binding through the authorized workflow; updates retain identity/key.

1. Select **Submit New Plugin**. Enter fields manually using [Listing Guide](../marketplace/LISTING_GUIDE.md). The current UI has no JSON import control.
2. Select the developer-signed `.pfp`, icon and screenshots; enter alt text/captions. The portal computes descriptors and uploads to quarantine. Server archive, signature and decoded-image checks must pass.
3. Supply separate **Rights and licensing disclosure**, including data/media/artwork rights. Developer website/privacy links can go in fullDescription/support content; listing v1 has no dedicated fields. Use [Submission Worksheet](../marketplace/SUBMISSION_WORKSHEET.md).
4. Submit for independent review. Publisher distribution eligibility and audience are server-managed. Ordinary authors cannot self-approve. Corrections/rejection return reasons. Passing local checks is not approval.
5. After approval/release availability, eligible users receive a catalog listing and separate signed download grant. TVs verify signatures, hashes, identity, compatibility, entitlement and signed admission, then obtain TV-owned consent.

**Public third-party executable distribution is currently gated.** Restricted production examples do not make public distribution available to every developer. Build/test locally and coordinate authorized review/device testing through the portal/operator process. This kit never submits automatically.

Generic third-party surface launch/discovery is also not available: current host UI exposes Sports Hub's reserved slot only. An arbitrary submitted plugin ID requires authorized host launch integration before its screen can be opened on a TV. Marketplace approval does not create a menu entry automatically.

Updates use **Update Existing Plugin**: preserve plugin/developer/key, increase versionCode, change version, provide changelog, rebuild/re-sign and regenerate listing. Leave artwork controls blank only to retain approved assets. Each release is reviewed separately. Hiding removes visibility; signed revocation also blocks admission/unloads affected plugins. They are distinct operations.
