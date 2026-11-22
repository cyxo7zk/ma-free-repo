package com.shipwright.block;

import com.shipwright.ModBlockEntities;
import com.shipwright.ship.PowerNetwork;
import com.shipwright.ship.ShipMover;
import com.shipwright.ship.ShipScanner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The ship's brain. Holds the throttle, reads the helmsman's keys, powers the propeller animation
 * and moves the ship. All state is saved so it survives being block-shifted.
 */
public class ShipCoreBlockEntity extends BlockEntity {
    // ---- tuning ----------------------------------------------------------------------------
    /** Throttle values with |t| below this give zero speed (the "stop zone"). */
    public static final float DEADZONE = 0.20f;
    /** Throttle change per tick while W/S is held (about 4 s from 0 to full). */
    private static final float THROTTLE_STEP = 0.0125f;
    /** Ticks the throttle sticks at exactly 0 when you slow down through it. */
    private static final int ZERO_HOLD_TICKS = 14;
    /** Ticks between block steps at the slowest / fastest speed. */
    private static final int MAX_INTERVAL = 22;
    private static final int MIN_INTERVAL = 4;
    private static final int TURN_COOLDOWN = 30;
    /** One powered propeller per this many hull blocks gives full speed. */
    private static final float BLOCKS_PER_PROP = 40f;
    /** Propeller frames per tick when idling / at full throttle. */
    private static final float SPIN_IDLE = 0.14f;
    private static final float SPIN_FULL = 1.0f;
    /** Seat armour-stand height relative to the wheel block's floor. Tweak if you sit too high/low. */
    private static final double SEAT_Y_OFFSET = -1.375;
    // ---------------------------------------------------------------------------------------

    private float throttle;
    private int zeroHold;
    private int moveProgress;
    private int turnCooldown;
    private UUID seatId;

    private int tickCount;
    private float spinPhase;
    private long lastWarnTick = -100;
    private ShipScanner.Ship ship;
    private PowerNetwork.Result power = PowerNetwork.Result.NONE;

