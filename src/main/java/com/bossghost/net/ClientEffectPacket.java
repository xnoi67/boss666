package com.bossghost.net;

import java.util.function.Supplier;

import com.bossghost.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** type 0 = เสียงต้อนรับ, type 1 = jumpscare */
public class ClientEffectPacket {
    private final int type;

    public ClientEffectPacket(int type) {
        this.type = type;
    }

    public static void encode(ClientEffectPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.type);
    }

    public static ClientEffectPacket decode(FriendlyByteBuf buf) {
        return new ClientEffectPacket(buf.readVarInt());
    }

    public static void handle(ClientEffectPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handle(m.type)));
        ctx.get().setPacketHandled(true);
    }
}
