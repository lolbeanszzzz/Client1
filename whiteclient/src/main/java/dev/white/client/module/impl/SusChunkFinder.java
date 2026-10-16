package dev.white.client.module.impl;

import dev.white.client.util.SdfText;
import dev.white.client.module.Category;
import dev.white.client.module.Module;
import dev.white.client.setting.Setting;
import dev.white.client.util.Friends;
import dev.white.client.util.Gfx;
import dev.white.client.util.Look;
import dev.white.client.util.Ui;
import dev.white.client.util.Projector;
import dev.white.client.util.WorldGfx;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rates every loaded chunk on how likely it is that a player base is in it and floats a
 * percentage tag above the worst ones.
 *
 * Each kind of evidence (machines, storage, farming, built blocks, torches underground, item frames
 * and mobs, old growth) turns into a 0..1 strength, and the strengths are combined like independent
 * clues (noisy-OR), so several different kinds of evidence push the percentage up much faster than
 * one kind alone. The neighbouring 8 chunks add a little, because bases cross chunk borders.
 * The percentage is a heuristic score, not a real probability.
 */
public class SusChunkFinder extends Module {
    private enum Sig {
        //        label           reliability  k   structure scale
        MACHINE("machines",       0.85,        3,  0.4),
        STORAGE("storage",        0.70,        4,  0.3),
        FARM   ("farming",        0.55,        8,  0.3),
        PLACED ("built blocks",   0.55,        14, 0.2),
        LIGHT  ("torches",        0.50,        8,  0.2),
        ENTITY ("frames/mobs",    0.55,        5,  0.5),
        GROWTH ("old growth",     0.20,        12, 1.0),
        REDSTONE("redstone",      0.75,        6,  0.4),
        PORTAL ("portals",        0.55,        6,  0.3),
        DECOR  ("crafted/decor",  0.50,        10, 0.3);

        final String label;
        final double reliability, k, structScale;
        Sig(String label, double reliability, double k, double structScale) {
            this.label = label; this.reliability = reliability; this.k = k; this.structScale = structScale;
        }
    }

    // marker bits found while scanning, used to spot natural structures (mineshafts, villages, ...)
    private static final int M_COBWEB = 1, M_RAIL = 2, M_STRUCT = 4, M_WOOD = 8, M_FENCE = 16,
            M_PLAYER = 32, M_SHIPWOOD = 64, M_TRIALCORE = 128, M_TUFF = 256, M_COPPERFX = 512,
            M_DWOOD = 1024, M_DFENCE = 2048;   // oak / dark oak planks / fences found below y 50 (mineshaft supports)   // M_PLAYER: machines/farms/torches/beds/doors/...; M_SHIPWOOD: planks, trapdoors, fences

    /**
     * Mineshaft evidence inside one chunk: cobwebs together with rails or oak/dark-oak wood, or the
     * rails + fence + planks combination. Most chunks of a mineshaft only hold some of these, so the
     * result is also spread to the neighbouring chunks (see mineChunks).
     */
    /** Trail chamber: a trial spawner / vault / heavy core, or tuff bricks together with copper bulbs / grates / doors. */
    private static boolean isTrial(int m) {
        return (m & M_TRIALCORE) != 0 || ((m & M_TUFF) != 0 && (m & M_COPPERFX) != 0);
    }

    private static boolean isMine(int m) {
        boolean web = (m & M_COBWEB) != 0, rail = (m & M_RAIL) != 0;
        boolean wood = (m & M_WOOD) != 0, fence = (m & M_FENCE) != 0;
        boolean deepWood = (m & M_DWOOD) != 0, deepFence = (m & M_DFENCE) != 0;
        return (web && (rail || wood || fence)) || (rail && wood && fence)
                // corridor chunks often hold only the supports (planks + fences, maybe a rail or a torch), no cobweb
                || (deepWood && deepFence) || (rail && (deepWood || deepFence));
    }

    private static final Set<String> MACHINE_IDS = Set.of("hopper", "dropper", "dispenser", "observer", "piston",
            "sticky_piston", "repeater", "comparator", "tnt", "daylight_detector", "beacon", "enchanting_table",
            "redstone_lamp", "note_block", "jukebox", "crafter");
    private static final Set<String> REDSTONE_IDS = Set.of("redstone_wire", "lever", "tripwire_hook", "target",
            "slime_block", "honey_block", "lightning_rod", "sculk_sensor", "calibrated_sculk_sensor", "redstone_torch",
            "redstone_wall_torch", "powered_rail", "activator_rail");
    private static final Set<String> DECOR_IDS = Set.of("flower_pot", "bookshelf", "chiseled_bookshelf", "lectern",
            "brewing_stand", "cauldron", "water_cauldron", "lava_cauldron", "grindstone", "lodestone", "respawn_anchor",
            "conduit", "bricks", "smooth_stone", "quartz_block", "chiseled_quartz_block", "quartz_pillar",
            "smooth_quartz", "polished_andesite", "polished_diorite", "polished_granite", "decorated_pot",
            "beehive", "jack_o_lantern", "carved_pumpkin", "cake", "end_crystal");
    private static final Set<String> FARM_IDS = Set.of("farmland", "wheat", "carrots", "potatoes", "beetroots",
            "nether_wart", "composter");
    private static final Set<String> PLACED_IDS = Set.of("crafting_table", "furnace", "blast_furnace", "smoker",
            "smithing_table", "fletching_table", "cartography_table", "loom", "stonecutter", "anvil", "chipped_anvil",
            "damaged_anvil", "ladder", "scaffolding", "glass", "tinted_glass", "glass_pane", "obsidian", "iron_block",
            "gold_block", "diamond_block", "emerald_block", "netherite_block", "lapis_block", "redstone_block");
    private static final Set<String> STRUCT_IDS = Set.of("end_portal_frame", "bell", "reinforced_deepslate",
            "sculk_shrieker", "trial_spawner", "vault", "nether_brick_fence", "gilded_blackstone", "purpur_block",
            "prismarine_bricks", "chiseled_sandstone", "crying_obsidian");

    private static final SoundEvent ALERT = SoundEvent.of(Identifier.of("whiteclient", "alert"));

