# Find Me Clientside 1.0.2 — Forge 1.20.1

First prepared release of a client-only storage finder with optional EMI support.

- Find items in containers you have opened, using Y over an inventory/EMI item or while holding it.
- Cyan outlines show matching storage through walls; matching item slots receive cyan backgrounds.
- Persistent observations are separated by server/save, player UUID, and dimension.
- Double-chest and shared ender-chest handling; optional exact-NBT matching.
- No server installation, Architectury, or JEI dependency. EMI is optional.
- Corrected Forge 1.20.1 camera transform. Tracers and nearest-container special styling removed.

Requires Minecraft **1.20.1**, Forge **47.1+ (47.x)**, and Java **17**.
Replace any earlier Find Me Clientside JAR; existing cache/configuration remain compatible.

**Client-only limit:** containers must be opened first. Closed-container snapshots may become stale;
reopen them to refresh. Default search duration is 12 seconds and radius is 64 blocks.

Validation: 13 automated cache/persistence tests; development startup with/without EMI; actual
in-game outline alignment, through-wall rendering, matching slot backgrounds, moved stacks, and
clearing highlights. Custom modpack menus and shader combinations may still need testing.

Code and text were substantially generated with Codex. See the source repository's AI disclosure.
The Modrinth package is prepared for **unlisted** submission with code/text disclosures.
