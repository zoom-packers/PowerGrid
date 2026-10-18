/*
 * Copyright 2025 patryk3211
 * Modified 2026 by chaevsfe for the unofficial Fabric / Create Fly 26.2 port.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.patryk3211.powergrid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.patryk3211.powergrid.PowerGridClient;
import org.patryk3211.powergrid.equipment.drill.DrillItem;
import org.patryk3211.powergrid.equipment.saw.SawItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3: ItemInHandRenderer became FirstPersonHandsAndItemsRenderer, which works from render states. Create's own
 * mixin wraps the call to submitArmWithItem and only falls through to it for stacks it does not handle, so this one
 * injects at the head of submitArmWithItem itself and the two cannot fight.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void powerGrid$submitToolArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTick, float xRot,
                                         InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
                                         PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if(state.isScoping || playerState.avatarRenderState == null || minecraft.player == null)
            return;
        if(PowerGridClient.ELECTRO_ZAPPER_RENDER_HANDLER.onRenderPlayerHand(stack, minecraft, playerState, state,
                poseStack, collector, light, partialTick, hand, equipProgress, swingProgress)) {
            ci.cancel();
        } else if(stack.getItem() instanceof DrillItem || stack.getItem() instanceof SawItem) {
            powerGrid$submitPortableTool(playerState, state, hand, swingProgress, equipProgress, poseStack, collector, light);
            ci.cancel();
        }
    }

    @Unique
    private void powerGrid$submitPortableTool(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, InteractionHand hand,
                                              float swingProgress, float equipProgress, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        boolean rightHand = hand == InteractionHand.MAIN_HAND ^ playerState.avatarRenderState.mainArm == HumanoidArm.LEFT;
        float flip = rightHand ? 1.0f : -1.0f;

        poseStack.pushPose();
        poseStack.translate(flip * (0.64f - 0.1f), -0.6f + equipProgress * -0.6f, -0.72f - 0.1f);

        RandomSource random = minecraft.player.level().getRandom();
        poseStack.rotate(Axis.YP.rotationDegrees(flip * (random.nextFloat() * 2 - 1) * 5.0f * swingProgress));
        poseStack.rotate(Axis.ZP.rotationDegrees(flip * (random.nextFloat() * 2 - 1) * -2.0f * swingProgress));
        // The hand's item render state was resolved for the matching first-person display context
        (hand == InteractionHand.MAIN_HAND ? state.mainHandRenderState : state.offHandRenderState)
                .submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
