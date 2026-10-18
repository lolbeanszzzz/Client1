# KIRXCLIENT BETA - Fabric client mod for Minecraft 1.21.11

This repository is a complete Gradle project. It includes the existing client GUI and
module collection, plus a local custom-cape picker.

SETUP
1. Install Java 21 and Gradle 9.2 or newer.
2. From this directory run `gradle runClient` to launch the development client.
3. Run `gradle build` to create `build/libs/kirxclient-0.1.0.jar`.
4. Install that jar with Fabric Loader and Fabric API for Minecraft 1.21.11.

CONTROLS
- RIGHT SHIFT opens/closes the GUI (also re-press to close)
- Left click a module: toggle.  Right click: open its settings dropdown.
- Middle click a module, then press a key: bind it (Esc/Backspace clears).
- Drag a window by its title bar. Right click a title bar to collapse it.
- Render -> Cape -> "Choose cape image" imports PNG, JPEG, GIF, or BMP files.
  Images are converted to the standard 64x32 cape texture and stored under
  `config/kirxclient/cape.png`. GIF uploads currently use their first frame.
  Custom capes are client-side only; they are rendered for your own player and
  are not uploaded to Mojang, a server, or other players.
- Block ESP -> "Choose blocks..." opens a searchable block list (saved to config/whiteclient-blocks.txt).
   Selected blocks have a rainbow strip: drag it to give that block its own colour, [x] resets it to the default.
   Blocks are drawn as flat solid-colour outlines (no 3D cube, so no lag) that show through walls; optional Fill, Thickness and Color settings in the dropdown.
- Tracers: line from the crosshair to every loaded player (targets behind you point off the screen edge).
- Attack Packets (Combat): every attack the client makes sends one swing + one attack packet. Switch "Swing before attack": off = attack then swing (1.21 vanilla), on = swing then attack (1.8 order). Your own mouse clicks always use the vanilla order.
- Item swaps: only ONE item swap per tick for all modules together (hotbar slot changes and inventory swaps). A module that is refused tries again next tick.
- Trigger Bot (Combat): attacks when the crosshair is on a player on a timer (Sword / Axe delay in ms, random between min and max). Optional Same player, Check shield, Only crit, Use shield, Stray bypass, All entities, Look at target (smooth turn after a hit). Won't fire with a screen open (unless Work in screen) or in Freecam.
- HUD (Render window): Color + Saturation recolour the menu (title bars / enabled modules); saturation 0 is the default white.
   Switching HUD on also shows a watermark and an enabled-module list on screen in that colour.