    // ---- settings ----
    private final Setting.Num range = add(new Setting.Num("Range (chunks)", 6, 2, 12, 1));
    // how fast it searches: raise both when flying (elytra) and it cannot keep up; very high speed can cost FPS
    private final Setting.Num scanSpeed = add(new Setting.Num("Scan speed (chunks/tick)", 3, 1, 40, 1));
    private final Setting.Num passDelay = add(new Setting.Num("Rescan delay (ticks)", 40, 0, 40, 5));
    private final Setting.Bool skipScanned = add(new Setting.Bool("Skip scanned chunks", true));   // each loaded chunk is scanned once, not every pass
    private final Setting.Button rescanAll = add(new Setting.Button("Rescan all chunks", () -> { this.scanned.clear(); this.timer = 1000; }));
    private final Setting.Bool progress = add(new Setting.Bool("Scan progress", true));   // on-screen readout: chunks scanned this pass, unloaded chunks, last pass time
    private final Setting.Num minPercent = add(new Setting.Num("Min % to highlight", 30, 10, 90, 5));
    private final Setting.Num maxHighlights = add(new Setting.Num("Max highlights", 60, 5, 200, 5));
    private final Setting.Num maxDistance = add(new Setting.Num("Max distance", 800, 100, 3000, 100));
    private final Setting.Bool fillHighlight = add(new Setting.Bool("Fill nearest", true));
    private final Setting.Bool flatFloor = add(new Setting.Bool("Flat at world bottom", true));
    private final Setting.Bool atBase = add(new Setting.Bool("Marker at base height", true));   // draw at the clues' real height instead of the flat plane
    private final Setting.Num markerSize = add(new Setting.Num("Marker size", 4, 2, 12, 1));
    private final Setting.Bool indicator = add(new Setting.Bool("Corner indicator", true));
    private final Setting.Num baseAt = add(new Setting.Num("Base indicator at %", 50, 20, 95, 5));
    private final Setting.Bool hoverInfo = add(new Setting.Bool("Hover info", true));
    private final Setting.Bool chatAlert = add(new Setting.Bool("Chat alert", true));
    private final Setting.Bool soundAlert = add(new Setting.Bool("Alert sound", false));
    private final Setting.Num alertAt = add(new Setting.Num("Alert at %", 60, 20, 95, 5));
    private final Setting.Bool flagPlayers = add(new Setting.Bool("Flag live players", true));
    private final Setting.Bool ignoreFriends = add(new Setting.Bool("Ignore friends", true));
    private final Setting.Bool playerChat = add(new Setting.Bool("Player chat alert", true));
    private final Setting.Bool playerSound = add(new Setting.Bool("Player alert sound", true));
    private final Setting.Num playerKeep = add(new Setting.Num("Player flag time (s)", 120, 10, 600, 10));
    private final Setting.Bool ignoreStructures = add(new Setting.Bool("Ignore structures", true));
    private final Setting.Bool regionMap = add(new Setting.Bool("Region map", true));
    private final Setting.Num mapRadius = add(new Setting.Num("Map radius (chunks)", 12, 4, 24, 2));
    private final Setting.Num mapCell = add(new Setting.Num("Map cell size", 4, 2, 8, 1));
    private final Setting.Bool tracerBest = add(new Setting.Bool("Tracer to best", false));
    private final Setting.Bool machines = add(new Setting.Bool("Sig: machines", true));
    private final Setting.Bool storage = add(new Setting.Bool("Sig: storage", true));
    private final Setting.Bool farming = add(new Setting.Bool("Sig: farming", true));
    private final Setting.Bool placed = add(new Setting.Bool("Sig: built blocks", true));
    private final Setting.Bool light = add(new Setting.Bool("Sig: torches", true));
    private final Setting.Bool entities = add(new Setting.Bool("Sig: frames/mobs", true));
    private final Setting.Bool growth = add(new Setting.Bool("Sig: old growth", true));
    private final Setting.Bool redstone = add(new Setting.Bool("Sig: redstone", true));
    private final Setting.Bool portals = add(new Setting.Bool("Sig: portals", true));
    private final Setting.Bool decor = add(new Setting.Bool("Sig: crafted/decor", true));
    private final Setting.Bool pets = add(new Setting.Bool("Sig: pets/leashed", true));
    // how strongly each check pushes the percentage (100 = default, 0 = no effect, 200 = double)
    private final Setting.Num wMachines = add(new Setting.Num("Weight: machines %", 100, 0, 200, 10));
    private final Setting.Num wStorage = add(new Setting.Num("Weight: storage %", 100, 0, 200, 10));
    private final Setting.Num wFarming = add(new Setting.Num("Weight: farming %", 100, 0, 200, 10));
    private final Setting.Num wPlaced = add(new Setting.Num("Weight: built blocks %", 100, 0, 200, 10));
    private final Setting.Num wLight = add(new Setting.Num("Weight: torches %", 100, 0, 200, 10));
    private final Setting.Num wEntities = add(new Setting.Num("Weight: frames/mobs %", 100, 0, 200, 10));
    private final Setting.Num wGrowth = add(new Setting.Num("Weight: old growth %", 100, 0, 200, 10));
    private final Setting.Num wRedstone = add(new Setting.Num("Weight: redstone %", 100, 0, 200, 10));
    private final Setting.Num wPortals = add(new Setting.Num("Weight: portals %", 100, 0, 200, 10));
    private final Setting.Num wDecor = add(new Setting.Num("Weight: crafted/decor %", 100, 0, 200, 10));

    private static final int LIVE = 0xFF38D9FF;            // colour of flagged (live player) chunks
    private static final double LIVE_RELIABILITY = 0.60;   // a player standing in a chunk counts as this strong a clue
    private static final int FILL_NEAREST = 24;            // nearest chunks that get a translucent fill (cheap now: it is all one GPU batch)

    // ---- data ----
    /** A player seen standing in a chunk; the chunk stays flagged for "Player flag time" after the last sighting. */
    private static final class Spot {
        String name;
        double x, y, z;
        long last;
        Spot(String name, double x, double y, double z, long last) { this.name = name; this.x = x; this.y = y; this.z = z; this.last = last; }
    }

    private static final class Info {
        final int cx, cz;
        final double[] counts = new double[Sig.values().length];
        int markers;
        double hx, hy, hz, hw;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double own, pct;
        String reasons = "";
        final List<String[]> rows = new ArrayList<>();   // tooltip lines: {points, text}
        int signals;                                      // how many different kinds of evidence
        Spot live;                                        // non-null while a player is flagged in this chunk
        boolean structure, mine, shipRaw, ship, trial;

        Info(int cx, int cz) { this.cx = cx; this.cz = cz; }

        void add(Sig s, double n, double x, double y, double z) {
            counts[s.ordinal()] += n;
            double base = switch (s) {
                case MACHINE, STORAGE, REDSTONE -> 3.0;
                case PORTAL -> 0.5;
                case FARM -> 2.0;
                case GROWTH -> 0.2;
                default -> 1.0;
            };
            double w = base * n;
            hx += x * w; hy += y * w; hz += z * w; hw += w;
            if (s != Sig.GROWTH) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        }

        double midX() { return hw > 0 ? hx / hw : cx * 16 + 8; }
        double midZ() { return hw > 0 ? hz / hw : cz * 16 + 8; }
        double midY(double fallback) { return hw > 0 ? hy / hw : fallback; }
        long key() { return ChunkPos.toLong(cx, cz); }

