// Vendored public listing v1 validator. See CONTRACT_SOURCE.md.
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MarketplaceListingError = exports.MAX_MARKETPLACE_CATALOG_ROWS = void 0;
exports.isMarketplacePluginId = isMarketplacePluginId;
exports.validateMarketplaceListing = validateMarketplaceListing;
exports.bindMarketplaceListing = bindMarketplaceListing;
exports.requireMarketplacePublicationEvidence = requireMarketplacePublicationEvidence;
exports.requireMarketplaceVersion = requireMarketplaceVersion;
exports.projectApprovedMarketplaceItem = projectApprovedMarketplaceItem;
const node_crypto_1 = require("node:crypto");
const CATEGORIES = new Set([
    "epg_metadata", "service_integration", "ui_extension", "media_server",
    "sync", "theme_pack", "runtime_extension", "subtitle_provider",
]);
const CAPABILITIES = new Set([
    "EPG_READ", "EPG_WRITE", "PLAYLIST_IMPORT", "METADATA_READ", "CATCHUP_PROVIDER",
    "MEDIA_SERVER_ACCESS", "SYNC_READ", "SYNC_WRITE", "CLOUD_STORAGE_ACCESS",
    "UI_SETTINGS_EXTENSION", "UI_HOME_EXTENSION", "UI_PLAYER_ACTION",
    "NETWORK_EXTERNAL", "FILE_EXPORT", "BACKGROUND_SYNC", "ANALYTICS_EVENT_WRITE", "PLUGIN_SCOPED_STORAGE",
]);
const FORMATS = new Set(["image/png", "image/jpeg", "image/webp"]);
const PUBLIC_TRUST_TIERS = new Set([
    "first_party", "verified_partner", "community_reviewed", "local_untrusted",
]);
const PLUGIN_ID = /^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/;
const LEGACY_PLUGIN_ID = /^[A-Za-z][A-Za-z0-9._-]{0,127}$/;
const VERSION = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,31}$/;
const HOST_VERSION = /^[0-9]{1,2}(?:\.[0-9]{1,2}){0,2}$/;
const HOST_MAJOR_WILDCARD = /^[0-9]{1,2}\.x$/;
const SHA256 = /^[0-9a-f]{64}$/;
const SUPPORT_URL = /^https:\/\/(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:[/?#][\x21-\x7e]*)?$/;
const MAX_INT = 2147483647;
exports.MAX_MARKETPLACE_CATALOG_ROWS = 250;
function isMarketplacePluginId(pluginId) {
    return pluginId.length <= 128 && PLUGIN_ID.test(pluginId);
}
class MarketplaceListingError extends Error {
    constructor(path, problem) {
        super(path + ": " + problem);
    }
}
exports.MarketplaceListingError = MarketplaceListingError;
function invalid(path, problem) {
    throw new MarketplaceListingError(path, problem);
}
function record(raw, path, keys) {
    if (raw === null || typeof raw !== "object" || Array.isArray(raw)) {
        invalid(path, "expected an object");
    }
    const value = raw;
    if (keys) {
        for (const key of Object.keys(value)) {
            if (!keys.includes(key)) {
                const field = /^[A-Za-z][A-Za-z0-9_]{0,39}$/.test(key) ? "." + key : "";
                invalid(path + field, "unexpected field");
            }
        }
    }
    return value;
}
function text(raw, path, max, multiline = false) {
    if (typeof raw !== "string" || !raw || raw.length > max || raw.trim() !== raw ||
        /[<>\u0000-\u0009\u000b-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/u.test(raw) ||
        (!multiline && raw.includes("\n"))) {
        invalid(path, "expected 1–" + max + " characters of plain text");
    }
    return raw;
}
function pattern(raw, path, max, regex) {
    const value = text(raw, path, max);
    if (!regex.test(value))
        invalid(path, "invalid format");
    return value;
}
function integer(raw, path, min, max) {
    if (typeof raw !== "number" || !Number.isInteger(raw) || raw < min || raw > max) {
        invalid(path, "expected an integer from " + min + " to " + max);
    }
    return raw;
}
function identifier(raw, path, allowed) {
    const value = text(raw, path, 64);
    if (!allowed.has(value))
        invalid(path, "unrecognized identifier");
    return value;
}
function array(raw, path, min, max) {
    if (!Array.isArray(raw) || raw.length < min || raw.length > max) {
        invalid(path, "expected " + min + "–" + max + " entries");
    }
    return raw;
}
function hostVersionNumber(value) {
    const [major, minor = "0", patch = "0"] = value.split(".");
    return Number(major) * 10000 + Number(minor) * 100 + (patch === "x" ? 0 : Number(patch));
}
function artwork(raw, path, icon) {
    const keys = ["assetId", "format", "width", "height", "byteSize", "altText"];
    const item = record(raw, path, icon ? keys : [...keys, "caption"]);
    const width = integer(item.width, path + ".width", icon ? 128 : 640, icon ? 1024 : 3840);
    const height = integer(item.height, path + ".height", icon ? 128 : 360, icon ? 1024 : 2160);
    if (icon ? width !== height : (width * 5 < height * 8 || width > height * 2)) {
        invalid(path, icon ? "expected a square icon" : "expected a landscape image between 1.6:1 and 2:1");
    }
    return {
        assetId: pattern(item.assetId, path + ".assetId", 64, SHA256),
        format: identifier(item.format, path + ".format", FORMATS),
        width,
        height,
        byteSize: integer(item.byteSize, path + ".byteSize", 1, icon ? 524288 : 2097152),
        altText: text(item.altText, path + ".altText", 160),
        ...(item.caption !== undefined ? { caption: text(item.caption, path + ".caption", 160) } : {}),
    };
}
const MANIFEST_IDENTITY_FIELDS = [
    "pluginId", "displayName", "developerId", "version", "versionCode", "category",
    "apiLevel", "minHostVersion", "maxHostVersion", "minSdk",
];
function validateMarketplaceListing(raw) {
    const item = record(raw, "listing", [
        "schemaVersion", ...MANIFEST_IDENTITY_FIELDS, "publisher", "shortDescription",
        "fullDescription", "capabilities", "icon", "screenshots", "features",
        "changelog", "support",
    ]);
    integer(item.schemaVersion, "listing.schemaVersion", 1, 1);
    const minHostVersion = pattern(item.minHostVersion, "listing.minHostVersion", 8, HOST_VERSION);
    let maxHostVersion;
    if (item.maxHostVersion !== undefined) {
        maxHostVersion = text(item.maxHostVersion, "listing.maxHostVersion", 8);
        if (!HOST_VERSION.test(maxHostVersion) && !HOST_MAJOR_WILDCARD.test(maxHostVersion)) {
            invalid("listing.maxHostVersion", "invalid format");
        }
        const max = maxHostVersion.endsWith(".x") ?
            Number(maxHostVersion.split(".")[0]) * 10000 + 9999 : hostVersionNumber(maxHostVersion);
        if (max < hostVersionNumber(minHostVersion)) {
            invalid("listing.minHostVersion", "exceeds maxHostVersion");
        }
    }
    const capabilities = array(item.capabilities, "listing.capabilities", 0, CAPABILITIES.size)
        .map((value, index) => identifier(value, "listing.capabilities[" + index + "]", CAPABILITIES));
    if (new Set(capabilities).size !== capabilities.length) {
        invalid("listing.capabilities", "duplicate capability");
    }
    const icon = artwork(item.icon, "listing.icon", true);
    const screenshots = array(item.screenshots, "listing.screenshots", 1, 8)
        .map((value, index) => artwork(value, "listing.screenshots[" + index + "]", false));
    if (new Set([icon, ...screenshots].map(image => image.assetId)).size !== screenshots.length + 1) {
        invalid("listing.screenshots", "duplicate artwork asset");
    }
    const support = record(item.support, "listing.support", ["url"]);
    const supportUrl = pattern(support.url, "listing.support.url", 512, SUPPORT_URL);
    if (/[\\<>"'{}|^]/.test(supportUrl))
        invalid("listing.support.url", "invalid HTTPS support link");
    const pluginId = text(item.pluginId, "listing.pluginId", 128);
    if (!isMarketplacePluginId(pluginId))
        invalid("listing.pluginId", "expected a reverse-DNS ID");
    return {
        schemaVersion: 1,
        pluginId,
        displayName: text(item.displayName, "listing.displayName", 80),
        developerId: text(item.developerId, "listing.developerId", 80),
        version: pattern(item.version, "listing.version", 32, VERSION),
        versionCode: integer(item.versionCode, "listing.versionCode", 1, MAX_INT),
        publisher: text(item.publisher, "listing.publisher", 80),
        shortDescription: text(item.shortDescription, "listing.shortDescription", 400),
        fullDescription: text(item.fullDescription, "listing.fullDescription", 4000, true),
        category: identifier(item.category, "listing.category", CATEGORIES),
        apiLevel: integer(item.apiLevel, "listing.apiLevel", 1, MAX_INT),
        minHostVersion,
        ...(maxHostVersion === undefined ? {} : { maxHostVersion }),
        minSdk: integer(item.minSdk, "listing.minSdk", 1, 200),
        capabilities,
        icon,
        screenshots,
        ...(item.features === undefined ? {} : {
            features: array(item.features, "listing.features", 0, 8)
                .map((value, index) => text(value, "listing.features[" + index + "]", 120)),
        }),
        ...(item.changelog === undefined ? {} : {
            changelog: text(item.changelog, "listing.changelog", 2000, true),
        }),
        support: { url: supportUrl },
    };
}
function bindMarketplaceListing(listing, identity, manifestJson, manifestSha256) {
    for (const key of ["pluginId", "version", "versionCode"]) {
        if (identity[key] !== listing[key])
            invalid("listing." + key, "differs from release identity");
    }
    if (typeof manifestJson !== "string" || manifestJson.length > 262144 ||
        Buffer.byteLength(manifestJson, "utf8") > 262144) {
        invalid("manifestJson", "expected a manifest of at most 256 KiB");
    }
    const actualDigest = (0, node_crypto_1.createHash)("sha256").update(manifestJson, "utf8").digest("hex");
    if (typeof manifestSha256 !== "string" || actualDigest !== manifestSha256.toLowerCase()) {
        invalid("manifestSha256", "does not match the exact manifest bytes");
    }
    let parsed;
    try {
        parsed = JSON.parse(manifestJson);
    }
    catch {
        invalid("manifestJson", "malformed JSON");
    }
    const manifest = record(parsed, "manifestJson");
    integer(manifest.schemaVersion, "manifestJson.schemaVersion", 1, 1);
    for (const key of MANIFEST_IDENTITY_FIELDS) {
        const value = key === "maxHostVersion" ? manifest[key] ?? undefined : manifest[key];
        if (value !== listing[key])
            invalid("listing." + key, "differs from package manifest");
    }
    const declared = manifest.capabilities === undefined ? [] : manifest.capabilities;
    const capabilities = array(declared, "manifestJson.capabilities", 0, CAPABILITIES.size);
    if (capabilities.length !== listing.capabilities.length ||
        new Set(capabilities).size !== capabilities.length ||
        !listing.capabilities.every(capability => capabilities.includes(capability))) {
        invalid("listing.capabilities", "differs from package manifest");
    }
    return listing;
}
function requireMarketplacePublicationEvidence(listing, raw, manifestSha256, packagePath) {
    const path = "verification";
    const receipt = record(raw, path, ["manifestSha256", "packagePath", "artwork"]);
    if (receipt.manifestSha256 !== manifestSha256.toLowerCase() || receipt.packagePath !== packagePath) {
        invalid(path, "package verification does not match this release");
    }
    const assets = [listing.icon, ...listing.screenshots];
    const approved = array(receipt.artwork, path + ".artwork", assets.length, assets.length)
        .map(image => record(image, path + ".artwork", ["assetId", "format", "width", "height", "byteSize"]));
    const byId = new Map(approved.map(image => [image.assetId, image]));
    for (const asset of assets) {
        const image = byId.get(asset.assetId);
        if (!image || image.format !== asset.format || image.width !== asset.width ||
            image.height !== asset.height || image.byteSize !== asset.byteSize) {
            invalid(path, "artwork verification does not match the listing");
        }
    }
}
function requireMarketplaceVersion(listing, highestPublished) {
    if (listing.versionCode <= highestPublished) {
        invalid("listing.versionCode", "must exceed the highest published versionCode");
    }
    if (highestPublished > 0 && listing.changelog === undefined) {
        invalid("listing.changelog", "required for an update");
    }
}
function legacyText(value, max) {
    return typeof value === "string" ? value.slice(0, max).trim() : "";
}
function projectApprovedMarketplaceItem(pluginId, plugin, version) {
    if (plugin.status !== "approved" || version.reviewState !== "approved" ||
        version.revoked !== false || !LEGACY_PLUGIN_ID.test(pluginId))
        return null;
    try {
        const versionCode = integer(version.versionCode, "versionCode", 1, MAX_INT);
        const latestVersion = legacyText(version.version, 32);
        const developerId = legacyText(plugin.developerId, 80);
        const trustTier = typeof plugin.trustTier === "string" && PUBLIC_TRUST_TIERS.has(plugin.trustTier) ?
            plugin.trustTier : "";
        const listing = Object.prototype.hasOwnProperty.call(version, "listing") ? bindMarketplaceListing(validateMarketplaceListing(version.listing), { pluginId, version: version.version, versionCode }, version.manifestJson, version.manifestSha256) : undefined;
        if (listing && listing.developerId !== developerId)
            return null;
        return {
            pluginId,
            displayName: listing?.displayName ?? (legacyText(plugin.displayName, 80) ||
                legacyText(plugin.pluginId, 80) || pluginId),
            shortDescription: listing?.shortDescription ?? legacyText(plugin.shortDescription, 400),
            developerId,
            trustTier,
            latestVersion,
            latestVersionCode: versionCode,
            ...(listing === undefined ? {} : { listing }),
        };
    }
    catch (error) {
        if (error instanceof MarketplaceListingError)
            return null;
        throw error;
    }
}
