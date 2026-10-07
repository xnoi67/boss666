package com.bossghost;

import com.bossghost.entity.BowLegBossGhost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BossGhostMod.MODID);

    public static final RegistryObject<EntityType<BowLegBossGhost>> BOSS_GHOST = ENTITIES.register("bow_leg_boss",
            () -> EntityType.Builder.of(BowLegBossGhost::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(new ResourceLocation(BossGhostMod.MODID, "bow_leg_boss").toString()));
}
