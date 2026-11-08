# Optional preferences and website settings

The working example uses the supported `pf.surface.v1` **form** descriptor: text greeting and numeric counter step. Host dialogs render it, return `values` on `submit`, and scoped storage persists validated changes. Defaults: greeting `Welcome to your plugin`, step `1`, count `0`. Choice descriptors can represent selection/toggle interactions; this example implements text/numeric controls. The protocol does not accept arbitrary Views.

These are in-screen non-secret preferences. They do not advertise a generic Plugin Hub or website Settings button. Remove the `preferences` card, handler and `preferences()` to remove them. If removing persistence too, remove broker storage calls and `PLUGIN_SCOPED_STORAGE` from manifest and regenerate listing. A plugin without an advertised supported settings contract has no generic Settings action.

Current website schema: declarative v1 (`schemaVersion`, `revision`, `fields` with text/url/number/toggle/select types). Core accepts it only through a reviewed host adapter bound to exact plugin, developer key and manifest. API 3 has **no generic apply-configuration method**; the registry defaults to no adapters. A settings-schema JSON file in a `.pfp` cannot register an adapter. This template therefore documents that schema boundary rather than shipping a fake remote configuration example. Public delivery requires further SDK/host work and review.

API-key/password/OAuth delivery is security-gated and unavailable to this example. Never bypass it with hidden text fields, URL tokens, scoped storage, logs or host-private files.
