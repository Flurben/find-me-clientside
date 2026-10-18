# In-game test checklist

Build verification completed on 2026-10-18 with Java 17 and Forge 47.3.0:

- `gradlew build`: passed; all **13** unit tests passed.
- Development-client startup without EMI: passed.
- Development-client startup with EMI 1.1.24+1.20.1: passed; integration detected.
- Packaged JAR: reobfuscated, client-only Mixin configuration and SRG refmap included; EMI not bundled.
- Dedicated-server launch reached the unaccepted EULA gate; no world was started. This does not
  constitute a full dedicated-server gameplay test.

Version 1.0.1 renderer verification: a separate singleplayer QA world was generated with nine
stacked barrels containing diamonds. All nine were opened, remembered, and matched. Screenshots
were visually checked for a front view, an angled view, third-person, Fabulous graphics at FOV 110,
and a solid wall hiding every barrel. Outlines aligned with the barrels and remained visible through
the wall. Captures are in `build/qa/rendering-1.0.1/`. This fixture is excluded from the release JAR.

Version 1.0.2 verification: world screenshots confirmed uniform cyan outlines with no tracer or
nearest-container display. Container screenshots confirmed cyan backgrounds behind two matching
diamond stacks, unchanged emerald/empty/player-inventory slots, the background following a moved
stack, and all item backgrounds disappearing when the search was cleared. Captures are in
`build/qa/rendering-1.0.2/`. The in-game query checks also rejected unrelated and empty stacks.

The checklist below remains useful for testing your actual server and modpack, particularly custom
storage blocks and shader mods.

Use the JAR from `build/libs` in a Forge 1.20.1 client. The key multiplayer check is joining a
separate server that **does not have Find Me Clientside or the original FindMe installed**.
The client mod list and handshake metadata can be inspected automatically, but actual server
joining, container association, key handling, and rendering require an in-game test.

1. **First observation:** Put diamonds in a chest you have never opened. Search a diamond from EMI
   or your hand; the chest should not be found. Open and close the chest, then search again.
   Expect a cyan outline and the correct remembered item count. Every matching location must
   use the same color, without any tracer or nearest-container details.
2. **Slot filtering:** Keep emeralds only in your own inventory while opening that chest. Searching
   emeralds must not attribute your carried inventory to the chest.
3. **Refresh:** Remove the diamonds, close the chest, and search again. The old match must disappear.
   Repeat after taking/adding items with shift-click and with a completely empty chest.
4. **Double chest:** Open both halves of a double chest containing diamonds. Expect one location
   with both blocks outlined and one combined item count. Repeat from the other half after editing it.
5. **Other inventories:** Repeat with a barrel, shulker box, hopper, and furnace. Test your modpack's
   storage blocks too; note the mod name/version if a custom menu is associated incorrectly.
6. **Ender chests:** Open two ender chests. Change contents through one, then search. Both known
   access points should use the latest contents, without doubling the shared item total.
7. **EMI:** Test Y over the item list, a favorite, a recipe output, and a tag/multi-item ingredient.
   Type text containing `y` in EMI's search box; it should type normally. Try an anvil text field too.
8. **Without EMI:** Remove EMI and start the client again. Y over ordinary inventory slots and Y
   while holding an item must still work, with no missing-class errors. Test a mouse-button binding.
9. **Persistence and isolation:** Disconnect/rejoin, then restart the client. Remembered contents
   should remain. A second server, a second singleplayer save, and a different dimension must not
   reuse the first dimension's container locations.
10. **Invalidation and stale data:** Break a remembered chest, then search while its chunk is loaded.
    It should be removed. Have another player change a closed chest: the old contents may persist
    until you reopen it, which is expected for a client-only cache.
11. **Configuration:** Test radius, NBT matching, duration, and leaving the screen open
    after success. Check `/findmeclient stats` and `/findmeclient clear`. Assign/try the clear-highlights key.
12. **Rendering:** Test through a wall, in third-person, after changing dimension, and with your actual
    shader/Embeddium/Oculus setup if installed. Markers should expire and disappear on disconnect.
13. **Item backgrounds:** Start a search and open a matching container before it expires. Every
    matching stack in that container should have a cyan background, with clear icons and counts.
    Unrelated/empty slots and player-inventory slots must stay unchanged. Move/remove a matching
    stack and confirm the backgrounds update. Test NBT matching and an EMI ingredient with multiple
    alternatives. A new search, expiry, or clearing highlights must remove the previous backgrounds.

Automated unit tests cover radius/dimension filtering, count/NBT matching, alternative ingredients,
empty-container refresh, double-chest replacement, shared ender contents, expiry/eviction,
session filename isolation, persistence round trips, and malformed-cache handling. They do not
substitute for the multiplayer and renderer checks above.

For a failure, provide the step, expected/actual behavior, container type, Forge and EMI versions,
and the relevant section of `logs/latest.log` (or a crash report). A screenshot helps with rendering.
