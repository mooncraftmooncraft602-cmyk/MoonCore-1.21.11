package com.mooncore.companion.mixin;

import com.mooncore.companion.ClientRigRegistry;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
        "net.minecraft.client.render.entity.EntityRenderDispatcher",
        "net.minecraft.client.render.entity.EntityRenderManager"
})
public abstract class MoonCoreHideBlockDisplayMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void mooncore$hideVanillaRigBone(Entity entity, Frustum frustum,
                                             double x, double y, double z,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (ClientRigRegistry.shouldHideVanillaBone(entity)) {
            cir.setReturnValue(false);
        }
    }
}
