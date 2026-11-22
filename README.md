# Shipwright (Fabric, Minecraft Java 26.3)

Turn your own builds into working ships. Everything stays real blocks, so you can still
break, place and use chests, furnaces, doors, etc. while sailing.

## Build
Needs **JDK 25**. No Gradle wrapper is bundled (it can't be downloaded offline), so either
copy `gradlew`, `gradlew.bat` and `gradle/` from https://github.com/FabricMC/fabric-example-mod (branch 26.3),
or run `gradle wrapper --gradle-version 9.6.0` once. Then:

    ./gradlew build          # jar ends up in build/libs/
    ./gradlew runClient      # test in-game

Versions live in `gradle.properties` (Loom 1.17, Loader 0.19.5, Fabric API 0.164.0+26.3).
Check https://fabricmc.net/develop for newer ones. `fabric.mod.json` allows Minecraft >= 26.3.

## How to build a ship
1. Build a hull on water and put a **Ship Core** inside/on it. Everything face-connected to the core
   (except terrain: dirt, sand, stone, ice, gravel, kelp...) is the ship. Right-click the core for a status report.
2. Place a **Ship's Wheel**. It faces the way you looked when placing it: that is the bow.
3. Place **Engines**, give them fuel (right-click: 5-slot fuel UI; coal, charcoal, wood, lava bucket, blaze rod...).
   A redstone signal switches an engine off.
4. Run **Drive Shafts** from the engine to a **Propeller**. Shafts carry power along their axis and can pass
   through up to **2 wall blocks**, so propellers can sit behind blocks and still get torque. A propeller
   can also be mounted straight onto an engine. Each running engine powers 4 propellers.
5. Fuelled engines make the propellers spin slowly. Right-click the wheel to take the helm:

| Key | Action |
|---|---|
| W / S | throttle up / down (S through zero stops at a wide, sticky 0 zone) |
| A / D | turn the whole ship 90 degrees (1.5 s cooldown) |
| Space | full stop |
| Shift | leave the wheel |

6. **Ship Anchor**: right-click to drop/raise. A ship with any dropped anchor can't move or turn.

Speed: one powered propeller per ~40 hull blocks gives full speed; fewer is slower (min 25%).
All numbers are constants at the top of `ShipCoreBlockEntity.java`.

## Restyling
`python3 tools/generate_assets.py` (needs `pip install pillow`) regenerates every texture, model,
blockstate, recipe and loot table.

## Known limits (honest list)
* Block-shift movement is stepped (1 block at a time), not smooth, and the ship turns in 90 degree steps.
* The engine UI is the vanilla hopper-style screen (no custom client code = nothing to break between versions).
* Players standing on deck are teleported each step, so you may feel small hitches; the helmsman rides a seat and is smooth.
* Not compiled by the author. If the build fails, the likely spots are listed in the chat reply.

## Getting the .jar without installing anything
Push this folder to a GitHub repo. The included workflow (.github/workflows/build.yml) builds it and
attaches `shipwright-1.0.0.jar` to the run under Actions > Artifacts. Put that jar (not the -sources one)
in your `mods` folder together with Fabric API 0.164.0+26.3.
