package com.bossghost.client;

import com.bossghost.entity.BowLegBossGhost;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

public class BowLegBossGhostModel extends HumanoidModel<BowLegBossGhost> {
    public BowLegBossGhostModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32);
    }

    @Override
    public void setupAnim(BowLegBossGhost entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, entity.isAggressive(), this.attackTime, ageInTicks);

        // ขาโก่ง: ขาแบะออกด้านข้าง
        this.rightLeg.zRot = 0.22F;
        this.leftLeg.zRot = -0.22F;

        // เดินส่ายตัว
        float sway = Mth.cos(limbSwing * 0.6662F) * 0.15F * limbSwingAmount;
        this.body.zRot = sway;
    }
}
