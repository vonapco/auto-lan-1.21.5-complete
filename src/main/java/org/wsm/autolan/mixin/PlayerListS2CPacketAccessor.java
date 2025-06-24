package org.wsm.autolan.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Entry;

@Mixin(PlayerListS2CPacket.class)
public interface PlayerListS2CPacketAccessor {
    @Accessor("entries")
    List<Entry> getEntries();
} 