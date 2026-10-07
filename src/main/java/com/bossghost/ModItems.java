package com.bossghost;

import com.bossghost.item.GhostTalismanItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BossGhostMod.MODID);

    public static final RegistryObject<Item> GHOST_TALISMAN = ITEMS.register("ghost_talisman",
            () -> new GhostTalismanItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> GHOST_SPAWN_EGG = ITEMS.register("bow_leg_boss_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.BOSS_GHOST, 0xF2F2F2, 0x8B0000, new Item.Properties()));
}
