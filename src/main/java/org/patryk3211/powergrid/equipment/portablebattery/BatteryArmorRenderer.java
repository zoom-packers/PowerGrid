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
package org.patryk3211.powergrid.equipment.portablebattery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zurrtum.create.client.catnip.render.CachedBuffers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.neoforge.client.ClientRegistration;

/**
 * NeoForge: Fabric's ArmorRenderer has no NeoForge counterpart, so the worn battery is a render layer again (as in
 * upstream Power Grid and Create's backtank), added to player, mannequin and armor stand renderers. The battery's
 * equipment asset ({@code powergrid:zinc}) has no layers, so vanilla's armor layer draws nothing for it.
 */
public class BatteryArmorRenderer<S extends HumanoidRenderState, M extends HumanoidModel<? super S>> extends RenderLayer<S, M> {
    public BatteryArmorRenderer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        ClientRegistration.layers(event -> {
            for (var skin : event.getSkins()) {
                addTo(event.getPlayerRenderer(skin));
                addTo(event.getMannequinRenderer(skin));
            }
            addTo(event.getRenderer(EntityTypes.ARMOR_STAND));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addTo(Object renderer) {
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel<?>)
            living.addLayer(new BatteryArmorRenderer(living));
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector submitter, int light, S state, float yRot, float xRot) {
        if(state.pose == Pose.SLEEPING)
            return;

        if(!(state.chestEquipment.getItem() instanceof PortableBatteryItem item))
            return;

        var renderedState = item.getBlock().defaultBlockState()
                .setValue(HorizontalElectricBlock.HORIZONTAL_FACING, Direction.NORTH);
        var battery = CachedBuffers.block(renderedState);

        matrices.pushPose();

        getParentModel().body.translateAndRotate(matrices);
        matrices.translate(-1 / 2f, 10 / 16f, 1f);
        matrices.scale(1, -1, -1);

        battery.disableDiffuse()
                .light(light)
                .submit(Sheets.cutoutBlockItemSheet(), matrices, submitter);

        matrices.popPose();
    }
}
