package com.shipwright.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Block-shift movement: the ship's real blocks (and their block entities) are lifted and set down
 * one step away. Containers keep their items, furnaces keep burning, everything stays interactive.
 */
public final class ShipMover {
    /** Update clients, skip neighbour shape updates so torches/doors/etc. do not pop off mid-move. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final String SEAT_TAG = "shipwright_seat";

    private ShipMover() {}

    public record Result(boolean ok, String reason) {
        static final Result OK = new Result(true, "");
        static Result fail(String why) { return new Result(false, why); }
    }

    private record Entry(BlockPos from, BlockPos to, BlockState state, CompoundTag tag) {}

    public static Result translate(ServerLevel level, ShipScanner.Ship ship, Direction dir) {
        int dx = dir.getStepX(), dz = dir.getStepZ();
        return apply(level, ship, p -> p.offset(dx, 0, dz), v -> v.add(dx, 0, dz), Rotation.NONE, 0f);
    }

    public static Result rotate(ServerLevel level, ShipScanner.Ship ship, boolean clockwise) {
        Rotation rot = clockwise ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        BlockPos c = ship.core;
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
        return apply(level, ship,
                p -> c.offset(p.subtract(c).rotate(rot)),
                v -> {
                    double rx = v.x - cx, rz = v.z - cz;
                    double nx = clockwise ? -rz : rz;
                    double nz = clockwise ? rx : -rx;
                    return new Vec3(cx + nx, v.y, cz + nz);
                },
                rot, clockwise ? 90f : -90f);
    }

    private static Result apply(ServerLevel level, ShipScanner.Ship ship, UnaryOperator<BlockPos> posMap,
                                UnaryOperator<Vec3> vecMap, Rotation rot, float yawDelta) {
        if (ship.truncated) return Result.fail("The ship is too big or attached to terrain.");
        RegistryAccess registries = level.registryAccess();

        // 1. Capture
        List<Entry> entries = new ArrayList<>(ship.hull.size());
        for (BlockPos p : ship.hull) {
            BlockEntity be = level.getBlockEntity(p);
            CompoundTag tag = be != null ? be.saveCustomOnly(registries) : null;
            entries.add(new Entry(p, posMap.apply(p), level.getBlockState(p).rotate(rot), tag));
        }

        // 2. Validate every destination cell
        for (Entry e : entries) {
            if (!canOccupy(level, ship, e.to)) return Result.fail("Something is in the way.");
        }
        for (BlockPos ip : ship.interior) {
            if (!canOccupy(level, ship, posMap.apply(ip))) return Result.fail("Something is in the way.");
        }

        // 3. Entities riding along (before anything moves)
        AABB box = new AABB(ship.minX - 1, ship.minY - 1, ship.minZ - 1, ship.maxX + 2, ship.maxY + 3, ship.maxZ + 2);
        List<Entity> riders = level.getEntitiesOfClass(Entity.class, box, ent -> onShip(ent, ship));

        // 4. Lift the hull. Containers are emptied first so nothing drops.
        for (Entry e : entries) {
            if (level.getBlockEntity(e.from) instanceof Clearable c) c.clearContent();
        }
        for (Entry e : entries) level.setBlock(e.from, Blocks.AIR.defaultBlockState(), FLAGS);

        // 5. Set it down
        for (Entry e : entries) level.setBlock(e.to, e.state, FLAGS);
        for (Entry e : entries) {
            if (e.tag == null) continue;
            BlockEntity be = level.getBlockEntity(e.to);
            if (be != null) {
                be.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, e.tag));
                be.setChanged();
            }
        }

        // 6. Carry the dry interior, then refill what the ship left behind
        it.unimi.dsi.fastutil.longs.LongOpenHashSet newAll = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (Entry e : entries) newAll.add(e.to.asLong());
        for (BlockPos ip : ship.interior) {
            BlockPos np = posMap.apply(ip);
            newAll.add(np.asLong());
            if (!level.getBlockState(np).isAir()) level.setBlock(np, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        List<BlockPos> oldAll = new ArrayList<>(ship.hull);
        oldAll.addAll(ship.interior);
        for (BlockPos p : oldAll) {
            if (newAll.contains(p.asLong())) continue;
            if (p.getY() <= ship.waterY) {
                level.setBlock(p, Blocks.WATER.defaultBlockState(), FLAGS);
            } else if (!level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }

        // 7. Move entities
        for (Entity ent : riders) {
            Vec3 np = vecMap.apply(ent.position());
            float yaw = ent.getYRot() + yawDelta;
            if (ent instanceof ServerPlayer sp) {
                if (sp.isPassenger()) continue; // moves together with its seat
                sp.connection.teleport(np.x, np.y, np.z, yaw, sp.getXRot());
            } else {
                ent.setPos(np.x, np.y, np.z);
                if (yawDelta != 0f) ent.setYRot(yaw);
            }
        }
        return Result.OK;
    }

    private static boolean canOccupy(ServerLevel level, ShipScanner.Ship ship, BlockPos p) {
        long k = p.asLong();
        if (ship.hullSet.contains(k) || ship.interiorSet.contains(k)) return true;
        if (!level.isInWorldBounds(p) || !level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)) return false;
        BlockState s = level.getBlockState(p);
        if (s.getFluidState().is(FluidTags.LAVA)) return false;
        return s.isAir() || (s.getCollisionShape(level, p).isEmpty() && !s.hasBlockEntity());
    }

    private static boolean onShip(Entity e, ShipScanner.Ship ship) {
        if (e.isSpectator()) return false;
        if (e.getTags().contains(SEAT_TAG)) return true;
        BlockPos bp = e.blockPosition();
        long k = bp.asLong();
        return ship.hullSet.contains(k) || ship.interiorSet.contains(k) || ship.hullSet.contains(bp.below().asLong());
    }
}
