package org.wsm.autolan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.OperatorList;
import net.minecraft.server.PlayerManager;

@Mixin(PlayerManager.class)
public interface PlayerManagerAccessor {
    @Accessor
    @Mutable
    public void setMaxPlayers(int maxPlayers);
    
    @Accessor("ops")
    public OperatorList getOps();
}
