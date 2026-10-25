# Changelog

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
