package com.shipwright.ship;

import com.shipwright.Shipwright;
import com.shipwright.block.AnchorBlock;
import com.shipwright.block.ShipCoreBlock;
import com.shipwright.block.ShipCoreBlockEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Finds which blocks belong to a ship: everything face-connected to the core, except terrain/water. */
public final class ShipScanner {
    public static final int MAX_BLOCKS = 6000;
    public static final long MAX_INTERIOR_VOLUME = 300_000L;
    public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK, Shipwright.id("ship_excluded"));

    private ShipScanner() {}

    public static final class Ship {
        public final BlockPos core;
        public final List<BlockPos> hull = new ArrayList<>();
        public final LongOpenHashSet hullSet = new LongOpenHashSet();
        /** Enclosed (or open-topped) air cells that travel with the hull so the inside stays dry. */
        public final List<BlockPos> interior = new ArrayList<>();
        public final LongOpenHashSet interiorSet = new LongOpenHashSet();
        public boolean truncated;
        public int anchors;
        public int anchorsDown;
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        public int waterY = Integer.MIN_VALUE;

        Ship(BlockPos core) {
            this.core = core;
        }

        /** The hull touches water. */
        public boolean afloat() {
            return waterY != Integer.MIN_VALUE && waterY >= minY;
        }
    }

    public static boolean isShipBlock(BlockState s) {
        return !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && !s.is(EXCLUDED);
    }

    public static Ship scan(ServerLevel level, BlockPos core) {
        Ship ship = new Ship(core);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ship.hullSet.add(core.asLong());
        queue.add(core);

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            ship.hull.add(p);
            ship.minX = Math.min(ship.minX, p.getX());
            ship.minY = Math.min(ship.minY, p.getY());
            ship.minZ = Math.min(ship.minZ, p.getZ());
            ship.maxX = Math.max(ship.maxX, p.getX());
            ship.maxY = Math.max(ship.maxY, p.getY());
            ship.maxZ = Math.max(ship.maxZ, p.getZ());

            BlockState here = level.getBlockState(p);
            if (here.getBlock() instanceof AnchorBlock) {
                ship.anchors++;
                if (here.getValue(AnchorBlock.DROPPED)) ship.anchorsDown++;
            }
            if (ship.hull.size() > MAX_BLOCKS) {
                ship.truncated = true;
                return ship;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                long key = n.asLong();
                if (ship.hullSet.contains(key) || !level.hasChunkAt(n)) continue;
                if (!isShipBlock(level.getBlockState(n))) continue;
                ship.hullSet.add(key);
                queue.add(n);
            }
        }
        computeInterior(ship);
        findWater(level, ship);
        return ship;
    }

    private static void computeInterior(Ship s) {
        long vol = (long) (s.maxX - s.minX + 3) * (s.maxY - s.minY + 2) * (s.maxZ - s.minZ + 3);
        if (vol > MAX_INTERIOR_VOLUME) return;

        LongOpenHashSet outside = new LongOpenHashSet();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        // Flood "outside" from the sides and bottom of the bounding box. The top layer is NOT a seed,
        // so an open-topped hull keeps its air (and stays dry) when it moves.
        for (int x = s.minX - 1; x <= s.maxX + 1; x++) {
            for (int y = s.minY - 1; y <= s.maxY; y++) {
                for (int z = s.minZ - 1; z <= s.maxZ + 1; z++) {
                    boolean edge = x == s.minX - 1 || x == s.maxX + 1 || z == s.minZ - 1 || z == s.maxZ + 1 || y == s.minY - 1;
                    if (!edge) continue;
                    BlockPos p = new BlockPos(x, y, z);
                    if (s.hullSet.contains(p.asLong()) || !outside.add(p.asLong())) continue;
                    q.add(p);
                }
            }
        }
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < s.minX - 1 || n.getX() > s.maxX + 1 || n.getZ() < s.minZ - 1 || n.getZ() > s.maxZ + 1
                        || n.getY() < s.minY - 1 || n.getY() > s.maxY) continue;
                long k = n.asLong();
                if (s.hullSet.contains(k) || !outside.add(k)) continue;
                q.add(n);
            }
        }
        for (int x = s.minX; x <= s.maxX; x++) {
            for (int y = s.minY; y <= s.maxY; y++) {
                for (int z = s.minZ; z <= s.maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    long k = p.asLong();
                    if (!s.hullSet.contains(k) && !outside.contains(k)) {
                        s.interior.add(p);
                        s.interiorSet.add(k);
                    }
                }
            }
        }
    }

    /** Finds the water surface next to the hull; cells left behind below it are refilled with water. */
    private static void findWater(ServerLevel level, Ship s) {
        int best = Integer.MIN_VALUE;
        for (int x = s.minX - 1; x <= s.maxX + 1; x++) {
            best = Math.max(best, topWater(level, s, x, s.minZ - 1));
            best = Math.max(best, topWater(level, s, x, s.maxZ + 1));
        }
        for (int z = s.minZ - 1; z <= s.maxZ + 1; z++) {
            best = Math.max(best, topWater(level, s, s.minX - 1, z));
            best = Math.max(best, topWater(level, s, s.maxX + 1, z));
        }
        s.waterY = best;
    }

    private static int topWater(ServerLevel level, Ship s, int x, int z) {
        for (int y = s.maxY; y >= s.minY - 1; y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!level.hasChunkAt(p)) return Integer.MIN_VALUE;
            if (level.getFluidState(p).is(FluidTags.WATER)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Walks outward from any ship block (e.g. the wheel) and returns the nearest Ship Core. */
    public static ShipCoreBlockEntity findCore(ServerLevel level, BlockPos start) {
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start.asLong());
        int count = 0;
        while (!q.isEmpty() && count++ < MAX_BLOCKS) {
            BlockPos p = q.poll();
            if (level.getBlockState(p).getBlock() instanceof ShipCoreBlock
                    && level.getBlockEntity(p) instanceof ShipCoreBlockEntity core) {
                return core;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong()) || !level.hasChunkAt(n)) continue;
                if (isShipBlock(level.getBlockState(n))) q.add(n);
            }
        }
        return null;
    }
}
