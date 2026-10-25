# Changelog

## 1.1.0

- Find placed blocks and fluid sources in loaded chunks with a separate keybind (Shift+Y by default), such as
  misplaced machines or an oil spring (amber outlines, nearest 512). Y still finds where items are stored.
  Chunks are scanned nearest first under a per-tick time budget, using each chunk section's palette to skip
  sections without the block.
- Add a searchable, filterable block search blacklist to the config screen. Its terrain defaults (stone, soil,
  ores, plants, water and lava, modded included) are generated from tags on joining a world, and only again
  when the mod list changes; the player's own changes are kept separately.
- Search fluids: hover a fluid in EMI or an AE2 terminal, or a filled bucket, cell, or jug. Opening a block
  records its tanks, including GregTech fluid hatches and drums that only sync while their screen is open.
- Read the contents that servers already sync to clients for loaded blocks, without opening them: GregTech
  super/quantum chests and tanks, machine and bus inventories, TerraFirmaCraft barrels, and similar.
- AE2 integration: remember a network's items and fluids when its terminal closes (violet outlines) and
  search the open terminal live. Wireless terminal networks are outlined at their linked access point. Large networks are converted off the render thread and stored in a
  separate file. Terminal grids and filter ghost slots are no longer recorded as the terminal's contents.
- Match stored entries with one hash lookup each, so tag searches stay fast against large networks.
- Cache entries now store 64-bit amounts and a fluid flag; existing cache files load unchanged.

## 1.0.4

- Reduce lag while containers are open. Open containers are re-recorded only when a slot changes,
  instead of serializing every item's NBT each tick, and the cache file is written on a background
  thread instead of the game thread.
- Load large caches faster on join and update remembered containers in constant time.
- Highlight matching item slots without serializing NBT every frame.
- Show search results only in the compact panel; the duplicate action-bar message is gone.
- Add message size and position options, with a live preview in the config screen.

## 1.0.3

- Fix searching from inside EMI's recipe and recipe tree screens. EMI's hover API does not report
  slots drawn by its own screens, so the hovered recipe slot is now read from those screens directly.
- Add an in-game config screen, opened from the Config button in the Forge mods list
  (and mod list replacements such as Catalogue that reuse it).

## 1.0.2

- Highlight the backgrounds of matching item slots when opening a container during an active search.
  Matching uses the same item/NBT rules and EMI ingredient alternatives as the container search.
- Use cyan for every container outline. Remove tracers, gold outlines, the tracer option,
  nearest-container selection, distance sorting, and the nearest-container HUD details.

## 1.0.1

- Fix displaced/distorted container highlights and tracers. Forge 1.20.1's `AFTER_LEVEL` event
  supplies a projection pose stack; drawing now uses the world-view stack at `AFTER_PARTICLES`.
- Use the particles output target so outlines are composited correctly with Fabulous graphics.
- Explicitly disable depth testing for the marker draw and restore it afterward, preserving
  through-wall visibility at the new render stage.
- Keep the existing remembered-container cache and configuration compatible.

## 1.0.0

- Initial Forge 1.20.1 client-only locator with persistent container snapshots and optional EMI hovers.
