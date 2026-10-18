# Find Me Clientside

<img src="assets/branding/icon-256.png" alt="Package search icon" width="96" height="96" />

A Forge **Minecraft 1.20.1 / Java 17** client-only item locator inspired by
[Buuz135's FindMe](https://github.com/Buuz135/FindMe/tree/1.20).
No server installation, server plugin, Architectury, JEI, or other library mod is required.
[EMI](https://github.com/emilyploszaj/emi) is optional.

[Download releases](https://github.com/Flurben/find-me-clientside/releases) ·
[Report a bug](https://github.com/Flurben/find-me-clientside/issues) ·
[AI disclosure](AI_DISCLOSURE.md)

## Install and use

1. Install Forge for Minecraft **1.20.1** (Forge **47.1 or newer in the 47.x series**).
2. Put `findmeclientside-forge-1.20.1-1.0.2.jar` from `build/libs` in your game's `mods` folder.
3. Join a server or singleplayer world and **open the containers you want remembered**.
4. Hover an inventory item or an EMI item and press **Y**. In the world, Y searches your held item
   (main hand, then offhand). The binding is configurable under Controls → Find Me Clientside.
5. Matching containers receive cyan outlines visible through walls. The HUD shows the number of
   remembered locations and items last seen. Open a container during the search to highlight the
   backgrounds of matching item slots in cyan; the item icons and counts stay fully visible.

The inventory/recipe screen closes after a successful search by default. Failed searches leave it open.
Typing into the EMI search box or a vanilla text field does not trigger a search.
EMI item-list, favorite, recipe, and ingredient hovers use EMI's public API. Multi-item ingredients
search all item alternatives. Fluid-only ingredients are not searchable.

### The client-only limit

**An unmodified server does not send unopened container contents to the client.** This mod searches
remembered snapshots of containers you have opened; it cannot perform the original FindMe's live
server-side scan or remotely pull items. Another player, hopper, or machine can change a closed
container without your client being told. Reopen it to refresh the cache. Counts describe your latest
observation, not a live guarantee.

Normal chests, double chests, barrels, shulker boxes, ender chests, hoppers, dispensers, droppers,
furnaces, and other block inventories exposing ordinary menu slots can be remembered. Double chests
are recorded once from either half. Known ender-chest access points share this player's latest
contents; totals do not count those shared items repeatedly. Player inventory slots are excluded.
Entity inventories (minecarts, boats, horses), item-opened backpacks, and server-opened virtual menus
without an associated block interaction are not recorded. Custom network terminals, ghost slots,
and menus with unusual synchronization may need dedicated integration.

Only your current dimension and the configured radius are searched. Loaded blocks that no longer
match the remembered block type are discarded. An unloaded location or same-type replacement may
remain stale until reopened. A server with several worlds behind the same address **and dimension
ID** cannot be distinguished reliably by a pure client; use `/findmeclient clear` after such switches
or a world reset. Different server addresses, singleplayer save paths, player UUIDs, and dimensions
are kept separate.

## Configuration and cache

The first launch creates `config/findmeclientside-client.toml` in the game directory.

| Option | Default | Purpose |
| --- | --- | --- |
| `search.radius` | 64 | Maximum distance in blocks (1–512) |
| `search.highlightSeconds` | 12 | Duration of outlines, HUD, and item-slot backgrounds |
| `search.matchNbt` | false | Require exact item NBT, including damage/enchantments |
| `search.closeScreenOnSuccess` | true | Close inventory/recipe screens to see markers |
| `cache.persist` | true | Load/save remembered contents between sessions |
| `cache.maxContainers` | 4096 | Maximum remembered inventories per server/world and player |
| `cache.maxAgeDays` | 30 | Expire old observations |

Persistent cache files live in `<game directory>/findmeclientside/`, with filenames hashed from the
server address or singleplayer save path plus player UUID. Files contain container positions and
item data in readable JSON. Changes save about every five seconds and on disconnect, using atomic
replacement where supported. Unreadable files are preserved with an `.unreadable-...` suffix.
Disabling persistence ignores existing files; it does not erase them. Restart after changing that option.

Client commands (handled locally):

- `/findmeclient stats` — remembered container count, integration, and search radius.
- `/findmeclient clear` — erase the current server/world and player's remembered contents.

You can assign a separate **Clear search highlights** key in Controls. It removes outlines and item-slot
backgrounds. Slot backgrounds apply to the container's own slots, update as items move, and use the
same NBT setting and EMI item alternatives as the search. A new search replaces the highlighted item;
expiry, clearing, a dimension change, or disconnect removes the highlights. Version 1.0.2 removes the
old `search.showTracer` option.

## Build

Use a **JDK 17** installation and the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The distributable, reobfuscated mod is `build/libs/findmeclientside-forge-1.20.1-1.0.2.jar`.
ForgeGradle, Forge 47.3.0, Mixin, and EMI 1.1.24+1.20.1 are pinned for development.
EMI is a compile-only API plus a development runtime dependency; it is **not bundled** into the mod.

```powershell
.\gradlew.bat runClient                  # development game with EMI
.\gradlew.bat runClient -PwithoutEmi     # verify the game without EMI
.\gradlew.bat test                       # cache and persistence regression tests
```

Implementation: a client-only Mixin observes vanilla's initial `AbstractContainerMenu.initializeContents`
after the server data is applied. Forge interaction events associate the menu with the clicked block.
Client ticks refresh the observed menu while it is open. No custom networking channel or search
request packet is registered. The mod accepts servers that do not advertise it and initializes no
client handlers on a dedicated server. Marker rendering is capped at 256 locations per search.
Outlines render during Forge's `AFTER_PARTICLES` stage with the world camera transform and the
particles render target, including Fabulous graphics. Version 1.0.1 fixes the misplaced outlines
caused by using the projection stack supplied at `AFTER_LEVEL` in Forge 1.20.1.

See [TESTING.md](TESTING.md) for the in-game test checklist and the limitations of automated checks.

## Credits and license

Inspired by **FindMe by Buuz135** (CC0-1.0); this is an independent client-cache implementation with a
different mod ID, not an official FindMe release. Distributed under **CC0-1.0**; see [LICENSE](LICENSE).
The Gradle wrapper retains its own Apache 2.0 notices.
The package-search icon is from Lucide under ISC; see [third-party notices](THIRD_PARTY_NOTICES.md).
Maintained by [Flurben](https://github.com/Flurben). The code and documentation were substantially
generated with Codex; see [AI_DISCLOSURE.md](AI_DISCLOSURE.md).

Release preparation and the unlisted Modrinth package are documented in [publishing/README.md](publishing/README.md).