    public ShipCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIP_CORE, pos, state);
    }

    // ---------------------------------------------------------------------------------------
    // Helm
    // ---------------------------------------------------------------------------------------

    public void takeWheel(ServerLevel level, ServerPlayer player, BlockPos wheelPos) {
        if (player.isPassenger()) return;
        discardSeat(level);

        ArmorStand seat = new ArmorStand(level, wheelPos.getX() + 0.5, wheelPos.getY() + SEAT_Y_OFFSET, wheelPos.getZ() + 0.5);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setSilent(true);
        seat.addTag(ShipMover.SEAT_TAG);
        level.addFreshEntity(seat);

        if (!player.startRiding(seat)) {
            seat.discard();
            player.sendOverlayMessage(Component.literal("Stand up (not sneaking) and use the wheel again."));
            return;
        }
        seatId = seat.getUUID();
        throttle = 0f;
        zeroHold = 0;
        moveProgress = 0;
        player.sendOverlayMessage(Component.literal("At the helm: W/S throttle, A/D turn, Space stop, Shift leave."));
    }

    private void discardSeat(ServerLevel level) {
        if (seatId != null) {
            Entity old = level.getEntity(seatId);
            if (old != null) old.discard();
            seatId = null;
        }
    }

    private ServerPlayer helmsman(ServerLevel level) {
        if (seatId == null) return null;
        Entity seat = level.getEntity(seatId);
        if (seat == null || seat.isRemoved()) {
            seatId = null;
            return null;
        }
        if (seat.getFirstPassenger() instanceof ServerPlayer sp) return sp;
        seat.discard();
        seatId = null;
        return null;
    }

    private Direction heading(ServerLevel level) {
        Entity seat = seatId == null ? null : level.getEntity(seatId);
        if (seat == null) return null;
        BlockPos wheel = BlockPos.containing(seat.getX(), seat.getY() - SEAT_Y_OFFSET + 0.2, seat.getZ());
        BlockState s = level.getBlockState(wheel);
        return s.getBlock() instanceof ShipWheelBlock ? s.getValue(ShipWheelBlock.FACING) : null;
    }

    // ---------------------------------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------------------------------

    public void serverTick(ServerLevel level) {
        tickCount++;
        if (ship == null || tickCount % 20 == 0) {
            ship = ShipScanner.scan(level, worldPosition);
            power = ship.truncated ? PowerNetwork.Result.NONE : PowerNetwork.evaluate(level, ship);
        }

        ServerPlayer helm = helmsman(level);
        if (turnCooldown > 0) turnCooldown--;

        if (helm == null) {
            throttle = 0f;
            zeroHold = 0;
            moveProgress = 0;
        } else {
            Entity seat = level.getEntity(seatId);
            if (seat != null) seat.setDeltaMovement(Vec3.ZERO);

            Input in = helm.getLastClientInput();
            updateThrottle(in);

            if (tickCount % 4 == 0) helm.sendOverlayMessage(hud());

            // Turning
            if (turnCooldown == 0 && (in.left() != in.right())) {
                if (canMove(level, helm)) {
                    turnCooldown = TURN_COOLDOWN;
                    if (report(level, helm, ShipMover.rotate(level, ship, in.right()))) return;
                }
            }

            // Driving
            float eff = effective(throttle);
            if (eff == 0f) {
                moveProgress = 0;
            } else if (canMove(level, helm)) {
                float thrust = thrust();
                float base = MIN_INTERVAL + (MAX_INTERVAL - MIN_INTERVAL) * (1f - Math.abs(eff));
                int interval = Math.max(MIN_INTERVAL, Math.round(base / thrust));
                if (++moveProgress >= interval) {
                    moveProgress = 0;
                    Direction bow = heading(level);
                    if (bow != null) {
                        Direction dir = eff > 0 ? bow : bow.getOpposite();
                        if (report(level, helm, ShipMover.translate(level, ship, dir))) return;
                    }
                }
            }
        }
        animatePropellers(level);
    }

    /** Returns true when the ship moved (this block entity was replaced and must stop ticking). */
    private boolean report(ServerLevel level, ServerPlayer helm, ShipMover.Result result) {
        if (result.ok()) return true;
        warn(helm, result.reason());
        return false;
    }

    private boolean canMove(ServerLevel level, ServerPlayer helm) {
        if (ship.truncated) { warn(helm, "The ship is too big or attached to terrain."); return false; }
        if (ship.anchorsDown > 0) { warn(helm, "Raise the anchor first!"); return false; }
        if (power.poweredCount() == 0) { warn(helm, "No propeller is receiving power."); return false; }
        if (!ship.afloat()) { warn(helm, "The ship is not in the water."); return false; }
        return true;
    }

    private void warn(ServerPlayer helm, String text) {
        if (tickCount - lastWarnTick < 30) return;
        lastWarnTick = tickCount;
        helm.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    // ---------------------------------------------------------------------------------------
    // Throttle
    // ---------------------------------------------------------------------------------------

    private void updateThrottle(Input in) {
        if (in.jump()) {
            throttle = 0f;
            zeroHold = ZERO_HOLD_TICKS;
            return;
        }
        if (zeroHold > 0) {
            zeroHold--;
            return;
        }
        int dir = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        if (dir == 0) return;

        float prev = throttle;
        float next = prev + dir * THROTTLE_STEP;
        if (prev == 0f) {
            // Leaving the zero detent: jump to the edge of the stop zone, speed still 0 there.
            next = dir * DEADZONE;
        } else if (Math.abs(prev) >= DEADZONE && Math.abs(next) < DEADZONE && Math.signum(next) == Math.signum(prev)) {
            // Slowing down into the stop zone: snap to 0 and hold, so stopping is easy.
            next = 0f;
            zeroHold = ZERO_HOLD_TICKS;
        }
        throttle = Math.max(-1f, Math.min(1f, next));
    }

    /** Throttle -> speed in [-1, 1]. Everything inside the stop zone is 0. */
    private static float effective(float t) {
        float a = Math.abs(t);
        if (a < DEADZONE) return 0f;
        return Math.signum(t) * (a - DEADZONE) / (1f - DEADZONE);
    }

    private float thrust() {
        if (power.poweredCount() == 0) return 0f;
        float needed = Math.max(1f, ship.hull.size() / BLOCKS_PER_PROP);
        return Math.max(0.25f, Math.min(1f, power.poweredCount() / needed));
    }

    // ---------------------------------------------------------------------------------------
    // Propeller animation
    // ---------------------------------------------------------------------------------------

    private void animatePropellers(ServerLevel level) {
        if (power.poweredCount() == 0) return;
        float eff = Math.abs(effective(throttle));
        float rate = SPIN_IDLE + (SPIN_FULL - SPIN_IDLE) * eff;
        float dir = throttle < 0 ? -1f : 1f;

        int before = frameOf(spinPhase);
        spinPhase = ((spinPhase + dir * rate) % 4f + 4f) % 4f;
        int after = frameOf(spinPhase);
        if (before == after) return;

        for (BlockPos p : power.poweredProps()) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof PropellerBlock && s.getValue(PropellerBlock.FRAME) != after) {
                level.setBlock(p, s.setValue(PropellerBlock.FRAME, after), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static int frameOf(float phase) {
        return Math.floorMod((int) Math.floor(phase), 4);
    }

    // ---------------------------------------------------------------------------------------
    // HUD / report
    // ---------------------------------------------------------------------------------------

    private Component hud() {
        MutableComponent c = Component.literal("");
        for (int i = 0; i <= 20; i++) {
            float v = (i - 10) / 10f;
            boolean center = Math.abs(v) < DEADZONE;
            boolean filled = throttle >= 0 ? (v > 0 && v <= throttle) : (v < 0 && v >= throttle);
            ChatFormatting color = center ? ChatFormatting.WHITE
                    : filled ? (throttle > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)
                    : ChatFormatting.DARK_GRAY;
            c.append(Component.literal(center ? "|" : "\u2502").withStyle(color));
        }
        float eff = effective(throttle);
        String label = eff == 0f ? "STOP" : (eff > 0 ? "AHEAD " : "ASTERN ") + Math.round(Math.abs(eff) * 100) + "%";
        c.append(Component.literal("  " + label).withStyle(ChatFormatting.YELLOW));
        c.append(Component.literal("   Power " + power.poweredCount() + "/" + power.props()).withStyle(ChatFormatting.GRAY));
        if (ship != null && ship.anchorsDown > 0) {
            c.append(Component.literal("   ANCHOR DOWN").withStyle(ChatFormatting.RED));
        }
        return c;
    }

    public void report(ServerLevel level, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        ship = ShipScanner.scan(level, worldPosition);
        power = ship.truncated ? PowerNetwork.Result.NONE : PowerNetwork.evaluate(level, ship);
        String text = ship.truncated
                ? "Too many connected blocks (limit " + ShipScanner.MAX_BLOCKS + ") - is the ship touching terrain?"
                : "Ship: " + ship.hull.size() + " blocks | Engines: " + power.burning() + "/" + power.engines() + " running"
                + " | Propellers: " + power.poweredCount() + "/" + power.props() + " powered"
                + " | Anchors: " + ship.anchorsDown + "/" + ship.anchors + " down"
                + " | " + (ship.afloat() ? "Afloat" : "Not in water");
        player.sendSystemMessage(Component.literal(text));
    }

    // ---------------------------------------------------------------------------------------
    // Persistence (this is what lets the core survive being block-shifted)
    // ---------------------------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        throttle = input.getIntOr("Throttle", 0) / 1000f;
        zeroHold = input.getIntOr("ZeroHold", 0);
        moveProgress = input.getIntOr("MoveProgress", 0);
        turnCooldown = input.getIntOr("TurnCooldown", 0);
        String id = input.getStringOr("Seat", "");
        try {
            seatId = id.isEmpty() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            seatId = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Throttle", Math.round(throttle * 1000f));
        output.putInt("ZeroHold", zeroHold);
        output.putInt("MoveProgress", moveProgress);
        output.putInt("TurnCooldown", turnCooldown);
        output.putString("Seat", seatId == null ? "" : seatId.toString());
    }
}
