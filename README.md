# Simple Machines

Machines, metals and parts for Minecraft 26.1 to 26.1.2, NeoForge and Fabric.

Contents so far:

- Alloy Furnace: two inputs, fuel and one output. Copper ingot and redstone make a Redstone Copper Ingot, iron ingot and 2 coal or charcoal make a Steel Ingot. Burns fuel and has no hopper sides.
- Extruder: one input, fuel and one output. One iron or copper ingot makes 2 rods. Slow, burns fuel, no hopper sides, and only takes items it has a recipe for.
- Iron Chassis (tier 1) and Steel Chassis (tier 2): the base every powered machine is built on. Steel machines run 1.5x faster and hold twice the energy.
- Solid Fuel Generator: burns fuel into FE, 40 FE/t on iron, 60 FE/t on steel.
- Rolling Mill: 20 FE/t, rolls iron, copper and steel ingots into 2 rods, or iron and copper into casings. The button in its screen switches between rods and casings.
- Electric Furnace: 20 FE/t, normal smelting recipes, a bit faster than a furnace.
- Redstone Copper Cable: carries up to 1000 FE/t, connects to anything that takes or gives FE.
- Parts: an assembly for each machine, control board, panels and worktops, screwdriver.
- Finishes for panels and worktops: the twelve woods, the sixteen concrete colours, iron, copper and steel.
- Steel ingot, Redstone Copper Ingot, iron, copper and steel rods, iron, copper, gold and steel plates, iron and copper casings.
- Battery: stores 500k FE on iron, 1M FE on steel. Takes FE on every side but the front and gives it out of the front, up to 1000 FE/t (2000 on steel). Keeps its charge as an item, so a charged battery can be carried and placed.
- Crusher: 20 FE/t. Ores and raw ores give 2 dusts (a steel crusher has a 10% chance of a third), ingots give 1 dust, cobblestone gives gravel and gravel gives sand. Dusts smelt or blast back into ingots. Works for any metal whose `c:ores`, `c:raw_materials` and `c:dusts` tags are filled by some mod; iron, copper and gold dusts are included.
- Sawmill: 20 FE/t. A log or stem gives 6 planks, a bamboo block 3, planks give 3 sticks.
- Alloy Smelter: 30 FE/t, the powered Alloy Furnace. Same recipes in half the time, no fuel.
- Crop Harvester: harvests grown crops in a 5x5 area in front of it (9x9 on steel) and replants them, 400 FE a crop.
- Shearer: shears sheep in the same area, 500 FE a sheep.
- Stone Generator: makes cobblestone when water and lava touch it, 10 FE/t.
- Water Generator: fills buckets when it touches 2 water sources, 10 FE/t.
- Mechanical Hive: 20 FE/t, makes honeycomb, and honey bottles from glass bottles.
- Charger: charges FE items placed in it, 500 FE/t (1000 on steel).
- Item Pipe: moves items between inventories, see below.
- Electric Drill and Chainsaw, see below.
- Overclocker Circuit and Energy Cell machine upgrades, see below.
- Compressor, Auto Crafter, Lava Generator, Solar Panels, Wind Turbine, Fluid Tank, fluid pipes, pipe filters, MV and HV cables, transformers and the Quarry, see below.
- Entangled Programmer, Link Card, Entangled Buffer, Tank, Relay, Pad and Backpack, and Chunk Loader and Range Modules, see below.

Powered machines keep their stored energy when broken, and a comparator reads how full they are.

## Building a machine

Place a chassis, then right click it with each part in turn and use the screwdriver to fix it:

1. A mechanical assembly, which decides the machine.
2. A control board.
3. Up to two upgrades (optional).
4. Panels for the sides.
5. A worktop on the open top.

Each part sits loose until it is screwed in. Wood panels come from four slabs, concrete panels from four concrete (makes two), worktops from three slabs or three concrete (makes two). Metal ones come from sheets made in the Rolling Mill. Sneak and use the screwdriver to take the last part off again. Breaking a part-built chassis drops its parts.

