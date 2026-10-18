# Find Me Clientside

Find that item in your storage without installing anything on the server.

**Find Me Clientside** remembers containers you open, then highlights where an item was last seen.
It runs entirely on your **Forge 1.20.1 client**, with optional EMI support.

## How to use it

1. Open the chests, barrels, or other containers you want remembered.
2. Hover an item in your inventory or EMI and press **Y**. You can also press Y while holding the item.
3. Look for the **cyan outlines**, visible through walls.
4. Open a matching container while the search is active to see **cyan backgrounds behind matching
   item slots**. Icons and stack counts stay readable.

The key is configurable under **Controls → Find Me Clientside**. Searches last 12 seconds and cover
64 blocks by default; change either in `config/findmeclientside-client.toml`. Inventory/recipe screens
close after successful searches by default, with an option to keep them open.

## Features

- Client-only operation on servers that do not have the mod installed.
- Inventory hovers, held items, and EMI item-list, favorite, and recipe hovers.
- EMI ingredient alternatives; EMI itself is optional.
- Uniform cyan container outlines and matching item-slot backgrounds.
- Persistent observations, separated by server/save, player UUID, and dimension.
- Double chests count as one inventory; known ender-chest access points share your latest contents.
- Configurable radius, duration, exact-NBT matching, cache size, and expiry.
- Local commands: `/findmeclient stats` and `/findmeclient clear`.

## What client-only searching can know

**Open a container before this mod can remember it.** Servers do not normally send unopened storage
contents to clients. Results are remembered snapshots, not live server scans. Another player,
hopper, or machine can change a closed container; reopen it to refresh.

Vanilla block inventories and modded containers exposing ordinary synchronized item slots can work.
Entity inventories, item-opened backpacks, fluid-only ingredients, and virtual menus without an
associated block interaction are not supported. Custom network terminals and ghost slots may need
specific integration. This mod does not remotely pull items.

Searches use your current dimension. If a server resets or switches between worlds behind the same
address and dimension ID, clear old observations with `/findmeclient clear`.

## Requirements

- **Minecraft Java Edition 1.20.1**
- **Forge 47.1+ in the 47.x series** and **Java 17**
- **EMI 1.1+ is optional**; no Architectury or JEI dependency
- Install on the **client only**; no server installation is needed

## Source, credits, and licensing

Maintained by [Flurben](https://github.com/Flurben).
[Source and issues](https://github.com/Flurben/find-me-clientside).

Inspired by [FindMe by Buuz135](https://github.com/Buuz135/FindMe), licensed CC0-1.0. This is an
independent client-cache implementation, not an official FindMe release. Code is offered under
**CC0-1.0**; the existing Lucide package-search icon is **ISC**, with its notices included. You may
include this mod in modpacks under those licenses.

Code and release text were substantially generated with Codex, with direction and game testing
from the maintainer. See the [AI disclosure](https://github.com/Flurben/find-me-clientside/blob/main/AI_DISCLOSURE.md).
The prepared Modrinth submission is intended to be **unlisted** with code/text disclosures.
