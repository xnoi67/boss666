package com.bossghost;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.bossghost.entity.BowLegBossGhost;
import com.bossghost.net.ClientEffectPacket;
import com.bossghost.net.ModNetwork;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * ระบบสยองขวัญทั้งหมดฝั่งเซิร์ฟเวอร์:
 *  - ต้อนรับเมื่อเข้าโลก
 *  - สุ่มตอนกลางวัน: jumpscare / ผียืนบนเขา / ผีแอบหลังต้นไม้
 *  - กลางคืน (บางคืน): ผีมาเคาะประตู-หน้าต่างบ้าน แล้วบุกเข้ามา
 */
@Mod.EventBusSubscriber(modid = BossGhostMod.MODID)
public class HorrorEvents {

    // ---------- สถานะ ----------
    private static final Map<UUID, State> STATES = new HashMap<>();

    static final class State {
        int welcomeTimer = -1;
        long nextScare;
        long doneDay = -1;
        int insideTicks = 0;
        Visit visit;
    }

    static final int ST_DOOR = 0, ST_WINDOW = 1, ST_CHALLENGE = 2, ST_GRAB = 3, ST_FLEE = 4;

    static final class Visit {
        int stage;
        int timer;
        BowLegBossGhost ghost;
        Room room;
        BlockPos doorOut;
        BlockPos winPos;
        BlockPos winStand;
        boolean repelled;
        Vec3 hold;
        Vec3 fleeDir;
    }

    static final class Room {
        final Set<BlockPos> cells = new HashSet<>();
        BlockPos door;
        final List<BlockPos> windows = new ArrayList<>();
        BlockPos bed;
        boolean enclosed;
    }