## Pipes and output sides

Item pipes connect to each other and to anything with an inventory. Use any screwdriver on a pipe end next to an inventory to cycle it through input (the default; the pipe delivers into it), output (the pipe pulls items out every half second and sends them round the network in turn to every other input on it) and disconnected (the pipe ignores that block, for example a cabinet sitting under a pipe meant for the machines either side). Machines only accept items they have a recipe for, and only give up their outputs.

Use a screwdriver (without sneaking) on a side of a built machine to make it push its outputs into whatever is on that side, such as a chest or a cupboard. Sides that output or have a pipe on them show a port. All sides are off to start with. Hoppers and other mods see inputs and outputs the same way: inputs can be filled, outputs can be emptied.

Use a screwdriver on a side of a Redstone Copper Cable to disconnect that side, or connect it again. It works on sides facing other cables and sides facing machines, so two runs laid next to each other stay separate networks.

Right-click a placed cable or item pipe with any dye to colour it (survival uses one dye). Coloured cables and pipes only join others of the same colour or uncoloured ones, so parallel runs in different colours never mix. Colour has no effect on connections to machines and inventories. Colour lives in the world only: a broken cable or pipe drops as the plain item.

## Upgrades

After the control board is screwed in, right click with an Overclocker Circuit or Energy Cell and screw it in. A machine has two upgrade slots and any mix fits; the step can be skipped. Each Overclocker adds half the base speed, and FE use and intake rise with it, so two run at double speed. Each Energy Cell adds the base storage again, so two hold triple. Upgrades stay in the machine when it is broken and carried, show in its tooltip and screen, and drop back when it is taken apart.

## Powered tools

The Electric Drill (pickaxe and shovel blocks) and Chainsaw (axe blocks) are built on a Tool Stand rather than crafted. Place the stand and fit the parts in order, right clicking with each and then with a screwdriver:

1. Tool frame (iron or steel)
2. Core: a Drill Motor makes a drill, a Chainsaw Bar a chainsaw
3. Control board
4. Battery module
5. Upgrades, optional, up to two: Overclocker Circuit or Energy Cell

Any time after the core, a Diamond Drill Bit (drill) or Diamond-Tipped Chain (chainsaw) can be fitted and screwed on in place of the standard one.

Take the finished tool off with an empty hand. To change one, put it back on an empty stand and sneak with the screwdriver: the last part comes off and drops, so you can strip it down and swap the core, frame or upgrades. The battery module keeps its charge. Breaking the stand drops it and whatever is on it.

A tool holds 50k FE and uses 50 FE a block. When empty it is slow and drops nothing. Charge it in the Charger. FE on NeoForge, Team Reborn Energy on Fabric.

Speed and tiers:

| | Effect |
|---|---|
| Steel frame | x1.25 speed |
| Overclocker | +50% speed and +50% FE per block each |
| Energy Cell | +50k FE and +15% speed each, no extra FE |
| Upgrades together | capped at +100% speed |
| Standard bit | mines up to diamond ore; obsidian and netherite level blocks are refused with white-hot sparks |
| Diamond Drill Bit | mines obsidian and netherite level blocks: full speed on a steel frame, 40% on an iron frame |
| Diamond-Tipped Chain | on a steel frame, the chainsaw fells a whole natural tree, top down, 50 FE per log, logs drop in the world; sneak to cut one log |

The parts show on the tool: a yellow drill with a tricone bit (standard or diamond), steel or iron frame, the control board behind a side panel, a heat sink per Overclocker, a bigger battery pack per Cell.

While digging, the bit's three cones turn, the chainsaw chain runs, the Overclocker fins glow, a motor sound loops (with subtitles), long streaks of hot steel sparks fly off stone and ore, and dirt, sand, gravel and wood throw bits of the block.

