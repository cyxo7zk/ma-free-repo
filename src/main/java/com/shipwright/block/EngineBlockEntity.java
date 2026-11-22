package com.shipwright.block;

import com.shipwright.ModBlockEntities;
import com.shipwright.ship.FuelTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class EngineBlockEntity extends BaseContainerBlockEntity {
    public static final int SIZE = 5;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int burnTime;

    public EngineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENGINE, pos, state);
    }

    /** True while fuel is burning and no redstone signal is switching the engine off. */
    public boolean isRunning() {
        return burnTime > 0 && !(level != null && level.hasNeighborSignal(worldPosition));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean paused = level.hasNeighborSignal(pos);
        if (!paused) {
            if (burnTime > 0) burnTime--;
            if (burnTime <= 0) {
                for (int i = 0; i < items.size(); i++) {
                    ItemStack stack = items.get(i);
                    int fuel = FuelTable.burnTime(stack);
                    if (fuel > 0) {
                        burnTime = fuel;
                        if (stack.is(Items.LAVA_BUCKET)) {
                            items.set(i, new ItemStack(Items.BUCKET));
                        } else {
                            stack.shrink(1);
                        }
                        setChanged();
                        break;
                    }
                }
            }
            if (burnTime > 0 && level.getGameTime() % 40 == 0) setChanged();
        }
        boolean lit = burnTime > 0 && !paused;
        if (state.getValue(EngineBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(EngineBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.shipwright.engine");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        // Re-uses the vanilla 5-slot hopper screen, so no custom client code is needed.
        return new HopperMenu(containerId, inventory, this);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return FuelTable.burnTime(stack) > 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burnTime = input.getIntOr("BurnTime", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("BurnTime", burnTime);
    }
}
