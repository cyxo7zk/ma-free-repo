package com.shipwright.ship;

import com.shipwright.block.EngineBlock;
import com.shipwright.block.EngineBlockEntity;
import com.shipwright.block.PropellerBlock;
import com.shipwright.block.ShaftBlock;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Power graph. Engines feed shafts and propellers on any side. Shafts and propellers conduct along
 * their axis, and a shaft can reach THROUGH up to WALL_REACH-1 solid hull blocks (a wall), so a
 * propeller can sit behind blocks and still receive torque.
 */
public final class PowerNetwork {
    public static final int WALL_REACH = 3;
    public static final int PROPS_PER_ENGINE = 4;

    private PowerNetwork() {}

    public record Result(int engines, int burning, int props, List<BlockPos> poweredProps) {
        public static final Result NONE = new Result(0, 0, 0, List.of());
        public int poweredCount() { return poweredProps.size(); }
    }

    private enum Kind { ENGINE, SHAFT, PROP }

    public static Result evaluate(ServerLevel level, ShipScanner.Ship ship) {
        List<BlockPos> pos = new ArrayList<>();
        List<Kind> kind = new ArrayList<>();
        List<Direction.Axis> axis = new ArrayList<>();
        Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        index.defaultReturnValue(-1);

        for (BlockPos p : ship.hull) {
            BlockState s = level.getBlockState(p);
            Kind k = null;
            Direction.Axis a = null;
            if (s.getBlock() instanceof EngineBlock) {
                k = Kind.ENGINE;
            } else if (s.getBlock() instanceof ShaftBlock) {
                k = Kind.SHAFT;
                a = s.getValue(ShaftBlock.AXIS);
            } else if (s.getBlock() instanceof PropellerBlock) {
                k = Kind.PROP;
                a = s.getValue(PropellerBlock.FACING).getAxis();
            }
            if (k == null) continue;
            index.put(p.asLong(), pos.size());
            pos.add(p);
            kind.add(k);
            axis.add(a);
        }
        int n = pos.size();
        if (n == 0) return Result.NONE;

        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            Kind ki = kind.get(i);
            for (Direction d : Direction.values()) {
                if (ki != Kind.ENGINE && d.getAxis() != axis.get(i)) continue;
                for (int step = 1; step <= WALL_REACH; step++) {
                    BlockPos q = pos.get(i).relative(d, step);
                    int j = index.get(q.asLong());
                    if (j >= 0) {
                        if (kind.get(j) == Kind.ENGINE || axis.get(j) == d.getAxis()) union(parent, i, j);
                        break; // a node always ends the scan (connected or blocked)
                    }
                    // Not a node: only continue through a solid ship block (a wall).
                    if (!ship.hullSet.contains(q.asLong()) || level.getBlockState(q).isAir()) break;
                }
            }
        }

        int engines = 0, burning = 0, props = 0;
        int[] burningIn = new int[n];
        List<List<Integer>> propsIn = new ArrayList<>();
        for (int i = 0; i < n; i++) propsIn.add(null);

        for (int i = 0; i < n; i++) {
            int r = find(parent, i);
            switch (kind.get(i)) {
                case ENGINE -> {
                    engines++;
                    if (level.getBlockEntity(pos.get(i)) instanceof EngineBlockEntity e && e.isRunning()) {
                        burning++;
                        burningIn[r]++;
                    }
                }
                case PROP -> {
                    props++;
                    if (propsIn.get(r) == null) propsIn.set(r, new ArrayList<>());
                    propsIn.get(r).add(i);
                }
                default -> {}
            }
        }

        List<BlockPos> powered = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            List<Integer> list = propsIn.get(r);
            if (list == null) continue;
            int capacity = burningIn[r] * PROPS_PER_ENGINE;
            for (int k = 0; k < list.size() && k < capacity; k++) powered.add(pos.get(list.get(k)));
        }
        return new Result(engines, burning, props, powered);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }
}