## Manual (Simple Books)

With Simple Books installed, getting your first screwdriver (from any mod) gives you the Simple Machines Manual, a blueprint style guide to every machine, the assembly steps with recipes, tiers, finishes, cables, pipes, auto-output, upgrades and tools. Another copy is crafted from a book, any screwdriver and a copper ingot; the screwdriver is given back. Simple Books is optional; without it the manual, its recipe and advancement are simply not loaded.

## Much More Storage and other mods

- Worktops are tagged `c:worktops`. Any worktop in that tag fits a chassis, and Simple Machines worktops fit Much More Storage carcasses. Neither mod needs the other.
- With Much More Storage installed, the wood and concrete worktop recipes here are turned off and its worktops are used instead. They look the same.
- Screwdrivers are tagged `c:tools/screwdriver`. Any of them builds a machine or a cupboard, and sneaking takes parts off.

## Compressor and Auto Crafter

- Compressor: 30 FE/t. An ingot makes a plate (iron, copper, gold and steel), 4 sand make a Silicon Wafer, 32 compostables make a Biofuel Block (burns for 2400 ticks), and 64 coal make a diamond.
- Auto Crafter: 20 FE/t. Set a crafting pattern in its screen and it crafts from the ingredients fed into its buffer, into 4 output slots. A redstone button picks always on, on with a signal or on without one. The screen says what it is waiting for.

## Fluids

- Fluid Tank: holds 16 buckets of one fluid and keeps it when broken. Right click with a bucket to fill or empty it.
- Fluid pipes move 250 mB at a time and use the same input, output and disconnected ends as item pipes.
- Pipe filters fit on an input end. Right click it with an empty hand to set the list. Allow lets only the listed items or fluids out there, Deny lets everything else. Tags on also matches anything sharing a tag, data Match also checks enchantments and damage. Pipes send matching items to filtered ends first.
- Item and fluid pipes are also crafted from casings and glass.

## Generators

- Lava Generator: 80 FE/t (120 on steel), burns 1 mB of lava every 10 ticks from a 4 bucket tank. Fill it with a bucket or a fluid pipe. It keeps its lava when broken.
- Solar Panels, half height with a gauge, made from a PV Cell and a core:

| Tier | Day | Night | Output |
|---|---|---|---|
| Iron | 4 FE/t | 0 | LV |
| Copper | 20 FE/t | 0 | LV |
| Gold | 100 FE/t | 0 | LV |
| Diamond | 1000 FE/t | 500 FE/t | MV |

- Wind Turbine: a base with 1 to 5 Turbine Blades stacked on top, each adding up to 50 FE/t, so five make up to 250 FE/t. Output is measured at the top blade and grows with height (full at Y 160), open air around it (9x9x9) and rain (x1.25) or thunder (x1.5). The blades turn together as one helix, faster and louder as they make more power. A blade with no base under it, or a sixth blade or higher, does nothing. Turbines from older worlds become a base with one blade.

## Voltage

Redstone Copper Cable is LV (1000 FE/t), Gold Cable MV (8000 FE/t) and Diamond Cable HV (64000 FE/t). Cables lose nothing. Machines take LV and the Quarry takes MV.

The LV/MV and MV/HV Transformers step power up or down. The front face is the high side and the other faces are the low side; use a screwdriver on a face to make it the high side, or sneak to pick the face opposite. Each step loses 5%.

Wiring a higher voltage into something rated lower makes it explode.

## Quarry

The Quarry Assembly is made with an Electric Drill, and the drill's frame, bit and upgrades carry over into the quarry. Build the Quarry on a chassis like any machine.

It digs a 16x16 area with the quarry at one corner, out from its front, down to bedrock. Landmarks placed in line with it, up to 64 blocks away in front and to one side, set the size instead. It runs on MV at 8000 FE a block and holds 200k FE (400k on steel).

