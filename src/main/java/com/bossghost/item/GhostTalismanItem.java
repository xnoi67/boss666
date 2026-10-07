package com.bossghost.item;

import com.bossghost.ModEntities;
import com.bossghost.entity.BowLegBossGhost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** ยันต์เสกผีพี่บอส: คลิกขวาที่พื้นเพื่อเสกผี (ใช้ได้ทุกเวลา เสกตอนเช้าผีจะเดินปกติ ไม่ดุ) */
public class GhostTalismanItem extends Item {
    public GhostTalismanItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        BowLegBossGhost ghost = ModEntities.BOSS_GHOST.get().create(server);
        if (ghost == null) return InteractionResult.FAIL;

        ghost.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        ghost.setSummoned(true);
        ghost.setPersistenceRequired();
        server.addFreshEntity(ghost);

        server.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                30, 0.3, 0.8, 0.3, 0.03);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.8F);

        Player player = ctx.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
