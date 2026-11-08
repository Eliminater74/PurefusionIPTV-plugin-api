# Local Marketplace card

Run `node tools/marketplace.cjs prepare`, then `node tools/marketplace.cjs preview` from template root and open `card.html`. No web server, network fetch or npm install is needed. `example-card.html` is the generated uncustomized reference. Regenerate after edits; source metadata stays listing-template.json in Marketplace listing v1.

The dark rounded card shows icon/publisher, descriptions, screenshots, features, compatibility, capabilities, changelog and support from that listing. This is a draft visualization, not an approval badge or pixel-for-pixel live layout promise. Images match exact listing digests; developer text is escaped; Content Security Policy disables scripts/network.