Once powered, it builds a raised frame one piece at a time, then the gantry moves the drill head over the area layer by layer. Blocks in the way of the frame are dug out first, and their drops (or a chest's contents) go to its output. If something it can't break is in the way, the screen says so and it waits. If any frame piece is broken, it stops and rebuilds the frame before digging again.

Up to three pipe filters fit in it to choose what it keeps; sneak with an empty hand to take the last one back. A Deny filter always voids what it lists. If any Allow filter is fitted, only what at least one Allow filter lists is kept. So two Allow filters keep either list, and a Deny filter can take items back out of an Allow list. A diamond bit lets it dig obsidian.

## Entangled tech

Entangled devices that share a 6 digit code (000000 to 999999) are linked across any distance and any dimension, at no extra cost.

- Parts: the Entangled Ingot (Redstone Copper Ingot and an Ender Pearl in the Alloy Smelter), Entangled Wire (2 per ingot, Rolling Mill in rod mode) and Entangled Coil (4 wire on a Steel Rod).
- Entangled Programmer: LV, holds 10k FE, 500 FE a write. Type a code or press Random, then Write or Enter. It writes onto Link Cards and Entangled Backpacks.
- Link Card: right click a device to give it the card's code, sneak right click to copy the device's code onto the card. Only a Link Card sets codes on devices. Cards are not used up.
- Private or public: a screwdriver click switches a device, owner only. A private device only opens for its owner and only connects to Simple Machines pipes and cables its owner placed. Cables and pipes placed before 2.2 have no owner.
- Entangled Buffer: 27 slots shared on the code. Entangled Tank: 16 buckets of one fluid shared on the code. Both LV, 8 FE/t, 20k FE.
- Entangled Relay: LV, MV or HV (1,000, 8,000 or 64,000 FE/t), Send or Receive, 10% loss, 8 FE/t upkeep.
- Entangled Pad: two pads per code, jump on one to go to the other. MV, 100k FE. A trip costs 2,000 FE plus 20 FE per block of x and z distance, at most 50,000 FE. 3 seconds between trips.
- Entangled Backpack: coded in the Programmer, it opens the buffer slots on its code from anywhere. Hold and right click, or wear it and press B. 1,000 FE an open from 50k FE, charged in the Charger.
- Chunk Loader Module and up to two Range Modules fit in a buffer, tank or relay (3 slots, no Overclocker): 1, 9 or 25 chunks at 32 FE/t a chunk. 75 chunks per player by default.

Server config, config/simplemachines-server.properties, read live: entangled_chunk_loading=true, entangled_fe_per_chunk=32, entangled_max_range=5, entangled_max_chunks_per_player=75 (0 for no cap), entangled_load_when_owner_offline=true.

## Changelog

### 2.2.0

- Entangled tech: Entangled Ingot, Wire and Coil, the Entangled Programmer and Link Cards, and 6 digit codes that link devices across any distance and dimension.
- Private and public codes, switched with a screwdriver. Private devices only open for their owner and only connect to their owner's Simple Machines pipes and cables.
- Entangled Buffer (27 shared slots), Entangled Tank (16 shared buckets) and Entangled Relay in LV, MV and HV with 10% loss.
- Entangled Pad: jump to teleport between two pads, 2,000 FE plus 20 FE per block, at most 50,000 FE.
- Chunk Loader and Range Modules for buffers, tanks and relays, 32 FE/t a chunk, 75 chunks per player, with server config keys.
- Entangled Backpack: opens the buffer on its code from anywhere, held or worn with a key, charged in the Charger.
- Only a Link Card sets codes on devices. The backpack is coded in the Programmer.
- Manual section and JEI info for entangled tech.
- Cables and pipes remember who placed them.

### 2.1.1

- Tooltips on every powered block and item show its voltage in the cable colour, what it accepts or sends, what it uses or makes, and what it holds.
- Powered tools show what they use per block and the Charger tier. The Quarry Assembly says it builds an MV quarry.
- Machine screens show LV, MV or HV over the energy bar on a dark backing plate, and the bar tooltip shows the tier.
- The Battery screen shows its charge with thousands separators, like 300,000 / 500,000 FE.
- Solar Panels, Wind Turbines and Transformers keep their energy when broken.
- The manual's voltage page says tooltips and machine screens show each voltage in its cable colour.

### 2.1.0

- New: Compressor, Auto Crafter, Lava Generator, Solar Panels in four tiers, Wind Turbine, Fluid Tank, fluid pipes, pipe filters, MV and HV cables, transformers, Quarry and Landmarks.
- Plates come from the Compressor now; the Rolling Mill makes rods and casings. A mill left in sheet mode in an older world waits until its mode is changed.
- Iron and copper dusts work in the steel and redstone alloy recipes.
- JEI recipe pages for every machine (optional).
- The Battery shows its charge on a 5 bar gauge, in the world and as an item. Tank items show their fluid.
- The Battery gauge bars glow.
- The Wind Turbine is now a base with up to 5 stacked blades that turn together, with a soft wind sound. Old turbines become a base with one blade.
- The Quarry builds its raised frame a piece at a time and repairs it, moves the drill head on a gantry with sparks and a drill sound, and only starts cutting once the head is over the block.
- The Quarry clears plain blocks where its frame goes and takes up to three filters.
- A screwdriver sets which face of a transformer is the high side.
- The Lava Generator keeps its lava when broken.
- The Compressor makes Biofuel from any seeds or crops tagged c:seeds or c:crops, so seeds and crops from other mods work too.
- Generators take turns on which side they power first.
- Switched-off cable sides can be clicked again with a screwdriver.
- Recipe unlocks are checked again on login.
- Version numbers are plain from 2.0.1 on.

### 2.0.0-release.1

First 26.1 release. Same content as 0.1.0-alpha.16.

### 0.1.0-alpha.14

- Square metal plate icons.

### 0.1.0-alpha.13

- The chainsaw now uses the drill's yellow housing and the same greys, and the manual covers spin-up, sparks, tool tiers, upgrades, tree felling, cables, dyes and pipe ends.
- The manual is crafted from a book, any screwdriver and a copper ingot, and the screwdriver is given back. Any screwdriver now triggers the free first copy.
- Every recipe unlocks in the recipe book once you hold one of its ingredients, and the `c:` tags have names in JEI and EMI.
- The chainsaw bar no longer shows a stray texture past its tip, and battery and cell faces stay on their own texture.
- The manual has cards for steel rods, the Charger and Power Cell, and all 11 assemblies.

### 0.1.0-alpha.12

- Hold attack and the drill or chainsaw spins up with a rising motor pitch, keeps spinning from block to block, and only winds down (pitch falling) about half a second after you stop mining.

### 0.1.0-alpha.11

- Drill sparks are back at the crosshair, and the drill is held aimed forward in first person so the bit tip sits on the crosshair.

### 0.1.0-alpha.10

- Drill sparks now fly from the tip of the bit instead of the middle of the block, sprayed back off the face. Other players see them too.

### 0.1.0-alpha.9

- New drill bit: a chunky tricone roller-cone bit on a threaded shank. The cones turn while mining. The drill body is yellow.
- Tool tiers. The standard bit mines up to diamond ore; obsidian and netherite level blocks throw white-hot sparks and do not break. The new Diamond Drill Bit mines them, at full speed on a steel frame and 40% on an iron frame.
- Diamond-Tipped Chain: a steel chainsaw with it fells whole natural trees, 50 FE per log, logs drop in the world. Sneak to cut one log.
- Energy Cells also add 15% speed each. Overclockers add 50% each; together capped at +100%. The tooltip shows the total.
- Overclocker fins glow only while the tool is in use.
- The control board sits on the side behind a panel.
- Taller Tool Stand, about the size of an anvil. New recipe: smooth stone slabs, a steel rod and smooth stone.
- Bigger, more numerous sparks with longer streaks.
- No arm swing or recoil while mining.
- Manual: new Tool Tiers page.

### 0.1.0-alpha.8

- Drill and Chainsaw are assembled from parts on the new Tool Stand: frame, core, control board, battery module and up to two upgrades, each screwed in. Sneak with a screwdriver to strip or swap parts. The finished tool recipes are gone; frames, cores, battery module and stand have recipes.
- 3D tool models that show their parts. The drill bit spins and the chain runs while digging.
- Hot orange steel sparks when tools cut stone and ore.
- Drill and Chainsaw sounds and mining particles.
- The Electric Shovel is merged into the Electric Drill, which now mines shovel blocks too. Old shovels vanish from worlds.
- Worktop items are now the same 3D slab as Much More Storage's, so the two mods' tops look identical in inventories. With Much More Storage installed, a Simple Machines wood or concrete worktop crafts on its own into the matching Much More Storage one so they stack. Tops from either mod still fit either mod's cabinets and machines.
- Screwdriver disconnects or reconnects a cable side.
- Cables and item pipes can be dyed in the world; different colours stay apart.

### 0.1.0-alpha.7

- Electric Drill, Chainsaw and Electric Shovel, charged in the Charger.
- Overclocker Circuit and Energy Cell upgrades, an optional assembly step after the control board.
- Simple Machines Manual for Simple Books (optional dependency).

### 0.1.0-alpha.6

- Pipe ends cycle through input, output and disconnected with a screwdriver. Old extract ends load as output.

### 0.1.0-alpha.5

- Item pipes and per-side auto-output.
- New machines: Battery, Crusher, Sawmill, Alloy Smelter, Crop Harvester, Shearer, Stone Generator, Water Generator, Mechanical Hive and Charger.
- Iron, copper and gold dusts.
- Machine inventories are sided for hoppers and other mods.

### 0.1.0-alpha.4

- Copper, iron and steel sheets redrawn as flat sheets.
- Panels and worktops in all twelve woods and sixteen concrete colours, drawn to match Much More Storage.
- Worktop sits on the machine as its own piece and shows the chosen finish.
- Shared worktop and screwdriver tags, see above.
- Breaking change: the old single `wood` finish is gone, so worlds from alpha.3 with wood panels or worktops will lose them.

Hoppers: top feeds the inputs, sides feed fuel first and then the inputs, bottom takes the output.

Recipes use the types `simplemachines:alloying`, `simplemachines:extruding` and `simplemachines:rolling`, see `data/simplemachines/recipe`.

The build uses Stonecutter: one source tree in `src/main`, one project per target under `versions/` (`1.20.1-forge`, `1.20.1-fabric`, `1.21.1-neoforge`, `1.21.1-fabric`, `26.1.2-neoforge`, `26.1.2-fabric`, `26.2-neoforge`, `26.2-fabric`, `26.3-neoforge`, `26.3-fabric`). Loader code lives in the `neoforge`, `fabric` and `forge` packages and is left out of the other loaders. Versions and dependencies are in `stonecutter.properties.toml`.

For now 1.21.1, 26.1.2, 26.2 and 26.3 build; the other targets are still being ported. Build one with `./gradlew :26.1.2-neoforge:build :26.1.2-fabric:build`, `./gradlew :26.2-neoforge:build :26.2-fabric:build`, `./gradlew :26.3-neoforge:build :26.3-fabric:build` (Java 25) or `./gradlew :1.21.1-neoforge:build :1.21.1-fabric:build` (Java 21), jars land in `versions/<target>/build/libs`. `./gradlew :26.1.2-neoforge:runClient` runs one; add `-PquickPlay=localhost:25565` to join a server straight away. Textures and json files come from `python3 tools/gen_assets.py`.