        /** Highlight volume: the whole chunk footprint, from just under the lowest clue to just over the highest. */
        void box(double[] out, double fallbackY) {
            double lo = minY <= maxY ? minY : fallbackY - 2, hi = minY <= maxY ? maxY : fallbackY + 3;
            lo -= 1.0; hi += 2.0;
            if (hi - lo < 6) { double m = (lo + hi) / 2; lo = m - 3; hi = m + 3; }
            out[0] = cx * 16.0; out[1] = lo; out[2] = cz * 16.0;
            out[3] = cx * 16.0 + 16; out[4] = hi; out[5] = cz * 16.0 + 16;
        }
    }

    private final Map<Block, Sig> classes = new HashMap<>();
    private final Map<Block, Integer> markers = new HashMap<>();

    private final Map<Long, Info> infos = new HashMap<>();      // remembered chunks (>= 12%)
    private Map<Long, Info> fresh = new HashMap<>();             // chunks scanned in the current pass
    private volatile List<Info> display = List.of();
    private final Set<Long> alerted = new HashSet<>();
    private final Set<Long> mineChunks = new HashSet<>();        // chunks that show mineshaft evidence
    private final Set<Long> trialChunks = new HashSet<>();       // chunks that show trail chamber evidence
    private final List<long[]> queue = new ArrayList<>();
    /** Chunks already scanned: chunk key -> identity of the loaded WorldChunk we scanned. A chunk is only scanned again
     *  if it was unloaded and loaded anew (a different object), or after "Rescan all chunks". */
    private final Map<Long, Integer> scanned = new HashMap<>();
    private int queuePos, timer;
    private World lastWorld;
    private final double[] o = new double[2];
    private final double[] bx = new double[6];
    private final Map<Long, Spot> liveSpots = new HashMap<>();   // chunk -> player seen there
    private final Map<UUID, Long> seenAt = new HashMap<>();      // player -> last time seen (for "new player" alerts)

    public SusChunkFinder() {
        super("Sus Chunk Finder", Category.BASE);
        for (Block b : Registries.BLOCK) {
            String p = Registries.BLOCK.getId(b).getPath();
            Sig s = classify(p);
            if (s != null) classes.put(b, s);
            int bits = 0;
            if (s == Sig.MACHINE || s == Sig.FARM || s == Sig.LIGHT || s == Sig.REDSTONE || s == Sig.PORTAL) bits |= M_PLAYER;
            if (s == Sig.PLACED) bits |= (p.endsWith("_planks") || p.endsWith("_trapdoor") || p.endsWith("_fence")) ? M_SHIPWOOD : M_PLAYER;
            if (p.equals("cobweb")) bits |= M_COBWEB;
            if (p.endsWith("rail")) bits |= M_RAIL;
            if (STRUCT_IDS.contains(p)) bits |= M_STRUCT;
            if (p.equals("trial_spawner") || p.equals("vault") || p.equals("heavy_core")) bits |= M_TRIALCORE;
            if (p.contains("tuff_brick") || p.startsWith("polished_tuff") || p.startsWith("chiseled_tuff")) bits |= M_TUFF;
            if (p.contains("copper_bulb") || p.contains("copper_grate") || p.contains("copper_door")
                    || p.contains("copper_trapdoor") || p.contains("chiseled_copper")) bits |= M_COPPERFX;
            if (p.equals("oak_planks") || p.equals("dark_oak_planks")) bits |= M_WOOD;
            if (p.equals("oak_fence") || p.equals("dark_oak_fence")) bits |= M_FENCE;
            if (bits != 0) markers.put(b, bits);
        }
        classes.put(Blocks.AMETHYST_CLUSTER, Sig.GROWTH);
    }

    private static Sig classify(String p) {
        if (MACHINE_IDS.contains(p)) return Sig.MACHINE;
        if (REDSTONE_IDS.contains(p) || p.endsWith("_button") || p.endsWith("_pressure_plate")) return Sig.REDSTONE;
        if (p.equals("nether_portal")) return Sig.PORTAL;
        if (DECOR_IDS.contains(p) || p.startsWith("stripped_") || p.endsWith("_candle") || p.equals("candle")
                || p.endsWith("_skull") || p.endsWith("_head") || p.endsWith("_carpet")) return Sig.DECOR;
        if (FARM_IDS.contains(p)) return Sig.FARM;
        if (PLACED_IDS.contains(p) || p.endsWith("planks") || p.endsWith("_bed") || p.endsWith("_wool")
                || p.endsWith("_concrete") || p.endsWith("glazed_terracotta") || p.endsWith("_door")
                || p.endsWith("_trapdoor") || p.endsWith("_sign") || p.endsWith("_fence_gate")
                || (p.endsWith("_fence") && !p.startsWith("nether_brick")) || p.endsWith("_stained_glass")
                || p.endsWith("_stained_glass_pane") || p.endsWith("_banner")) return Sig.PLACED;
        if (p.endsWith("torch") || p.endsWith("lantern") || p.equals("glowstone") || p.equals("shroomlight")
                || p.equals("end_rod") || p.equals("campfire") || p.equals("soul_campfire")) return Sig.LIGHT;
        return null;
    }

    private double weight(Sig s) {
        return switch (s) {
            case MACHINE -> wMachines.value;
            case STORAGE -> wStorage.value;
            case FARM -> wFarming.value;
            case PLACED -> wPlaced.value;
            case LIGHT -> wLight.value;
            case ENTITY -> wEntities.value;
            case GROWTH -> wGrowth.value;
            case REDSTONE -> wRedstone.value;
            case PORTAL -> wPortals.value;
            case DECOR -> wDecor.value;
        } / 100.0;
    }

    private boolean on(Sig s) {
        return switch (s) {
            case MACHINE -> machines.value;
            case STORAGE -> storage.value;
            case FARM -> farming.value;
            case PLACED -> placed.value;
            case LIGHT -> light.value;
            case ENTITY -> entities.value;
            case GROWTH -> growth.value;
            case REDSTONE -> redstone.value;
            case PORTAL -> portals.value;
            case DECOR -> decor.value;
        };
    }

    @Override
    protected void onEnable() { timer = 1000; }

    @Override
    protected void onDisable() {
        dev.white.client.util.Finds.set("Base", List.of());
        infos.clear(); fresh = new HashMap<>(); display = List.of(); alerted.clear(); mineChunks.clear(); trialChunks.clear(); scanned.clear(); queue.clear(); queuePos = 0;
        liveSpots.clear(); seenAt.clear();
    }

    // ---------------- scanning (a few chunks per tick, so it never causes a lag spike) ----------------

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (mc.world != lastWorld) {                       // new world / dimension
            lastWorld = mc.world;
            infos.clear(); fresh = new HashMap<>(); display = List.of(); alerted.clear(); mineChunks.clear(); trialChunks.clear(); scanned.clear(); queue.clear(); queuePos = 0;
        liveSpots.clear(); seenAt.clear();
            timer = 1000;
        }

