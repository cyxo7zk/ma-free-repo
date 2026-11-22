package com.shipwright;

import com.shipwright.block.AnchorBlock;
import com.shipwright.block.EngineBlock;
import com.shipwright.block.PropellerBlock;
import com.shipwright.block.ShaftBlock;
import com.shipwright.block.ShipCoreBlock;
import com.shipwright.block.ShipWheelBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModBlocks {
    /** Every block item we register, used for the creative tab. */
    public static final List<BlockItem> ITEMS = new ArrayList<>();

    public static final Block SHIP_CORE = register("ship_core", ShipCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(5.0f, 12.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops());

    public static final Block SHIP_WHEEL = register("ship_wheel", ShipWheelBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2.5f).sound(SoundType.WOOD).noOcclusion());

    public static final Block ENGINE = register("engine", EngineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 9.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(EngineBlock.LIT) ? 13 : 0));

    public static final Block SHAFT = register("shaft", ShaftBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());

    public static final Block PROPELLER = register("propeller", PropellerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(2.5f, 6.0f).sound(SoundType.COPPER).noOcclusion());

    public static final Block ANCHOR = register("anchor", AnchorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(4.0f, 9.0f).sound(SoundType.ANVIL).noOcclusion());

    private ModBlocks() {}

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties props) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Shipwright.id(name));
        T block = factory.apply(props.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Shipwright.id(name));
        BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        ITEMS.add(item);
        return block;
    }

    /** Touching this class registers everything. */
    public static void init() {}
}
