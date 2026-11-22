package com.shipwright;

import com.shipwright.block.EngineBlockEntity;
import com.shipwright.block.ShipCoreBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities {
    public static final BlockEntityType<ShipCoreBlockEntity> SHIP_CORE =
            register("ship_core", ShipCoreBlockEntity::new, ModBlocks.SHIP_CORE);

    public static final BlockEntityType<EngineBlockEntity> ENGINE =
            register("engine", EngineBlockEntity::new, ModBlocks.ENGINE);

    private ModBlockEntities() {}

    private static <T extends BlockEntity> BlockEntityType<T> register(
            String name, BlockEntityType.BlockEntitySupplier<? extends T> factory, Block block) {
        BlockEntityType<T> type = new BlockEntityType<>(factory, Set.of(block));
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Shipwright.id(name), type);
    }

    public static void init() {}
}
