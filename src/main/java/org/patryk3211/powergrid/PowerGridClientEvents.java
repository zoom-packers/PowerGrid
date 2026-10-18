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
package org.patryk3211.powergrid;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.patryk3211.powergrid.neoforge.client.ClientRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.collections.ModPackets;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.collections.ModdedKeys;
import org.patryk3211.powergrid.collections.ModdedPackets;
import org.patryk3211.powergrid.config.ServerConfigSync;
import org.patryk3211.powergrid.electricity.ClientElectricNetwork;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.info.PowerGridTooltipModifiers;
import org.patryk3211.powergrid.electricity.info.TerminalHandler;
import org.patryk3211.powergrid.electricity.wire.ClientWireInteractions;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.electricity.wire.WirePreview;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItemRenderer;
import org.patryk3211.powergrid.network.packets.EntityDataS2CPacket;
import org.patryk3211.powergrid.network.packets.NegotiateSyncC2SPacket;
import org.patryk3211.powergrid.utility.PlacementOverlay;

public class PowerGridClientEvents {
    private static boolean alternatePlacementDown;

    // NeoForge: Fabric's client lifecycle, tick, tooltip, interaction, HUD and level-render callbacks became the
    // NeoForge events below. SubmitCustomGeometryEvent fires at the end of LevelRenderer#submitFeatures with that
    // method's identity PoseStack, where Fabric's LevelRenderEvents.COLLECT_SUBMITS was injected.
    public static void register() {
        ModdedKeys.register();
        ModPackets.PACKETS.registerS2CListener();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> negotiateSync());
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            var client = Minecraft.getInstance();
            ClientElectricNetwork.unloadWorld(client, client.level);
            EntityDataS2CPacket.reset();
            ServerConfigSync.restore();
        });
        bus.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ClientLevel level) {
                ClientElectricNetwork.levelChanged(Minecraft.getInstance(), level);
                EntityDataS2CPacket.reset();
            }
        });

        bus.addListener((LevelTickEvent.Pre event) -> {
            if (event.getLevel() instanceof ClientLevel level)
                GlobalElectricNetworks.preTick(level);
        });
        bus.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ClientLevel level)
                TerminalHandler.tick(level);
        });
        bus.addListener((ClientTickEvent.Post event) -> clientTick(Minecraft.getInstance()));

        bus.addListener((ItemTooltipEvent event) -> {
            var stack = event.getItemStack();
            var lines = event.getToolTip();
            WireItem.tooltip(stack, lines, event.getContext(), event.getFlags());
            PowerGridTooltipModifiers.appendElectricProperties(stack, event.getFlags(), lines);
        });
        bus.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel().isClientSide())
                EntityDataS2CPacket.clientEntityAdded(event.getEntity());
        });

        bus.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (cutClear(event.getLevel()) != InteractionResult.PASS) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        bus.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (cutClear(event.getLevel()) != InteractionResult.PASS) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        bus.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (cutClear(event.getLevel()) != InteractionResult.PASS) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });

        ClientRegistration.guiLayerAboveAll(PowerGrid.asResource("placement_overlay"),
                (extractor, delta) -> PlacementOverlay.renderOverlay(extractor));

        bus.addListener((SubmitCustomGeometryEvent event) -> {
            var client = Minecraft.getInstance();
            if(client.level == null || client.player == null)
                return;
            var cameraPos = event.getLevelRenderState().cameraRenderState.pos;
            if(cameraPos == null)
                return;
            WirePreview.render(event.getPoseStack(), event.getSubmitNodeCollector(), client.level, client.player, cameraPos);
            MultimeterItemRenderer.render(event.getPoseStack(), event.getSubmitNodeCollector(), client.level, client.player, cameraPos);
        });
    }

    private static void negotiateSync() {
        ModdedPackets.sendToServer(new NegotiateSyncC2SPacket(ModdedConfigs.common().syncWithDoubles.get()));
    }

    /**
     * Fabric had no raw key or mouse event, so the alternate placement modifier is edge polled
     * once a tick and fed to the same handler the raw input event used to call.
     */
    private static void clientTick(Minecraft client) {
        PowerGridClient.clientTick(client);
        if (client.player == null || client.level == null) {
            alternatePlacementDown = false;
            return;
        }
        boolean down = ModdedKeys.ALTERNATE_WIRE_PLACEMENT.isPressed();
        if (down != alternatePlacementDown) {
            alternatePlacementDown = down;
            ClientWireInteractions.alternatePlacementCheck(client, down ? InputConstants.PRESS : InputConstants.RELEASE);
        }
    }

    private static InteractionResult cutClear(Level level) {
        if (!level.isClientSide())
            return InteractionResult.PASS;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return InteractionResult.PASS;
        return ClientWireInteractions.cutClearCheck(client) ? InteractionResult.FAIL : InteractionResult.PASS;
    }
}