    private static State stateOf(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> {
            State s = new State();
            s.nextScare = p.serverLevel().getGameTime() + 3600 + p.getRandom().nextInt(3600);
            return s;
        });
    }

    // ---------- เหตุการณ์ผู้เล่น ----------
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State st = new State();
        st.welcomeTimer = 100;
        st.nextScare = p.serverLevel().getGameTime() + 3600 + p.getRandom().nextInt(3600);
        STATES.put(p.getUUID(), st);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        State st = STATES.remove(e.getEntity().getUUID());
        if (st != null && st.visit != null && st.visit.ghost != null) st.visit.ghost.discard();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        State st = stateOf(p);
        ServerLevel level = p.serverLevel();

        if (st.welcomeTimer > 0 && --st.welcomeTimer == 0) {
            welcome(p);
        }

        if (!p.isAlive()) {
            if (st.visit != null) endVisit(st, true);
            return;
        }
        if (p.isSpectator() || level.dimension() != Level.OVERWORLD) return;

        long time = level.getDayTime();
        long day = time / 24000L;
        long tod = time % 24000L;

        if (st.visit != null) {
            tickVisit(p, st, st.visit, level);
            return;
        }

        if (tod < 12000L) {
            dayScares(p, st, level);
        } else if (tod >= 13000L && tod <= 21000L && p.tickCount % 20 == 0) {
            if (st.doneDay == day || !isVisitNight(p.getUUID(), day)) {
                st.insideTicks = 0;
                return;
            }
            Room room = scan(level, p.blockPosition());
            if (isHouse(room)) st.insideTicks += 20; else st.insideTicks = 0;
            if (st.insideTicks >= 200) {
                st.insideTicks = 0;
                startVisit(p, st, level, room, day);
            }
        }
    }

    // ---------- ต้อนรับ ----------
    static void welcome(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Welcome To Boss kragong").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("Created by ZIWX1").withStyle(ChatFormatting.GRAY));
        sendEffect(p, 0);
    }

    static void sendEffect(ServerPlayer p, int type) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ClientEffectPacket(type));
    }

    // ---------- สุ่มตอนกลางวัน ----------
    private static void dayScares(ServerPlayer p, State st, ServerLevel level) {
        long now = level.getGameTime();
        if (now < st.nextScare) return;
        RandomSource r = level.random;
        int roll = r.nextInt(100);
        boolean ok;
        if (roll < 40) {
            sendEffect(p, 1);
            ok = true;
        } else {
            ok = spawnWatcher(p, roll >= 70);
        }
        st.nextScare = now + (ok ? 6000 + r.nextInt(6000) : 200);
    }

    /** สร้างผียืนนิ่งๆ: tree=true แอบหลังต้นไม้, false ยืนบนที่สูง/เนินเขา */
    static boolean spawnWatcher(ServerPlayer p, boolean tree) {
        ServerLevel level = p.serverLevel();
        RandomSource r = level.random;
        for (int i = 0; i < 40; i++) {
            double ang = Math.toRadians(p.getYRot() + 90.0D) + (r.nextDouble() - 0.5D) * 1.6D;
            double dist = tree ? 12 + r.nextInt(14) : 28 + r.nextInt(28);
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (!level.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(feet.below());
            if (!below.isFaceSturdy(level, feet.below(), Direction.UP)) continue;
            if (below.is(BlockTags.LOGS) || !level.getFluidState(feet).isEmpty()) continue;
            if (!passable(level, feet) || !passable(level, feet.above())) continue;

            if (tree) {
                double dx = p.getX() - x, dz = p.getZ() - z;
                Direction d = Math.abs(dx) > Math.abs(dz)
                        ? (dx > 0 ? Direction.EAST : Direction.WEST)
                        : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                if (!level.getBlockState(feet.relative(d)).is(BlockTags.LOGS)) continue;
            } else if (i < 25 && y < p.getBlockY() + 6) {
                continue; // ต้องสูงกว่าผู้เล่นพอสมควร (ถ้าหาไม่ได้จะผ่อนเงื่อนไข)
            }

            BowLegBossGhost g = ModEntities.BOSS_GHOST.get().create(level);
            if (g == null) return false;
            g.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
            g.makeWatcher();
            if (!tree && !p.hasLineOfSight(g)) continue; // บนเขาต้องมองเห็นชัด
            g.faceToward(p.position());
            level.addFreshEntity(g);
            p.playNotifySound(SoundEvents.SOUL_ESCAPE, SoundSource.AMBIENT, 0.8F, 0.6F);
            return true;
        }
        return false;
    }

    // ---------- บ้าน ----------
    static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    static boolean isHouse(Room r) {
        return r.enclosed && (r.door != null || !r.windows.isEmpty());
    }

    /** สแกนห้องที่ผู้เล่นยืนอยู่ ว่าปิดมิดชิด (มีหลังคา) และมีประตู/หน้าต่างหรือไม่ */
    static Room scan(ServerLevel level, BlockPos start) {
        Room room = new Room();
        BlockPos s = start;
        if (!passable(level, s)) {
            s = s.above();
            if (!passable(level, s)) return room;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(s);
        room.cells.add(s);
        while (!queue.isEmpty()) {
            BlockPos c = queue.poll();
            if (level.canSeeSky(c)) return failed(room);
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (room.cells.contains(n)) continue;
                if (!level.hasChunkAt(n)) return failed(room);
                BlockState st = level.getBlockState(n);
                if (st.getCollisionShape(level, n).isEmpty()) {
                    if (room.cells.size() >= 2000) return failed(room);
                    room.cells.add(n);
                    queue.add(n);
                } else if (st.is(BlockTags.DOORS)) {
                    BlockPos dp = n;
                    if (st.hasProperty(DoorBlock.HALF) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) dp = n.below();
                    if (room.door == null) room.door = dp;
                } else if (st.is(Tags.Blocks.GLASS) || st.is(Tags.Blocks.GLASS_PANES)) {
                    if (d.getAxis().isHorizontal() && !room.windows.contains(n)) room.windows.add(n);
                } else if (st.is(BlockTags.BEDS) && room.bed == null) {
                    room.bed = n;
                }
            }
        }
        room.enclosed = room.cells.size() >= 6;
        return room;
    }

    private static Room failed(Room r) {
        r.enclosed = false;
        return r;
    }

    static BlockPos outsideOfDoor(ServerLevel level, Room room) {
        BlockPos d = room.door;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos inner = d.relative(dir);
            BlockPos outer = d.relative(dir.getOpposite());
            if ((room.cells.contains(inner) || room.cells.contains(inner.above())) && passable(level, outer)
                    && passable(level, outer.above())) {
                return outer;
            }
        }
        return null;
    }

    static BlockPos standOutsideWindow(ServerLevel level, Room room, BlockPos w) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos inner = w.relative(dir);
            BlockPos outer = w.relative(dir.getOpposite());
            if (room.cells.contains(inner) && passable(level, outer)) {
                for (int dy = 0; dy <= 6; dy++) {
                    BlockPos p = outer.below(dy);
                    if (passable(level, p) && passable(level, p.above()) && !passable(level, p.below())) return p;
                }
            }
        }
        return null;
    }

    /** ใน 5 วัน มีคืนที่ผีมา 2-3 คืน (สุ่มต่างกันในแต่ละผู้เล่น) */
    static boolean isVisitNight(UUID id, long day) {
        long block = Math.floorDiv(day, 5L);
        int idx = (int) Math.floorMod(day, 5L);
        long seed = (id.getMostSignificantBits() ^ (id.getLeastSignificantBits() * 31L)) ^ (block * 0x9E3779B97F4A7C15L);
        Random r = new Random(seed);
        List<Integer> days = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        Collections.shuffle(days, r);
        int count = 2 + r.nextInt(2);
        return days.subList(0, count).contains(idx);
    }

    // ---------- การมาเยือนตอนกลางคืน ----------
    static boolean startVisit(ServerPlayer p, State st, ServerLevel level, Room room, long day) {
        BlockPos doorOut = room.door != null ? outsideOfDoor(level, room) : null;
        BlockPos winPos = null, winStand = null;
        for (BlockPos w : room.windows) {
            BlockPos stand = standOutsideWindow(level, room, w);
            if (stand != null) {
                winPos = w;
                winStand = stand;
                break;
            }
        }
        if (doorOut == null && winStand == null) return false;

        BowLegBossGhost g = ModEntities.BOSS_GHOST.get().create(level);
        if (g == null) return false;

        Visit v = new Visit();
        v.room = room;
        v.doorOut = doorOut;
        v.winPos = winPos;
        v.winStand = winStand;
        v.ghost = g;
        v.stage = doorOut != null ? ST_DOOR : ST_WINDOW;

        Vec3 pos = Vec3.atBottomCenterOf(doorOut != null ? doorOut : winStand);
        g.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        g.makeScripted();
        g.setHitHook(hitter -> {
            if (hitter.getUUID().equals(p.getUUID())) v.repelled = true;
        });
        g.faceToward(Vec3.atCenterOf(doorOut != null ? room.door : winPos));
        level.addFreshEntity(g);

        st.doneDay = day;
        st.visit = v;
        return true;
    }

    private static void endVisit(State st, boolean discardGhost) {
        if (st.visit != null && discardGhost && st.visit.ghost != null) st.visit.ghost.discard();
        st.visit = null;
    }

    private static void tickVisit(ServerPlayer p, State st, Visit v, ServerLevel level) {
        BowLegBossGhost g = v.ghost;
        if (g == null || g.isRemoved()) {
            st.visit = null;
            return;
        }
        v.timer++;

        switch (v.stage) {
            case ST_DOOR -> {
                if (leftHouse(p, v)) { abortToHunt(st, v); return; }
                g.faceToward(Vec3.atCenterOf(v.room.door));
                int m = v.timer % 40;
                if (m == 0 || m == 7 || m == 14) {
                    level.playSound(null, v.room.door, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 3.0F, 0.8F);
                }
                if (v.timer >= 200) {
                    if (v.winStand != null) enterWindow(v); else enterChallenge(p, v, level);
                }
            }
            case ST_WINDOW -> {
                if (leftHouse(p, v)) { abortToHunt(st, v); return; }
                g.faceToward(Vec3.atCenterOf(v.winPos));
                int m = v.timer % 30;
                if (m == 0 || m == 5) {
                    level.playSound(null, v.winPos, SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 4.0F, 0.6F);
                }
                if (v.timer >= 140) enterChallenge(p, v, level);
            }
            case ST_CHALLENGE -> {
                Vec3 target = p.position();
                Vec3 cur = g.position();
                Vec3 delta = target.subtract(cur);
                double dist = delta.length();
                Vec3 next = cur;
                if (dist > 1.3D) {
                    next = cur.add(delta.normalize().scale(Math.min(0.18D, dist - 1.3D)));
                }
                g.moveAndFace(next, target);
                if (v.timer % 20 == 0) {
                    level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 1.0F);
                    p.displayClientMessage(Component.literal("ตีมัน!!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                }
                if (v.repelled) enterFlee(p, v, level);
                else if (v.timer >= 120) enterGrab(p, v, level);
            }
            case ST_GRAB -> tickGrab(p, v, level);
            case ST_FLEE -> {
                if (v.timer <= 8) {
                    // 1000 km/h ~= 13.9 บล็อกต่อทิก
                    Vec3 np = g.position().add(v.fleeDir.scale(13.9D));
                    g.setPos(np.x, np.y, np.z);
                    level.sendParticles(ParticleTypes.CLOUD, np.x, np.y + 1.0, np.z, 6, 0.2, 0.6, 0.2, 0.05);
                    level.sendParticles(ParticleTypes.SMOKE, np.x, np.y + 1.0, np.z, 6, 0.2, 0.6, 0.2, 0.05);
                } else {
                    g.discard();
                    st.visit = null;
                }
            }
            default -> st.visit = null;
        }
    }

    private static boolean leftHouse(ServerPlayer p, Visit v) {
        if (p.tickCount % 10 != 0) return false;
        BlockPos bp = p.blockPosition();
        return !(v.room.cells.contains(bp) || v.room.cells.contains(bp.above()));
    }

    /** ผู้เล่นออกจากบ้าน: ผีกลายเป็นล่าปกติ */
    private static void abortToHunt(State st, Visit v) {
        v.ghost.releaseToHunt();
        st.visit = null;
    }

    private static void enterWindow(Visit v) {
        v.stage = ST_WINDOW;
        v.timer = 0;
        Vec3 pos = Vec3.atBottomCenterOf(v.winStand);
        v.ghost.moveAndFace(pos, Vec3.atCenterOf(v.winPos));
    }

    private static void title(ServerPlayer p, String main, String sub, ChatFormatting color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
        if (sub != null) p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(main).withStyle(color)));
    }

    private static void enterChallenge(ServerPlayer p, Visit v, ServerLevel level) {
        v.stage = ST_CHALLENGE;
        v.timer = 0;
        BowLegBossGhost g = v.ghost;
        g.noPhysics = true;
        g.setNoGravity(true);

        level.playSound(null, p.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 3.0F, 0.7F);

        Vec3 pos;
        if (v.room.door != null && v.doorOut != null) {
            BlockState ds = level.getBlockState(v.room.door);
            if (ds.getBlock() instanceof DoorBlock db && ds.hasProperty(DoorBlock.OPEN) && !ds.getValue(DoorBlock.OPEN)) {
                db.setOpen(null, level, ds, v.room.door, true);
            }
            pos = Vec3.atBottomCenterOf(v.room.door);
        } else {
            Vec3 look = Vec3.directionFromRotation(0.0F, p.getYRot());
            pos = p.position().add(look.scale(3.0D));
        }
        g.moveAndFace(pos, p.position());
        title(p, "มันเข้ามาแล้ว!", "ตีมันให้ทัน!", ChatFormatting.DARK_RED);
    }

    private static void enterGrab(ServerPlayer p, Visit v, ServerLevel level) {
        v.stage = ST_GRAB;
        v.timer = 0;
        BowLegBossGhost g = v.ghost;
        title(p, "ไม่ทันแล้ว...", "ผีพี่บอสจับคุณกดไว้", ChatFormatting.DARK_RED);

        Vec3 look = Vec3.directionFromRotation(0.0F, p.getYRot());
        if (v.room.bed != null) {
            Vec3 bp = Vec3.atBottomCenterOf(v.room.bed);
            p.teleportTo(bp.x, bp.y + 0.1D, bp.z);
            p.startSleeping(v.room.bed);
            v.hold = bp;
            Vec3 dir = new Vec3(g.getX() - bp.x, 0, g.getZ() - bp.z);
            dir = dir.lengthSqr() < 0.01D ? new Vec3(1, 0, 0) : dir.normalize();
            g.moveAndFace(bp.add(dir.scale(1.0D)), bp);
        } else {
            v.hold = p.position();
            g.moveAndFace(v.hold.add(look.scale(0.9D)), v.hold);
        }
    }

    private static void tickGrab(ServerPlayer p, Visit v, ServerLevel level) {
        BowLegBossGhost g = v.ghost;
        // ล็อกผู้เล่นไว้กับที่
        if (v.room.bed != null) {
            if (!p.isSleeping()) p.startSleeping(v.room.bed);
        } else if (v.timer % 2 == 0) {
            p.connection.teleport(v.hold.x, v.hold.y, v.hold.z, p.getYRot(), p.getXRot());
        }
        g.faceToward(p.position());

        if (v.timer == 1) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0, false, false));
        if (v.timer == 40) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 0, false, false));

        if (v.timer % 10 == 0) {
            SoundEvent[] sounds = {SoundEvents.BONE_BLOCK_BREAK, SoundEvents.SLIME_SQUISH, SoundEvents.PLAYER_HURT};
            SoundEvent s = sounds[(v.timer / 10) % sounds.length];
            level.playSound(null, p.blockPosition(), s, SoundSource.HOSTILE, 2.5F, 0.6F + level.random.nextFloat() * 0.3F);
        }
        if (v.timer == 30) p.displayClientMessage(Component.literal("...มีดผ่าตัด...").withStyle(ChatFormatting.DARK_RED), true);
        if (v.timer == 80) p.displayClientMessage(Component.literal("...กระดูกขาถูกหัก...").withStyle(ChatFormatting.DARK_RED), true);
        if (v.timer == 130) p.displayClientMessage(Component.literal("...ขาโก่งแล้ว...").withStyle(ChatFormatting.DARK_RED), true);

        if (v.timer >= 170) finishSurgery(p, v, level);
    }

    private static DamageSource surgerySource(ServerLevel level) {
        Registry<DamageType> reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> h = reg.getHolderOrThrow(
                ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(BossGhostMod.MODID, "surgery")));
        return new DamageSource(h);
    }

    private static void finishSurgery(ServerPlayer p, Visit v, ServerLevel level) {
        Vec3 pos = p.position();
        String name = p.getGameProfile().getName();

        p.hurt(surgerySource(level), Float.MAX_VALUE);
        if (p.isAlive()) p.kill();

        BowLegBossGhost newGhost = ModEntities.BOSS_GHOST.get().create(level);
        if (newGhost != null) {
            newGhost.moveTo(pos.x, pos.y, pos.z, p.getYRot(), 0.0F);
            newGhost.setSummoned(true);
            newGhost.setPersistenceRequired();
            newGhost.setCustomName(Component.literal("ผี " + name));
            newGhost.setCustomNameVisible(true);
            level.addFreshEntity(newGhost);
        }
        level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 1.0, pos.z, 40, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, BlockPos.containing(pos), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.5F);

        if (v.ghost != null) v.ghost.discard();
        State st = STATES.get(p.getUUID());
        if (st != null) st.visit = null;
    }

    private static void enterFlee(ServerPlayer p, Visit v, ServerLevel level) {
        v.stage = ST_FLEE;
        v.timer = 0;
        BowLegBossGhost g = v.ghost;
        g.noPhysics = true;
        g.setNoGravity(true);
        g.setHitHook(null);

        Vec3 dir;
        if (v.doorOut != null) {
            dir = Vec3.atCenterOf(v.doorOut).subtract(p.position());
        } else {
            dir = g.position().subtract(p.position());
        }
        dir = new Vec3(dir.x, 0.0D, dir.z);
        if (dir.lengthSqr() < 0.01D) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            dir = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
        }
        v.fleeDir = dir.normalize();
        g.faceToward(g.position().add(v.fleeDir));

        level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 3.0F, 0.5F);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
        title(p, "มันวิ่งหนีไปในความมืด...", null, ChatFormatting.GRAY);
    }

    // ---------- คำสั่งทดสอบ (ต้องเป็น OP / เปิด cheats) ----------
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bossghost").requires(s -> s.hasPermission(2))
                .then(Commands.literal("welcome").executes(c -> {
                    welcome(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("jumpscare").executes(c -> {
                    sendEffect(c.getSource().getPlayerOrException(), 1);
                    return 1;
                }))
                .then(Commands.literal("mountain").executes(c -> {
                    boolean ok = spawnWatcher(c.getSource().getPlayerOrException(), false);
                    if (!ok) c.getSource().sendFailure(Component.literal("หาที่ยืนไม่เจอ ลองอีกครั้ง"));
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("tree").executes(c -> {
                    boolean ok = spawnWatcher(c.getSource().getPlayerOrException(), true);
                    if (!ok) c.getSource().sendFailure(Component.literal("หาต้นไม้ไม่เจอ ลองไปใกล้ป่า"));
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("visit").executes(c -> forceVisit(c.getSource().getPlayerOrException(), c.getSource()))));
    }

    private static int forceVisit(ServerPlayer p, net.minecraft.commands.CommandSourceStack src) throws CommandSyntaxException {
        State st = stateOf(p);
        ServerLevel level = p.serverLevel();
        if (st.visit != null) {
            src.sendFailure(Component.literal("กำลังมีผีมาเยือนอยู่แล้ว"));
            return 0;
        }
        Room room = scan(level, p.blockPosition());
        if (!isHouse(room) || !startVisit(p, st, level, room, level.getDayTime() / 24000L)) {
            src.sendFailure(Component.literal("ต้องยืนในบ้านที่มีหลังคา และมีประตูหรือหน้าต่างกระจก"));
            return 0;
        }
        return 1;
    }
}