- (The internal mod id / package stay "whiteclient" / dev.white.client so the setup steps above don't change.)
- Flight (Player): move with WASD, Jump = up, Sneak = down; Speed setting. Most servers' anti-cheat kicks for this.
- Kill Aura (Combat): attacks the nearest player (optionally mobs) in Range once your weapon is charged.
- Block ESP: scans a few chunks per tick (no lag spikes), draws only the nearest 'Max blocks', and simplifies far cubes.
- Movement window: Flight, Bhop (auto-jump while moving, optional Air speed) and Scaffold (places solid hotbar blocks under your feet; Auto switch picks a block slot).
- Auto Box (Combat): walls in the nearest player (feet ring, head ring, optional cap) with obsidian or any solid hotbar block.
- Auto Cobweb (Combat): places a cobweb on the nearest player (feet, optionally head) from a hotbar cobweb.
- Ding Dong Builder (Player): look at the ground where you want it, then switch it on. Builds a small structure (3 flat in a line + 3 stacked on the middle one) from solid hotbar blocks, facing the way you face; stay within reach. Switches itself off when done.
- Toggle All (Player): master switch. Middle-click it in the menu, press a key to bind it. Press: everything that is on switches off (remembered); press again: those switch back on.
- Breach Swap (Combat): when you attack while holding a sword, the hit is made with a Breach mace from your hotbar, then your slot swaps back. Settings: Breach only, Only from sword, Swap back (ticks).
- Auto Weapon (Combat): replaces Auto Mace Swap. Picks the best weapon in your hotbar for the target, modelled on LiquidBounce's AutoWeapon. The pick is a plain function of the target's current state (no multi-step sequence): a target that would block the hit (vanilla horizontal shield check) gets an axe (Auto shield break); while you fall more than 1.5 blocks (not gliding) it picks a mace (Auto mace: Density from 'Density from fall (blocks)' and up, else Breach, or any mace if 'Use any mace if none'); with Stun slam on and both true it takes the axe first and, the moment the shield is down, the same rule picks the mace. Otherwise the best of Prefer sword / axe / mace by Priority (0 default, 1 knockback, 2 damage, 3 speed). Trigger Bot, Silent Aura, Kill Aura and Auto Clicker call it: the weapon is chosen when the target is known ('Change on target', default) and the hit goes out a tick later (a slot change and a hit never share a tick); 'Change on attack' also swaps at the moment of the attack. Axe-on-shield and mace-smash hits skip the usual charge/delay wait, spaced by 'Follow-up gap (ticks)'. Switch back (ticks) returns to your old slot; 'Only from weapon' (default on) never takes the slot away from blocks, pearls, food etc. Replaces the separate Auto Shield Break only in the sense that the same job is built in: turn Auto Shield Break off when Auto Weapon is on.
- Auto Walk (Movement): holds the forward key for you. Entity Speed (Movement): sets the speed of the mount/vehicle you are riding while you hold a movement key (Speed setting).
- Block ESP alert: plays a sound (assets/whiteclient/sounds/alert.ogg, must be Vorbis) when 'Alert count' or more selected blocks are drawn at once. Settings: Alert sound, Alert count, Count behind you, Alert cooldown, Alert volume. Replace alert.ogg to change the sound.
- Auto Tunnel (Player): digs a 1- or 2-high tunnel straight ahead in the direction you face and (optionally) walks forward as it opens. Settings: Reach, 2 high, Walk forward, Auto tool. Stops at liquids next to the block and at unbreakable blocks. Not turned on by Toggle All.
- Auto Eat (Player): when hunger is at or below 'Eat at hunger', eats the best food from offhand/hotbar (not rotten flesh, spider eye, pufferfish, poisonous potato, chorus fruit or golden apples), then swaps back. Pauses Auto Tunnel while eating.
- No Slow (Movement): no walking slowdown while eating/drinking/blocking (adds the lost speed back each tick; no game patch needed).
- Stash Finder (Player): scans loaded chunks for chests/trapped chests/barrels/shulker boxes. When 'Min chests' or more sit within 'Radius' blocks of each other it prints their coordinates in your chat (client side only). Dungeons (a mob spawner nearby) are ignored by default, since a dungeon has only 1-2 chests; natural structures with many chests (bastions, strongholds, villages) can still trigger it.
- Reach (Combat): extends your block / entity interaction range (Block reach, Entity reach). The server checks reach too, so large values may be rejected or flagged.
- Auto Totem (Combat): keeps a Totem of Undying in your offhand, pulling one from your inventory when it is missing. Settings: Delay (ticks), Replace offhand item. Only acts while no screen is open.
- Safe Walk (Movement): you can't walk off an edge when the drop is more than 'Max drop' blocks (default 3 = the vanilla fall-damage limit; water below counts as a safe landing). It edge-clips like sneaking, only where the fall would hurt. Doesn't stop jumping off or being knocked off.
- Freelook (Render): look around with the mouse while your character keeps facing/walking the same way. Turn it off to snap back.
- Chest Stealer (Player): when a chest/barrel-style container or shulker box is open, shift-clicks everything into your inventory (Delay, Auto close).
- Inv Sorter (Player): switch on (or bind a key) to sort your main inventory by item, biggest stacks first; it switches itself off when done.
- Auto Replenish (Player): refills a hotbar slot from your inventory when its stack runs out.
- Auto Armor (Combat): wears the best armor you carry and upgrades worse pieces (never swaps out an Elytra).
- Criticals (Combat): sends two small in-air position packets before a ground hit so it crits. Often flagged by anti-cheat.
- Velocity (Combat): scales knockback down (Horizontal %, Vertical %; 0 = none).
- Jump Reset (Combat): jumps for one tick the moment you're hit on the ground, which cuts the knockback. Chance % setting.
- Auto Dtap (Combat): crystal double tap. Every hit on an end crystal is sent Taps times in the same tick; Auto break hits the crystal under your crosshair for you (Auto delay in ticks).
- Baritone (Movement): buttons for the Baritone pathfinding mod (Stop, Pause, Resume, Go this way, Go to nearest waypoint, Mine Block ESP blocks, Explore, Farm, Follow players). Baritone itself is a separate mod: put baritone-standalone-fabric (v1.17.0 is the 1.21.11 release, github.com/cabaletta/baritone/releases) in your mods folder next to this one. Bind a key to the module to stop pathing instantly. You can also type Baritone's own # commands in chat.
- Elytra Swap (Player): click it (or press its keybind) to wear the hotbar Elytra with the most durability left; your old chestplate goes into that hotbar slot. Ignores broken elytras and does nothing if you already wear the best one.
- Auto Shield Break (Combat): when your crosshair is on a player blocking with a shield (facing you), switches to a hotbar axe and hits them to disable it, then switches back. Settings: Auto attack, Switch back, Delay.
- Macro Record + Macro (Player): bind a key to Macro Record to start/stop recording your keys, mouse buttons and camera turns (up to 5 min); bind a key to Macro to play it (press again to stop). Macro has a Speed slider (0.25x-4x), Loop, and Replay camera turns; the recording is saved in config/whiteclient-macro.txt.
- Waypoints (Render): saved coordinates drawn on screen with distance, per server + dimension (config/whiteclient-waypoints.txt). Buttons: Add here / Remove nearest / List in chat / Clear all. 'Death point' saves where you died. Add Waypoint (Render): bind a key to it to save a waypoint at your feet.
- Player Info (Render, formerly Armor Display): name, ping (coloured by quality), optional distance, health and armor durability above players. Settings: Range, Name, Ping, Distance, Held items.
- Snowflakes (Render): snow falls down the screen while the client menu is open (Amount, Speed, Big flakes).
- Look (util/Look.java): the shared overlay style (smoked-dark plates with an accent bar, soft saturated colours, translucent fill + bright outline). Block ESP, Sus Chunk Finder (tags + map), New Chunks, Stash Finder (new Highlight setting), Logout Spots, Waypoints, Hitboxes, Tracers and Player Info all draw through it, so new overlays should too.
- New Chunks: fixed chunks being filed under the wrong world after a dimension change, data lost when leaving a world, fills popping off when you stand on a chunk, and lag from filling every chunk (now clipped at the near plane, fill range + per-frame budget, distance fade).
- Sus Chunk Finder (rework): every suspected chunk (>= "Min % to highlight") is outlined in the world, coloured by strength; no tags any more. Aim the crosshair at a highlighted chunk (works through walls) and a card shows the %, direction, distance, Y range, every clue with its points and a confidence level. Only the nearest chunks get a translucent fill (cheap). "Max highlights" caps how many are drawn.
- Live players: "Flag live players" marks the chunk any other player is standing in (cyan, "LIVE name" marker even off-screen), keeps it flagged for "Player flag time (s)", and notifies you (chat + sound) the first time each player appears. "Ignore friends" skips your friends list. A flagged chunk also counts as evidence in the %.
- Block ESP / all overlays: lines are now drawn at real screen resolution (thin and crisp at any GUI scale, with a per-frame budget so it cannot lag). HUD -> "Steady view (ESP)" switches off view bobbing + sprint FOV zoom while an overlay is on (your own settings are restored when it's off), which is what made boxes slide off blocks.
- UI look (HUD module): ONE colour for everything. Color + Saturation recolour the menus AND every in-world overlay (Sus Chunk Finder plates/tags/map, ESP tags, Player Info, ...); saturation 0 = the classic white/dark look, and it is also how strongly the overlay plates are tinted. Menu opacity % lets the blurred world show through the windows, Overlay opacity % does the same for in-world plates, Corner radius (0-8) rounds every window/plate (0 = square), Menu blur uses the game's own menu blur (strength: Options > Accessibility > Menu Background Blurriness), and 'One accent colour' makes chunk highlights and tooltips use the picked colour instead of red/orange/amber.
- World-space renderer (util/WorldGfx.java, Meteor-style): Block ESP and Sus Chunk Finder boxes are now queued in real world coordinates during the world render pass (WorldRenderEvents.BEFORE_TRANSLUCENT -> ModuleManager.onWorld -> Module.onWorld) and drawn on the GPU in ONE call through the game's own projection. They can no longer slide off their block (FOV, sprint zoom, view bobbing, hurt tilt are all handled by the game) and cost almost nothing, instead of hundreds of GUI rectangles per frame. Tracers, labels, the hover tooltip, the region map and the alert sound are still drawn in the HUD pass (onHud). New modules should override onWorld() and call WorldGfx.box(...) for anything that must stay attached to the world.

- GUI search: just start typing while the menu is open; windows filter to matching modules (Esc or the x clears it).
- Misc window: Friends (protected from Kill Aura / Trigger Bot, drawn in a colour; open its settings, click 'Type name to add', type a username and press Enter - Ctrl+V works, 'Type name to remove' does the reverse, no need for the player to be nearby) and Config (slots, export / import via clipboard, also .config export|import).
- Base Finding window: Sus Chunk Finder, Stash Finder, New Chunks, Block ESP, Logout Spots, Geode ESP (only geodes with many fully grown clusters).
- Spear Swap (Combat): selects a hotbar spear for the click / dash, then switches back.
- Found List (Base Finding / .finds): everything Sus Chunk Finder, Stash Finder, Geode ESP and Trail Tracker found; filter, sort, save a waypoint or send Baritone there.
- Trail Tracker (Base Finding): needs New Chunks on; draws a line + arrow along long thin trails of new chunks.
- Sky (Render): white or gray sky, white clouds, matching fog.
- Baritone: Tunnel (height / width / length), Dig to Y, Surface, Come, Go to nearest base / trail head, Follow nearest non-friend, movement setting toggles.
- Sus Chunk Finder ignores trail chambers (and 2 chunks around them) when "Ignore structures" is on.

- Version 1.1. fabric.mod.json now has the version hardcoded to "1.1" and an icon (assets/whiteclient/icon.png); the HUD watermark shows it too.
  If your Gradle template overrides the version, set mod_version=1.1 in gradle.properties as well.
- Freeze (Movement): by default the server is told you stand still while you move on your own screen (movement packets are held back).
  "Snap back on release" returns you to the held spot when you switch it off. Turn "Move locally" off for a local lock instead.

- Fixes (after 1.1): Freeze's sendPacket hook moved to ClientCommonNetworkHandlerMixin (sendPacket is declared on that parent class, so it crashed on load).
  Menu: long setting/button labels are shortened with "..." (full text shows on hover) and Hue rows move their strip right for longer labels; Baritone button names were shortened.
  Sus Chunk Finder: new "Marker at base height" setting (default on) draws markers at the clues' real height instead of the flat plane; the reasons card now also shows when you aim at the marker or anywhere over the chunk column (not only the clue box);
  changing any setting re-rates and rescans straight away; chat/sound alerts no longer drop chunks past the 3rd per pass and no longer burn a chunk's one-time alert while Chat alert / Alert sound are off.

- Sky (1.21.11 fix): the sky colour is now set in SkyRenderingMixin (SkyRendering#updateRenderState -> SkyRenderState.skyColor), clouds in CloudRendererMixin (renderClouds colour) and fog in FogRendererMixin (getFogColor). The old ClientWorld#getSkyColor/getCloudsColor hooks do not exist in 1.21.11. New setting "Hide sunset glow".
- Sus Chunk Finder: "Scan speed (chunks/tick)" (default 3) and "Rescan delay (ticks)" (default 40) control how fast it searches; raise the speed / lower the delay when flying with an elytra. Work per tick is capped at ~12 ms.

- Totem Pops (Combat): chat line + optional sound on every Totem of Undying pop (server entity status 35, forwarded by ClientPlayNetworkHandlerMixin#white$status), a small on-screen list with pop counts, and a message when that player dies (count resets). Settings: Chat message, Alert sound, HUD list, List time, Count yourself, Ignore friends, Death message.
- Surround (Combat): obsidian (else any solid hotbar block) on the four sides of your feet; optional Floor under feet and Head level ring. Re-places broken blocks. "Off if I move" turns it off when you leave the block; otherwise it follows you.
- Aim Assist (Combat): smoothly steers your turn speed toward the target nearest your crosshair inside FOV/Range. Runs every frame (frame-rate independent) on the target's interpolated position. Settings: Max speed (deg/s), Strength %, Smoothing % (soft start/stop), Target lead % (follows strafing targets), Dead zone (deg), Vertical speed %, Yield to mouse (backs off briefly when you move the mouse against the pull). Toggle it on and it is always active (no need to hold click); turn on "Hold click to aim" if you want it to pull only while left click is held. Friends are skipped.
- Sus Chunk Finder: "Scan progress" readout (top-left) shows chunks scanned this pass, how many were not loaded yet and how long the last pass took (green < 4 s, amber < 8 s, red = not keeping up).
- Sky colour: Sky colour strip + "Sky color amount %" tint the sky and matching fog.

- Finish time (s): Surround, Auto Box, Auto Cobweb and Ding Dong Builder have a "Finish time (s)" slider (default 2.0). With N blocks to place they wait finish/N seconds between blocks and place one at a time, so the last block lands about that many seconds after the action starts (Auto Cobweb: after a target is in range). Hotbar slot is put back after each block while pacing. 0 = the old instant behaviour (Delay + Blocks/tick). Helper: util/Pacing.java.
- Aim Assist: reads the left mouse button every frame (a click counts for 0.35 s), default Range 6, and shows a status tag under the crosshair ("Aim: <name>", "Aim: hold click", "Aim: no target", "Aim: paused ..."). "Show status" turns the tag off.
- Spotify (Render): small HUD plate with the song and artist Spotify (desktop app) is playing. No login: Windows reads the Spotify window title, Linux uses playerctl, macOS uses AppleScript (progress bar on Linux/macOS only). Settings: X, Y, Refresh (s), Progress bar, Hide when paused.
- Auto MLG (Player): when you fall at least "Min fall" blocks it looks straight down, selects a hotbar water bucket, places the water within "Place within" blocks of the floor, then (optionally) scoops it back up and restores your slot and view. Skips flying, gliding, riding, water / slime / hay landings and Nether-style dimensions.
- New menu look: frosted rounded windows centred in a row (menu icon in a title folds the window), module rows with a pill when on and a chevron when they have settings (click the chevron or right click to open them), search at the bottom centre, sun / moon switch (top right) for light / dark menus, palette button (bottom right): click = next accent colour, right click = back to white / grey. HUD Color / Saturation / Menu opacity / Corner radius still apply.

- Sidebar UI (Render): menu style switch. Off = the classic draggable windows, On = a sidebar menu (gui/SidebarGuiScreen.java): dark panel, categories on the left (with module counts), module rows with a one-line description and a switch on the right, search box top right (or just start typing; it searches every category). Left click a row = toggle, click its chevron or right click = settings, middle click = bind a key. General: Theme (HUD settings), Configs and Socials (Friends) open as settings pages. The accent colour follows HUD -> Color. Settings: Descriptions, Category counts. Switching it while the menu is open swaps menus instantly; Toggle All never touches it. Descriptions live in module/Descriptions.java (add a line there for new modules).
- Both menus now fit the screen: they are laid out at their normal size and shrunk (down to 40%) when the screen is too small for the current GUI scale, so nothing goes off screen. Mouse input is scaled to match, and the classic windows are re-centred / kept on screen when the resolution or GUI scale changes.
- Stash Finder highlight now uses the same world-space renderer as Block ESP (onWorld -> WorldGfx, one GPU call), so it no longer lags and the boxes stay on their chests. Each stash's centre / bounding box is computed once when it is found, the list is kept nearest-first, and only "Max boxes" (default 300) are drawn. New settings: Max boxes, Thickness, Glow, Fill, Fill opacity, Color, One box per stash (a single box around the whole stash, cheapest), Labels (one "STASH xN  dist" tag per stash, max 12).
- Auto MLG fix: the bucket's reach is measured from your EYES (about 1.6 blocks above your feet, 4.5 max), but the old "Place within" check measured from the feet, so with the default 4 the floor was still out of reach when it tried to place and no water appeared. Now "Place distance (eyes)" (default 4.4, capped at your real interaction range) is measured from the eyes, the water is placed the first tick the floor is in reach, and arming + placing can happen in the same tick (no wasted tick on fast falls). The setting was renamed, so an old saved value is ignored.
- Hitbox (Render): filled, shaded box + outline around players (style B), through walls, drawn with the world-space renderer (WorldGfx.shadedBox, one GPU call) so it follows entities smoothly. Settings: Players, Mobs, End crystals, Range (blocks), Fill opacity, Thickness, Glow, Color, Friend colour (friends use the Friends colour), Invisible. Your own box shows while Freecam is on. At most 200 boxes per frame.

- Hitboxes (Render, replaces Player ESP): filled 3D box with tinted faces and an outline around players (optionally mobs / end crystals), through walls. Color + Saturation (0 = white) + Brightness set the colour; also Fill opacity, Thickness, Glow, Range, Friend colour. Name / health / distance are in Player Info.
- Fast Crystal (Combat): Fast place removes the delay between crystal placements while you hold an end crystal; Instant break hits a crystal the tick it spawns (aimed at it, or any in range with Only when aimed at off). Break range is capped by your real entity reach (Reach raises it).
- Reach now has Hit chance (%): once per tick it rolls whether the longer entity reach applies (100 = always, 0 = never).
- Sus Chunk Finder: each loaded chunk is now scanned once and skipped on later passes (setting "Skip scanned chunks"). A chunk is scanned again only if it was unloaded and loaded anew, or when you press "Rescan all chunks". Old results stay on screen between passes.
- Hitbox Expand (Combat): makes other players' (optionally mobs' / crystals') hitboxes bigger for your own aiming, by Size (blocks) on every side, so slightly-off hits land. Client-side picking only; the server still checks reach, so it does not add distance. Hitboxes draws the expanded box while it is on.
- Hitboxes now draws the exact box the game aims at (bounding box + Hitbox Expand margin). 'Smooth motion' slides it between ticks like the player model, but then it can differ slightly from the real hit test. Fast Crystal: new Place delay, Retry hit after and Max hits per second settings.
- Sidebar UI restyle: navy panel with an inset sidebar card, pixel icons on every nav item, a GENERAL section (Settings, Theme, Configs, Socials, Keybinds), All / Enabled filter chips, a list / grid switch, and bold module names. Keybinds page: click a module's key, then press the new key (right click clears).

- Sidebar UI polish: custom fonts (Instrument Serif italic for the page title and the accent word of the brand, Instrument Sans for all other text; assets/whiteclient/font/*.json + TTFs, SIL OFL, see font/OFL.txt; util/Fonts.java builds the styled text). Real PNG icons (assets/whiteclient/textures/gui/icons: crown.png + nav_0..10.png, white 32x32, tinted by the menu colours, .mcmeta turns on smoothing) replace the pixel icons.
  Animated gradient controls: switches have a gradient track and a knob that slides and stretches, sliders have a gradient fill and a round ringed knob that grows on hover / drag (and glide to new values), Hue sliders get a knob too, rows / nav items / chips / buttons fade between hover and active, and the panel eases in when it opens. Helpers: Ui.gradient(...) and Ui.mix(...).

- Username Hider (Misc): replaces your own username with a fake name on your screen only (chat, tab list, name tags, scoreboard, menus); the server and other players still see your real name. Settings: Type fake name (click, type, Enter; saved in config/whiteclient-fakename.txt), Show fake name, Reset fake name. Implemented in TextVisitFactoryMixin (every drawn / measured string passes through UsernameHider.apply).

- Radial Menu (Player, was Circle): bind a key to it (or switch it on) and a radial menu opens and splits into three slices. Left click a slice = it types that text for you in chat, or runs it as a command when it starts with "/" (client commands such as ".toggle fly" work too). Right click a slice (or left click an empty one) to type its text - spaces are fine, Enter saves, Esc cancels. The module key / Esc closes it. Settings: Close after sending, Edit slot 1-3 (opens the radial menu in typing mode), Clear all slots. It follows the menu theme: dark mode (sun / moon switch in the menu) gives a dark radial menu, light mode a light one, using the same colours and tint as the windows; with the Sidebar UI on it keeps the dark navy look. Texts are saved in config/whiteclient-radial.txt (gui/RadialMenuScreen.java, module/impl/RadialMenu.java). Plain chat slots are sent with Baritone's chat control switched off for that one message, so words like "come" or "stop" reach the server instead of being run by Baritone; start a slot with # to talk to Baritone on purpose.
- Ancient Debris (Base Finding): outlines ancient debris through walls in the Nether (by default as a rough 8x8x8 area box around each cluster, for servers that shift ore positions; switch Rough area off for exact blocks) with tracers, "Debris Y.. ..m" labels and a chat alert for new finds; also lists it in the Found List (source "Debris"). Settings: Range (chunks), Nether only, Rough area, Area size, Area height, Min / Max Y (default 0-127 so shifted ore is not filtered out), Only exposed, colour / thickness / glow / fill, Tracers, Labels, Max shown, Chat alert. Baritone has a "Nearest debris" button that walks you to the closest one. It only reads blocks the server sends your client: with anti-xray on, buried debris is hidden (and some anti-xray modes send fake ore that vanishes up close), so only exposed or uncovered debris shows (module/impl/AncientDebris.java).
- Auto Tool (Player): while you hold attack on a block, the fastest hotbar tool for it is selected (pickaxe on stone, axe on logs, sword on cobweb...). Settings: Switch back (+ delay), Save durability (skips tools with 2 or fewer uses left). It never swaps when you hit an entity, in creative, in a menu or while eating (module/impl/AutoTool.java).
- Auto Fish (Player): hold a fishing rod and switch it on: it casts, reels in on the bite (splash sound, bobber dip as backup) after a short delay, then casts again. Settings: Auto cast, Reel delay, Cast delay, Recast timeout. Only runs while a rod is in your main hand or offhand; not restored on startup (module/impl/AutoFish.java).

- Shader UI: every rounded shape in the menus and overlays is now drawn by GPU shaders instead of rows of fill() calls.
  util/ShaderUi.java registers the pipelines (core/ui_*.fsh + core/ui_shape.vsh in assets/whiteclient/shaders). One shape = one quad;
  the fragment shader evaluates a rounded-box distance field (smooth corners at any GUI scale), a frosted gradient with inner rim light,
  an outer glow halo, a thin edge ring, left-to-right colour ramps (Ui.gradient) and a rainbow strip (Ui.hueStrip).
  util/Ui.java keeps its old API (corners / round / outline / gradient), now shader-backed, plus Ui.panel(...) for glowing windows,
  so every screen and overlay uses it. ClickGuiScreen windows use Ui.panel. HUD -> "Glow %" (0 = off) and "Blob shape" (organic edge) style it.
  The .fsh files are generated: edit tools/gen_shaders.py and run  python3 tools/gen_shaders.py src/main/resources/assets/whiteclient/shaders/core

- Shader UI, part 2: Radial Menu slices are now one shader quad each (ShaderUi.slice, core/ui_slice*.fsh: ring segment with rounded corners,
  frosted gradient, rim light, glowing edge). Sus Chunk Finder: the in-world chunk markers / footprint / clue volume are drawn by a world-space
  shader (WorldGfx.glowSquare / glowBox / glowDot, core/world_marker.fsh): edges stay a constant number of pixels wide with a soft glow and a frosted
  fill, still through walls, still one GPU call. Look.plate / Look.panel / Look.bar (tooltip card, scan readout, indicator cards, region map cells,
  tags, bars) go through the shader helpers too. Regenerate shaders with tools/gen_shaders.py if you edit them.

- Shader UI, part 3: (1) enabled-module pills (Ui.selected) have an animated wave pattern (ShaderUi.wave, core/ui_wave.fsh; the phase is the clock, one loop per 3 s).
  (2) The remaining non-sidebar UI is on shaders too: Block picker / Waypoint / Found list windows and rows, rainbow strips (Ui.hueStrip), scroll bars, the Radial Menu text box,
  Snowflakes (round soft flakes), every screen-space line (Gfx.line -> ShaderUi.line, an anti-aliased rotated quad with round caps, used by tracers, ESP tracers, trails and cubes),
  the PlayerInfo durability bar, ESP end dots, and the HUD watermark + module list (new HUD setting "Glass plates").
  (3) New module "Blob UI" (Render): the menu becomes a hub of glowing organic blobs around a centre badge (gui/BlobGuiScreen.java, ShaderUi.blob, core/ui_blobx*.fsh).
  Click a blob to open that category as a glass panel: left click = toggle, chevron / right click = settings, middle click = bind, type to search, Esc = back to the hub.
  It wins over Sidebar UI; Toggle All never touches it. Settings: Title (off by default), Show counts, Falling snow. The centre of the hub shows your own skin head (PlayerSkinDrawer). Shader sizing is now rotation-safe (Jacobian of the quad), which is what allows rotated shader quads.

SHADER FONT + BLOB UI (update)
- All menu text is drawn with a shader (util/SdfText.java + shaders/core/ui_text.fsh) from signed-distance-field atlases
  (assets/whiteclient/textures/gui/font/sdf_*.png, metrics in assets/whiteclient/font/sdf_*.metrics), so it stays sharp at any scale.
  To rebuild the atlases:  python3 tools/gen_sdf_font.py src/main/resources/assets/whiteclient   (needs Pillow, numpy, scipy)
  To rebuild the shaders:  python3 tools/gen_shaders.py src/main/resources/assets/whiteclient/shaders/core
- Blob UI: your head sits in the middle with one blobby ring tab per category around it (same idea as the radial menu).
- Enabled modules are a flat solid colour, 60% transparent (no wave, no glow). All other UI text (Click GUI, Block Picker, Waypoints, Found list, HUD, Sus Chunk Finder pop-ups, etc.) is shader text too.
- Auto Clicker (Combat): hold the left mouse button with the crosshair on a player/mob and it attacks at a steady CPS (1-20, at most one click per tick). Settings: CPS, Min charge (waits for weapon charge, 0 = off), Players, Other mobs, Pause while using item. Block breaking is left to the game.
- Pearl Catch (Player): needs an Ender Pearl and a Wind Charge in the hotbar. Switch it on (or press its key): it predicts both flights, throws the pearl, then throws a wind charge so the charge meets the pearl in the air, and switches itself off. Settings: Use current aim (the charge goes where you look now; the pearl angle and delay are searched), Charge delay (ticks), Chat messages. If no plan gets the paths within ~0.6 blocks it refuses and tells you. Each throw happens inside one tick and the view is restored straight away. Timing and angles are predicted, so tune the delay in game.

- Colour wheel (click GUI): Hitboxes (Color + optional Outline color), Tracers, New Chunks, Stash Finder, Logout Spots, Geode ESP,
  Trail Tracker, Ancient Debris and Friends use a full colour wheel: drag on the wheel for hue + saturation, drag the bar under it for
  brightness. Sidebar / Blob menus still show these as a plain hue strip. The wheel is drawn by shaders/core/ui_wheel.fsh (tools/gen_shaders.py).
- Pearl Catch: plans at the moment of the throw (not when the key is pressed), from your real per-tick movement, and prefers plans where
  the charge meets the pearl early (less random spread). Refuses (with a chat message) if the pearl is on cooldown or no plan is close enough.

- Trigger Bot extras: Random extra delay (ticks), Mouse over delay (ms), Early / Late hit chance %, Hold click to activate, Select first hit
  (waits until the target hurts you first), Air crits (only hits while falling), Shield check (skips players blocking). Hits still go
  through interactionManager.attackEntity + swing, so Auto Weapon keeps working.

- Trigger Bot rework (matches the reference Triggerbot): Extra delay min/max (ticks, negative = hit before the cooldown is done, re-rolled every tick),
  Mouse over delay min/max (ms, random per target), Require mouse down (was "Hold click to activate"), Ignore activation click (swallows the click that
  starts it, via MinecraftClientMixin#doAttack, unless you are already on a target with the attack ready), Target miss chance % (swings at nothing),
  Early hit chance % (hit 2-3 ticks early), Limit to items (+ Allow swords / axes / mace / spears), 50 ms pause after a manual click, Shield check backs off
  while Auto Shield Break is on with a hotbar axe. Min charge, Delay, Random extra delay and Late hit chance were replaced by Extra delay.

- Auto Mace Swap: new "Aim at target" (+ "Aim range"). While you fall with a mace in the hotbar it turns your view toward the nearest non-friend player (aim point 75% up the body).
  In reach already: aims straight at them. Otherwise it predicts both paths (gravity / drag, target's last-tick movement) to the tick you come within reach and only starts turning
  once the turn needs about that many ticks (2-4), then picks a turn speed that covers the angle in the ticks left (speed capped at ~75-79 per rotation update, 1.875 updates per tick).
  The turn uses the normal mouse path, so your real view moves (no silent rotation). Stops when you land.

- Silent Aura (Combat): kill aura with silent rotations (ClientPlayerEntityMixin swaps the server-side yaw/pitch into the movement packet while it is sent; your view never moves).
  Modes IDLE / ATTACKING / FLICKING_AWAY. It turns toward a jittered aim point (3 smoothed random offsets, new goal every 100-1000 ms) at Aim speed x a log-normal random factor (1.4-3.0x, slower when the
  target is closer than 0.8 blocks), attacks only when charged AND the server-side rotation points at the target's hitbox, then flicks back toward your real view for 2-4 ticks.
  Settings: Range, Rotate range, Players, Other mobs, Min charge, Aim speed, Aim jitter, Flick away after hit. Ties between targets go to the lower entity id.

- (Old, replaced by Auto Weapon) Stun slam (was inside Auto Mace Swap's settings, replaces the separate Auto Stun Slam module): turn on "Stun slam". While you fall (Slam min fall, default 1.5 blocks) with an axe and a mace in the hotbar and your crosshair on a player blocking with a shield (facing you),
  it hits with the axe (shield disabled), waits Slam smash delay ticks (default 0 = the mace hit is sent in the same tick as the axe hit, so you are still in the air), hits with the mace (Breach / Density chosen as usual) and puts your slot back. Settings: Slam min fall, Slam smash delay, Slam min charge, Slam only while falling.

- Silent rotation rework (util/Rotations.java): the server-side rotation now lives in ONE shared place. A module claims it with Rotations.set(this, yaw, pitch) (returns false if another module owns it) and gives it back with Rotations.release(this).
  While it is active the movement packet carries it (ClientPlayerEntityMixin) and your look vector / crosshair picking follows it (EntityMixin#getRotationVec); your camera is never touched.
  Silent Aura and Auto Mace Swap ("Silent aim", next to "Aim at target") both use it, so they can no longer fight over the rotation. Not done: movement fix, silent item-use packets.