        // any setting changed (a "Sig:" check, weights, Ignore structures...): re-rate what we remember right away
        // and rescan, instead of waiting for the next pass, so turning a check on shows its reasons straight away
        long st = settingsStamp();
        if (st != stamp) {
            stamp = st;
            rebuildDisplay();
            if (queuePos >= queue.size()) timer = 1000;
        }

        tickLive();

        if (queuePos >= queue.size()) {
            if (++timer < passDelay.intValue()) return;     // a new pass every "Rescan delay" ticks (default 40 = ~2 seconds)
            timer = 0;
            beginPass();
            if (queue.isEmpty()) return;
        }

        // "Scan speed" chunks per tick, but never more than ~12 ms of work in one tick so a high value cannot freeze the game
        int speed = scanSpeed.intValue();
        long t0 = System.nanoTime();
        for (int n = 0; n < speed && queuePos < queue.size(); n++) {
            long[] c = queue.get(queuePos++);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk((int) c[0], (int) c[1]);
            if (chunk != null) {
                Info i = scanChunk(chunk);
                fresh.put(i.key(), i);
                scanned.put(i.key(), System.identityHashCode(chunk));
            } else skipped++;
            if (System.nanoTime() - t0 > 12_000_000L) break;
        }
        if (queuePos >= queue.size()) finishPass();
    }

    private long stamp = Long.MIN_VALUE;
    private long passStart, lastPassMs;
    private int skipped;                                         // chunks in range that were not loaded yet this pass

    private long settingsStamp() {
        long h = 17;
        for (Setting s : settings) {
            long v = 0;
            if (s instanceof Setting.Bool b) v = b.value ? 1 : 0;
            else if (s instanceof Setting.Num n) v = Double.doubleToLongBits(n.value);
            else if (s instanceof Setting.Hue hu) v = Float.floatToIntBits(hu.hue) * 31L + Float.floatToIntBits(hu.sat) * 17L + Float.floatToIntBits(hu.val);
            h = h * 31 + v;
        }
        return h;
    }

    private void beginPass() {
        queue.clear(); queuePos = 0; fresh = new HashMap<>();
        passStart = System.nanoTime(); skipped = 0;
        ChunkPos cp = mc.player.getChunkPos();
        int rad = range.intValue();
        var cm = mc.world.getChunkManager();
        if (skipScanned.value) {
            // forget chunks that have been unloaded, so they are scanned again if they come back (their blocks may have changed)
            scanned.keySet().removeIf(k -> cm.getWorldChunk(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k)) == null);
        } else scanned.clear();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                int x = cp.x + dx, z = cp.z + dz;
                if (skipScanned.value) {
                    WorldChunk loaded = cm.getWorldChunk(x, z);
                    Integer seen = scanned.get(ChunkPos.toLong(x, z));
                    if (loaded != null && seen != null && seen == System.identityHashCode(loaded)) continue;   // already scanned
                }
                queue.add(new long[]{x, z});
            }
        queue.sort((a, b) -> Long.compare(
                (a[0] - cp.x) * (a[0] - cp.x) + (a[1] - cp.z) * (a[1] - cp.z),
                (b[0] - cp.x) * (b[0] - cp.x) + (b[1] - cp.z) * (b[1] - cp.z)));   // nearest chunks first
    }

    private boolean interesting(BlockState s) {
        Block b = s.getBlock();
        return classes.containsKey(b) || markers.containsKey(b) || b == Blocks.KELP;
    }

    private Info scanChunk(WorldChunk chunk) {
        Info info = new Info(chunk.getPos().x, chunk.getPos().z);
        int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();

        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::interesting)) continue;
            int baseY = (chunk.getBottomSectionCoord() + i) << 4;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.isAir()) continue;
                        Block b = st.getBlock();
                        Integer mk = markers.get(b);
                        if (mk != null) {
                            info.markers |= mk;
                            if (baseY + y < 50) {
                                if ((mk & M_WOOD) != 0) info.markers |= M_DWOOD;
                                if ((mk & M_FENCE) != 0) info.markers |= M_DFENCE;
                            }
                        }
                        Sig sg = classes.get(b);
                        if (b == Blocks.KELP && st.contains(Properties.AGE_25) && st.get(Properties.AGE_25) >= 25)
                            sg = Sig.GROWTH;                       // fully grown kelp
                        if (sg == null) continue;
                        int wy = baseY + y;
                        double c = 1;
                        if (sg == Sig.LIGHT && wy >= 55) continue;  // only torches underground count
                        if (sg == Sig.FARM && wy < 45) c = 2;       // farmland / crops underground count double
                        info.add(sg, c, baseX + x + 0.5, wy + 0.5, baseZ + z + 0.5);
                    }
        }

        // storage: chests, barrels, shulker boxes, ender chests (a double chest counts once)
        int stores = 0, spawners = 0;
        List<BlockEntity> storeList = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity) { spawners++; continue; }
            boolean store = be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                    || be instanceof ShulkerBoxBlockEntity || be instanceof EnderChestBlockEntity;
            if (!store) continue;
            BlockState st = be.getCachedState();
            if (st.contains(ChestBlock.CHEST_TYPE) && st.get(ChestBlock.CHEST_TYPE) == ChestType.RIGHT) continue;
            stores++;
            storeList.add(be);
        }
        if (!(spawners > 0 && stores <= 2)) {                     // a lone dungeon chest is natural
            for (BlockEntity be : storeList)
                info.add(Sig.STORAGE, 1, be.getPos().getX() + 0.5, be.getPos().getY() + 0.5, be.getPos().getZ() + 0.5);
        }

        // shipwreck: in an ocean or beach, only wood (planks/trapdoors/fences) and a few chests, nothing a player would build
        if ((info.markers & M_PLAYER) == 0 && ((info.markers & M_SHIPWOOD) != 0 || stores > 0)
                && info.counts[Sig.STORAGE.ordinal()] <= 4) {
            var biome = mc.world.getBiome(new BlockPos(baseX + 8, 62, baseZ + 8));
            info.shipRaw = biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_BEACH);
        }
        return info;
    }

    private void finishPass() {
        lastPassMs = (System.nanoTime() - passStart) / 1_000_000L;
        // item frames, armor stands, named mobs and animal/villager pens, counted per chunk
        for (Entity e : mc.world.getEntities()) {
            double w = 0;
            if (e instanceof ItemFrameEntity || e instanceof ArmorStandEntity
                    || e instanceof net.minecraft.entity.decoration.painting.PaintingEntity) w = 1.5;
            else if (pets.value && ((e instanceof net.minecraft.entity.passive.TameableEntity t && t.isTamed())
                    || (e instanceof net.minecraft.entity.passive.AbstractHorseEntity h && h.isTame())
                    || (e instanceof net.minecraft.entity.mob.MobEntity m && m.isLeashed()))) w = 2.0;
            else if (e instanceof LivingEntity && !(e instanceof PlayerEntity)) {
                if (e.hasCustomName()) w = 1.5;
                else if (e instanceof AnimalEntity || e instanceof VillagerEntity) w = 0.5;
            }
            if (w == 0) continue;
            Info i = fresh.get(ChunkPos.toLong(e.getBlockX() >> 4, e.getBlockZ() >> 4));
            if (i != null) i.add(Sig.ENTITY, w, e.getX(), e.getY(), e.getZ());
        }

        Set<Long> newStructure = new HashSet<>();                   // chunks that only now turned out to be mineshaft / trail chamber
        for (Info i : fresh.values()) {                             // remember which chunks look like mineshaft / trail chamber
            if (isMine(i.markers)) { if (mineChunks.add(i.key())) newStructure.add(i.key()); } else mineChunks.remove(i.key());
            if (isTrial(i.markers)) { if (trialChunks.add(i.key())) newStructure.add(i.key()); } else trialChunks.remove(i.key());
        }
        for (Info i : fresh.values()) {
            rate(i);
            if (i.own >= 0.12) infos.put(i.key(), i); else infos.remove(i.key());
        }
        // A chunk scanned earlier was rated before its neighbour (which shows the structure) was loaded: it was flagged
        // as a base. Now that the neighbour is known, rate those chunks again so the structure discount applies.
        if (!newStructure.isEmpty()) {
            List<Info> redo = new ArrayList<>();
            for (Info o : infos.values()) {
                if (fresh.containsKey(o.key())) continue;
                for (long k : newStructure) {
                    if (Math.abs(o.cx - (int) k) <= 2 && Math.abs(o.cz - (int) (k >>> 32)) <= 2) { redo.add(o); break; }
                }
            }
            for (Info o : redo) {
                rate(o);
                if (o.own >= 0.12) infos.put(o.key(), o); else infos.remove(o.key());
            }
        }
        fresh = new HashMap<>();
        rebuildDisplay();
        List<Info> sorted = display;

        // one-time alerts
        int sent = 0;
        for (Info i : sorted) {
            int pct = (int) Math.round(i.pct * 100);
            if (pct < alertAt.intValue()) break;
            if (!chatAlert.value && !soundAlert.value) break;       // nothing to announce: keep chunks un-alerted for when it is switched on
            if (i.live != null) continue;                           // live players have their own alert
            if (alerted.contains(i.key())) continue;
            if (sent >= 3) break;                                   // the rest are announced on the next passes, not lost
            alerted.add(i.key());
            sent++;
            if (chatAlert.value) {
                mc.player.sendMessage(Text.literal("[Sus Chunk] ").formatted(Formatting.GOLD)
                        .append(Text.literal(pct + "% chance of a base near " + (int) i.midX() + ", " + (int) i.midZ()
                                + (i.reasons.isEmpty() ? "" : " (" + i.reasons + ")")).formatted(Formatting.YELLOW)), false);
            }
            if (soundAlert.value) mc.player.playSound(ALERT, 1.0f, 1.0f);
        }
    }

    // ---------------- live players ----------------

    /** Flags the chunk of every other player in view and tells you the first time each one shows up. */
    private void tickLive() {
        if (!flagPlayers.value) {
            if (!liveSpots.isEmpty()) { liveSpots.clear(); seenAt.clear(); rebuildDisplay(); }
            return;
        }
        long now = System.currentTimeMillis(), keep = (long) playerKeep.value * 1000L;
        boolean changed = false;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.isSpectator()) continue;
            if (ignoreFriends.value && Friends.is(p)) continue;
            String name = p.getName().getString();
            long key = ChunkPos.toLong(p.getBlockX() >> 4, p.getBlockZ() >> 4);
            Spot sp = liveSpots.get(key);
            if (sp == null) { liveSpots.put(key, new Spot(name, p.getX(), p.getY(), p.getZ(), now)); changed = true; }
            else { sp.name = name; sp.x = p.getX(); sp.y = p.getY(); sp.z = p.getZ(); sp.last = now; }
            Long prev = seenAt.get(p.getUuid());
            if (prev == null || now - prev > keep) notifyPlayer(name, p.getX(), p.getY(), p.getZ());
            seenAt.put(p.getUuid(), now);
        }
        for (Iterator<Spot> it = liveSpots.values().iterator(); it.hasNext(); )
            if (now - it.next().last > keep) { it.remove(); changed = true; }
        seenAt.values().removeIf(t -> now - t > keep);
        if (changed) rebuildDisplay();
    }

    private void notifyPlayer(String name, double x, double y, double z) {
        double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
        long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
        if (playerChat.value) {
            mc.player.sendMessage(Text.literal("[Sus Chunk] ").formatted(Formatting.AQUA)
                    .append(Text.literal("LIVE PLAYER " + name + " at " + (int) x + ", " + (int) z + " (chunk "
                            + ((int) Math.floor(x) >> 4) + ", " + ((int) Math.floor(z) >> 4) + ", " + dist + "m away)").formatted(Formatting.WHITE)), false);
        }
        if (playerSound.value) mc.player.playSound(ALERT, 1.0f, 1.0f);
    }

    /** Re-rates every remembered chunk plus every flagged chunk, spreads a little score into neighbours and sorts. */
    private void rebuildDisplay() {
        Map<Long, Info> all = new HashMap<>(infos);
        for (Map.Entry<Long, Spot> e : liveSpots.entrySet()) {
            all.computeIfAbsent(e.getKey(), k -> {
                Info i = new Info(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k));
                i.hx = e.getValue().x; i.hy = e.getValue().y; i.hz = e.getValue().z; i.hw = 1;   // no block evidence: centre on the player
                return i;
            });
        }
        for (Info i : all.values()) rate(i);                        // also re-applies the current toggles to remembered chunks

        // a base spreads across chunk borders: neighbours add a little
        for (Info i : all.values()) {
            double none = 1 - i.own;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Info n = all.get(ChunkPos.toLong(i.cx + dx, i.cz + dz));
                    if (n != null) none *= 1 - 0.35 * n.own;
                }
            i.pct = Math.min(0.99, 1 - none);
        }
        List<Info> sorted = new ArrayList<>(all.values());
        sorted.sort((a, b) -> Double.compare(b.pct, a.pct));
        display = sorted;
        publish(sorted);
    }

    /** Shares the strongest results with the Found List screen. */
    private void publish(List<Info> sorted) {
        List<dev.white.client.util.Finds.Find> out = new ArrayList<>();
        double py = mc.player != null ? mc.player.getY() : 64;
        for (Info i : sorted) {
            int pct = (int) Math.round(i.pct * 100);
            if (i.live == null && pct < minPercent.intValue()) continue;
            if (out.size() >= 100) break;
            String label = (i.live != null ? "Player " + i.live.name : pct + "% base") + (i.reasons.isEmpty() ? "" : " (" + i.reasons + ")");
            out.add(new dev.white.client.util.Finds.Find("Base", label, (int) i.midX(), (int) i.midY(py), (int) i.midZ(), pct));
        }
        dev.white.client.util.Finds.set("Base", out);
    }

    /** Turns a chunk's counts into its own probability-like score and a short list of reasons. */
    private void rate(Info i) {
        boolean mine = isMine(i.markers);
        for (int dx = -2; dx <= 2 && !mine; dx++)                   // a mineshaft continues into the next chunks (corridors are long)
            for (int dz = -2; dz <= 2 && !mine; dz++)
                if (mineChunks.contains(ChunkPos.toLong(i.cx + dx, i.cz + dz))) mine = true;
        boolean trial = isTrial(i.markers);
        for (int dx = -2; dx <= 2 && !trial; dx++)                   // a trail chamber is big: it spans several chunks
            for (int dz = -2; dz <= 2 && !trial; dz++)
                if (trialChunks.contains(ChunkPos.toLong(i.cx + dx, i.cz + dz))) trial = true;
        i.trial = ignoreStructures.value && trial;
        i.mine = ignoreStructures.value && mine;
        i.ship = ignoreStructures.value && i.shipRaw;
        i.structure = ignoreStructures.value && (mine || trial || i.ship || (i.markers & M_STRUCT) != 0);
        double none = 1;
        Sig[] all = Sig.values();
        double[] contrib = new double[all.length];
        for (Sig s : all) {
            if (!on(s)) continue;
            // mineshafts are full of planks/fences/torches and shipwrecks of planks/chests: those never count there
            boolean zero = (i.trial && s != Sig.FARM && s != Sig.PORTAL && s != Sig.GROWTH)   // dispensers, chests, pots, copper, lanterns: all natural there
                    || (i.mine && (s == Sig.PLACED || s == Sig.LIGHT || s == Sig.DECOR)) || (i.ship && (s == Sig.PLACED || s == Sig.STORAGE || s == Sig.DECOR));
            double scale = zero ? 0.0 : i.structure ? s.structScale : 1.0;
            double c = i.counts[s.ordinal()] * scale;
            double p = Math.min(0.99, s.reliability * weight(s) * (1 - Math.exp(-c / s.k)));
            contrib[s.ordinal()] = p;
            none *= 1 - p;
        }
        i.live = liveSpots.get(i.key());
        if (i.live != null) none *= 1 - LIVE_RELIABILITY;
        i.own = 1 - none;

        // reasons, strongest first: a short line for chat alerts and the full list for the hover tooltip
        i.rows.clear();
        StringBuilder sb = new StringBuilder();
        boolean[] used = new boolean[all.length];
        int shown = 0;
        i.signals = 0;
        for (int n = 0; n < all.length; n++) {
            int best = -1;
            for (int k = 0; k < all.length; k++)
                if (!used[k] && contrib[k] >= 0.04 && (best < 0 || contrib[k] > contrib[best])) best = k;
            if (best < 0) break;
            used[best] = true;
            i.signals++;
            long cnt = Math.round(i.counts[best]);
            i.rows.add(new String[]{"+" + Math.round(contrib[best] * 100), all[best].label + "  " + cnt});
            if (contrib[best] >= 0.08 && shown++ < 3) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(all[best].label).append(' ').append(cnt);
            }
        }
        if (i.live != null) {
            i.rows.add(0, new String[]{"+" + Math.round(LIVE_RELIABILITY * 100), "live player  " + i.live.name});
            sb.insert(0, "player " + i.live.name + (sb.length() > 0 ? " | " : ""));
        }
        if (i.structure) {
            String t = i.trial ? "trail chamber" : i.mine ? "near mineshaft" : i.ship ? "shipwreck?" : "near structure?";
            i.rows.add(new String[]{"-", t + " (discounted)"});
            sb.append(sb.length() > 0 ? " | " : "").append(t);
        }
        i.reasons = sb.toString();
    }

    // ---------------- drawing ----------------

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final double AIM_PX = 16;   // GUI pixels around the crosshair that count as "aiming at" a marker

    /** Height the marker is drawn at: the clues' real height, or the flat plane (world bottom / your feet). */
    private double markerY(Info i, double py) {
        if (atBase.value && i.hw > 0) return i.midY(py);
        return flatFloor.value ? mc.world.getBottomY() + 0.05 : Math.floor(py) + 0.02;
    }

    private static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));             // 0 = north, 90 = east (+Z is south in Minecraft)
        return COMPASS[(int) Math.round(((a % 360) + 360) % 360 / 45.0) % 8];
    }

    private static int colorOf(Info i, int pct) { return i.live != null ? LIVE : Look.accentOverride ? Look.ACCENT : tierColor(pct); }

    private record Vis(Info info, int pct, double dist) {}

    // what the world pass decided this frame; the HUD pass reads it for the tooltip, markers and tracer
    private List<Vis> curVis = List.of();
    private Vis curHover;

    /**
     * World pass: works out which chunks are in range / aimed at and queues their boxes. They are drawn by WorldGfx
     * in a single GPU call through the game's own projection (Meteor-style), so they stay glued to the world and
     * cost almost nothing, instead of hundreds of GUI rectangles per frame.
     */
    @Override
    public void onWorld() {
        curVis = List.of();
        curHover = null;
        List<Info> list = display;
        if (list.isEmpty()) return;

        Vec3d c = Projector.camPos;
        double maxD = maxDistance.value, max2 = maxD * maxD;
        int min = minPercent.intValue(), limit = maxHighlights.intValue();
        double py = mc.player.getY();

        // 1) every suspected chunk (or flagged player chunk) in range, nearest first
        List<Vis> vis = new ArrayList<>();
        for (Info i : list) {
            int pct = (int) Math.round(i.pct * 100);
            if (i.live == null && pct < min) continue;
            double dx = i.midX() - c.x, dy = i.midY(py) - c.y, dz = i.midZ() - c.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > max2) continue;
            vis.add(new Vis(i, pct, Math.sqrt(d2)));
        }
        if (vis.isEmpty()) return;
        vis.sort((a, b) -> Double.compare(a.dist, b.dist));
        if (vis.size() > limit) {                                   // keep the most suspicious ones (flagged chunks always stay)
            List<Vis> keep = new ArrayList<>();
            List<Vis> rest = new ArrayList<>();
            for (Vis v : vis) (v.info.live != null ? keep : rest).add(v);
            rest.sort((a, b) -> Double.compare(b.pct, a.pct));
            for (int k = 0; k < rest.size() && keep.size() < limit; k++) keep.add(rest.get(k));
            keep.sort((a, b) -> Double.compare(a.dist, b.dist));
            vis = keep;
        }

        // 2) which one is under the crosshair? Three ways to be "aimed at", best first (all see through walls):
        //    a) the crosshair is within a few pixels of the chunk's marker (what you actually see on screen),
        //    b) the view ray passes through the box around the clues,
        //    c) the view ray passes through the chunk's full-height column (so looking at the ground above an
        //       underground base, or at the sky over it, still shows the reasons).
        Vis hover = null;
        if (hoverInfo.value && mc.currentScreen == null) {
            double bestKey = Double.MAX_VALUE;
            double scx = Projector.width() / 2.0, scy = Projector.height() / 2.0;
            double bottom = mc.world.getBottomY(), top = bottom + mc.world.getHeight();
            double[] sp = new double[2];
            for (Vis v : vis) {
                double key = Double.MAX_VALUE;
                if (Projector.project(v.info.midX(), markerY(v.info, py), v.info.midZ(), sp)) {
                    double pd = Math.hypot(sp[0] - scx, sp[1] - scy);
                    if (pd <= AIM_PX) key = pd;
                }
                if (key == Double.MAX_VALUE) {
                    v.info.box(bx, py);
                    double t = Projector.rayBox(bx[0], bx[1], bx[2], bx[3], bx[4], bx[5], maxD);
                    if (t >= 0) key = 1e3 + (t == 0 ? 1e6 + v.dist : t);   // a chunk you are standing in only wins if nothing else is aimed at
                }
                if (key == Double.MAX_VALUE) {
                    double x0 = v.info.cx * 16.0, z0 = v.info.cz * 16.0;
                    double t = Projector.rayBox(x0, bottom, z0, x0 + 16, top, z0 + 16, maxD);
                    if (t >= 0) key = 1e8 + (t == 0 ? 1e6 + v.dist : t);
                }
                if (key < bestKey) { bestKey = key; hover = v; }
            }
        }
        curVis = vis;
        curHover = hover;

        // 3) queue flat squares on one plane (the bottom of the world, or your feet), far to near.
        //    Every suspect gets a small filled square + dot where its clues are; the aimed-at chunk also gets
        //    its full 16x16 footprint outlined.
        double half = markerSize.value / 2.0;
        for (int k = vis.size() - 1; k >= 0; k--) {
            Vis v = vis.get(k);
            boolean hot = v == hover;
            int col = colorOf(v.info, v.pct);
            double mx = v.info.midX(), mz = v.info.midZ();
            double y = markerY(v.info, py);
            // shader markers: crisp glowing edges at a constant pixel width + frosted fill (core/world_marker.fsh)
            WorldGfx.glowSquare(mx - half, mz - half, mx + half, mz + half, y,
                    Look.withAlpha(col, hot ? 0xFF : 0xC0), hot ? 34 : 20, 1);
            WorldGfx.glowDot(mx, y, mz, Look.withAlpha(col, 0xFF));
            if (hot) {
                if (atBase.value && v.info.hw > 0) {                 // outline the real volume the clues sit in
                    v.info.box(bx, py);
                    WorldGfx.glowBox(bx[0], bx[1], bx[2], bx[3], bx[4], bx[5], Look.withAlpha(col, 0xFF), 10, 2);
                } else {
                    double x0 = v.info.cx * 16.0, z0 = v.info.cz * 16.0;
                    WorldGfx.glowSquare(x0, z0, x0 + 16, z0 + 16, y, Look.withAlpha(col, 0xFF), 12, 2);
                }
            }
        }
    }

    /** HUD pass: region map, live-player markers, tracer and the tooltip (all 2D). The boxes are in onWorld(). */
    @Override
    public void onHud(DrawContext g) {
        if (progress.value && mc.world != null) drawProgress(g);
        List<Info> list = display;
        if (list.isEmpty()) return;
        if (regionMap.value) drawMap(g, list);

        List<Vis> vis = curVis;
        Vis hover = curHover;
        if (vis.isEmpty()) return;
        double py = mc.player.getY();

        // flagged players keep an on-screen marker even when you are not looking at them
        TextRenderer tr = mc.textRenderer;
        for (Vis v : vis) {
            if (v.info.live == null) continue;
            Projector.projectOrPoint(v.info.live.x, v.info.live.y + 2.3, v.info.live.z, o);
            Look.tagClamped(g, tr, "LIVE " + v.info.live.name + "  " + (int) v.dist + "m", (int) o[0], (int) o[1] - 14, LIVE);
        }

        if (tracerBest.value) {
            Vis best = null;
            for (Vis v : vis) if (best == null || v.pct > best.pct) best = v;
            if (best != null) tracer(g, best.info.midX(), best.info.midY(py), best.info.midZ(), best.pct);
        }

        if (indicator.value) drawIndicator(g, tr, vis);

        // the explanation only shows while you aim at a chunk
        if (hover != null) {
            try { drawTooltip(g, tr, hover); }
            catch (RuntimeException ex) {
                if (!tooltipErrorShown) {
                    tooltipErrorShown = true;
                    mc.player.sendMessage(Text.literal("[Sus Chunk] hover info failed: " + ex), false);
                }
            }
        }
    }

    /** Top-left readout: how far this pass is, how many chunks were not loaded, how long the last pass took. */
    private void drawProgress(DrawContext g) {
        TextRenderer tr = mc.textRenderer;
        boolean scanning = queuePos < queue.size();
        double frac = queue.isEmpty() ? 1.0 : Math.min(1.0, queuePos / (double) queue.size());
        String s = "Sus scan " + (scanning ? queuePos + "/" + queue.size() : "idle")
                + (skipped > 0 ? "  " + skipped + " unloaded" : "")
                + (lastPassMs > 0 ? "  last " + String.format("%.1f", lastPassMs / 1000.0) + "s" : "");
        int col = lastPassMs > 8000 ? Look.BAD : lastPassMs > 4000 ? Look.WARN : Look.GOOD;   // slow passes = not keeping up
        int w = SdfText.w(s) + 16, x = 4, y = 18;
        Look.panel(g, x, y, x + w, y + 17, col);
        SdfText.ui(g, s, x + 8, y + 3, Look.TEXT, false);
        Look.bar(g, x + 8, y + 13, w - 16, 2, frac, col);
    }

    /** Bottom-right cards: a player head when a player is flagged, a chest when a base is detected. */
    private void drawIndicator(DrawContext g, TextRenderer tr, List<Vis> vis) {
        Vis player = null, base = null;
        for (Vis v : vis) {
            if (v.info.live != null) { if (player == null || v.dist < player.dist) player = v; }
            else if (v.pct >= baseAt.intValue() && (base == null || v.pct > base.pct)) base = v;
        }
        int rows = (player != null ? 1 : 0) + (base != null ? 1 : 0);
        if (rows == 0) return;
        int w = 130, h = 24;
        int x = Projector.width() - w - 8;
        int y = Projector.height() - 34 - rows * (h + 4);
        if (player != null) {
            int c = colorOf(player.info, player.pct);
            Look.panel(g, x, y, x + w, y + h, c);
            g.drawItem(new ItemStack(Items.PLAYER_HEAD), x + 4, y + 4);
            SdfText.ui(g, "Player", x + 26, y + 4, c, false);
            SdfText.ui(g, player.info.live.name + "  " + (int) player.dist + "m", x + 26, y + 13, Look.MUTED, false);
            y += h + 4;
        }
        if (base != null) {
            int c = colorOf(base.info, base.pct);
            Look.panel(g, x, y, x + w, y + h, c);
            g.drawItem(new ItemStack(Items.CHEST), x + 4, y + 4);
            SdfText.ui(g, "Base  " + base.pct + "%", x + 26, y + 4, c, false);
            SdfText.ui(g, compass(base.info.midX() - Projector.camPos.x, base.info.midZ() - Projector.camPos.z) + " " + (int) base.dist + "m", x + 26, y + 13, Look.MUTED, false);
        }
    }

    private boolean tooltipErrorShown;

    private void drawTooltip(DrawContext g, TextRenderer tr, Vis v) {
        Info i = v.info;
        int tier = colorOf(i, v.pct);
        String title = i.live != null ? "Live player: " + i.live.name : v.pct >= 75 ? "Very likely base" : v.pct >= 50 ? "Likely base" : "Possible base";
        String badge = v.pct + "%";
        double dx = i.midX() - Projector.camPos.x, dz = i.midZ() - Projector.camPos.z;
        String where = compass(dx, dz) + " " + (int) v.dist + "m";
        String yr = i.minY <= i.maxY ? "Y " + (int) Math.floor(i.minY) + ".." + (int) Math.floor(i.maxY) : "Y " + (int) i.midY(mc.player.getY());
        String conf = i.live != null || (v.pct >= 75 && i.signals >= 3) ? "HIGH" : (v.pct >= 50 || i.signals >= 2) ? "MEDIUM" : "LOW";
        int pts = 0;
        for (String[] r : i.rows) if (r[0].startsWith("+")) pts += Integer.parseInt(r[0].substring(1));
        String foot = pts + " points  confidence " + conf;

        int w = Math.max(SdfText.w(title) + SdfText.w(badge) + 32, SdfText.w(where) + SdfText.w(yr) + 24);
        w = Math.max(w, SdfText.w(foot) + 18);
        for (String[] r : i.rows) w = Math.max(w, SdfText.w(r[0]) + SdfText.w(r[1]) + 28);
        w = Math.max(w, 150);
        int rowsH = i.rows.size() * 11;
        int h = 40 + (rowsH > 0 ? rowsH + 6 : 0) + 15;

        // centred on the middle of the chunk marker; if that is off screen or behind you, centre on the crosshair instead
        double[] cp = new double[2];
        double markY = markerY(i, mc.player.getY());
        double scrW = Projector.width(), scrH = Projector.height();
        boolean onScreen = Projector.project(i.midX(), markY, i.midZ(), cp)
                && Double.isFinite(cp[0]) && Double.isFinite(cp[1])
                && cp[0] > 0 && cp[0] < scrW && cp[1] > 0 && cp[1] < scrH;
        if (!onScreen) { cp[0] = scrW / 2.0; cp[1] = scrH / 2.0 + h / 2.0 + 14; }
        int x = (int) cp[0] - w / 2, y = (int) cp[1] - h / 2;
        x = MathHelper.clamp(x, 4, Math.max(4, Projector.width() - w - 4));
        y = MathHelper.clamp(y, 4, Math.max(4, Projector.height() - h - 4));

        Look.panel(g, x, y, x + w, y + h, tier);
        int tx = x + 9;
        SdfText.ui(g, title, tx, y + 6, tier, false);
        SdfText.ui(g, badge, x + w - 7 - SdfText.w(badge), y + 6, tier, false);
        Look.bar(g, tx, y + 18, w - 16, 3, v.pct / 100.0, tier);
        SdfText.ui(g, where, tx, y + 25, Look.MUTED, false);
        SdfText.ui(g, yr, x + w - 7 - SdfText.w(yr), y + 25, Look.MUTED, false);

        int ry = y + 40;
        if (rowsH > 0) {
            g.fill(tx, y + 36, x + w - 7, y + 37, Look.EDGE);
            for (String[] r : i.rows) {
                SdfText.ui(g, r[0], tx, ry, r[0].startsWith("+") ? tier : Look.WARN, false);
                SdfText.ui(g, r[1], tx + SdfText.w(r[0]) + 7, ry, Look.TEXT, false);
                ry += 11;
            }
            g.fill(tx, ry + 1, x + w - 7, ry + 2, Look.EDGE);
            ry += 5;
        }
        SdfText.ui(g, foot, tx, ry, Look.MUTED, false);
    }

    private static int tierColor(int pct) {
        return pct >= 75 ? Look.BAD : pct >= 50 ? Look.ORANGE : pct >= 25 ? Look.WARN : 0xFF7C8594;
    }

    /** A line from the crosshair to the most suspicious chunk. */
    private void tracer(DrawContext g, double x, double y, double z, int pct) {
        double[] p = new double[2];
        Projector.projectOrPoint(x, y, z, p);
        Gfx.line(g, Projector.width() / 2.0, Projector.height() / 2.0, p[0], p[1], 1, Look.withAlpha(tierColor(pct), 0xB4));
    }

    /** Top-down map of every remembered suspicious chunk around you (north is up, white dot is you). */
    private void drawMap(DrawContext g, List<Info> list) {
        int r = mapRadius.intValue(), cell = mapCell.intValue(), size = (r * 2 + 1) * cell;
        int x0 = Projector.width() - size - 12, y0 = Math.max(30, (Projector.height() - size) / 2);
        Look.plate(g, x0 - 5, y0 - 5, x0 + size + 5, y0 + size + 5);
        Ui.corners(g, x0, y0, x0 + size, y0 + size, 3, 0x40000000, true, true, true, true);
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        for (Info i : list) {
            int dx = i.cx - pcx, dz = i.cz - pcz;
            if (Math.abs(dx) > r || Math.abs(dz) > r) continue;
            int pct = (int) Math.round(i.pct * 100);
            int px = x0 + (dx + r) * cell, py = y0 + (dz + r) * cell;
            Ui.corners(g, px, py, px + cell - 1, py + cell - 1, Math.min(2, (cell - 1) / 2), Look.withAlpha(colorOf(i, pct), 0xE6), true, true, true, true);
        }
        int me = r * cell;
        Ui.corners(g, x0 + me - 1, y0 + me - 1, x0 + me + cell, y0 + me + cell, (cell + 2) / 2, 0xFF000000, true, true, true, true);        // you: white dot, dark rim
        Ui.corners(g, x0 + me, y0 + me, x0 + me + cell - 1, y0 + me + cell - 1, Math.max(1, (cell - 1) / 2), 0xFFFFFFFF, true, true, true, true);
        SdfText.ui(g, "N", x0 + size / 2 - 2, y0 - 15, Look.TEXT, true);
    }
}
