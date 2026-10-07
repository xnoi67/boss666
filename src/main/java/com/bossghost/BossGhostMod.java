package com.bossghost;

import com.bossghost.entity.BowLegBossGhost;
import com.bossghost.net.ModNetwork;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BossGhostMod.MODID)
public class BossGhostMod {
    public static final String MODID = "bossghost";

    public BossGhostMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::registerSpawns);
        bus.addListener(this::addCreative);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(ModNetwork::register);
    }

    private void registerAttributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.BOSS_GHOST.get(), BowLegBossGhost.createAttributes().build());
    }

    private void registerSpawns(SpawnPlacementRegisterEvent e) {
        e.register(ModEntities.BOSS_GHOST.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.OR);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(ModItems.GHOST_SPAWN_EGG);
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(ModItems.GHOST_TALISMAN);
    }
}
