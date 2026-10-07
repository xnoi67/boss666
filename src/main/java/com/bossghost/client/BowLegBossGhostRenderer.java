package com.bossghost.client;

import com.bossghost.BossGhostMod;
import com.bossghost.entity.BowLegBossGhost;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BowLegBossGhostRenderer extends MobRenderer<BowLegBossGhost, BowLegBossGhostModel> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(BossGhostMod.MODID, "bow_leg_boss"), "main");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BossGhostMod.MODID, "textures/entity/bow_leg_boss.png");

    public BowLegBossGhostRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BowLegBossGhostModel(ctx.bakeLayer(LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(BowLegBossGhost entity) {
        return TEXTURE;
    }
}
