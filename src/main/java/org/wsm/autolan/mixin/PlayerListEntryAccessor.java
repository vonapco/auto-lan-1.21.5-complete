package org.wsm.autolan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

@Mixin(PlayerListEntry.class)
public interface PlayerListEntryAccessor {
    @Accessor("displayName")
    @Mutable
    void setDisplayName(Text text);
} 