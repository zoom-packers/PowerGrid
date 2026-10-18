/*
 * Copyright 2026 Zoom Packers (NeoForge 26.3 port of Create: Power Grid)
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
package org.patryk3211.powergrid.neoforge.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fabric API registered client content with direct calls (EntityRendererRegistry, KeyMappingHelper, BlockColorRegistry,
 * ParticleProviderRegistry, HudElementRegistry, ...). NeoForge uses mod-bus events that fire after mod construction,
 * so Power Grid's client setup, which still runs in the constructor, queues its registrations here and they are
 * replayed when the matching event fires.
 */
public final class ClientRegistration {
    private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> ENTITY_RENDERERS = new ArrayList<>();
    private static final List<Consumer<EntityRenderersEvent.AddLayers>> LAYERS = new ArrayList<>();
    private static final List<Consumer<RegisterKeyMappingsEvent>> KEYS = new ArrayList<>();
    private static final List<Consumer<RegisterColorHandlersEvent.BlockTintSources>> BLOCK_TINTS = new ArrayList<>();
    private static final List<Consumer<RegisterParticleProvidersEvent>> PARTICLES = new ArrayList<>();
    private static final List<Consumer<RegisterParticleGroupsEvent>> PARTICLE_GROUPS = new ArrayList<>();
    private static final List<Consumer<RegisterGuiLayersEvent>> GUI_LAYERS = new ArrayList<>();
    private static final List<Consumer<RegisterRenderPipelinesEvent>> PIPELINES = new ArrayList<>();

    private ClientRegistration() {
    }

    public static <T extends Entity> void entityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
        ENTITY_RENDERERS.add(event -> event.registerEntityRenderer(type, provider));
    }

    public static void layers(Consumer<EntityRenderersEvent.AddLayers> adder) {
        LAYERS.add(adder);
    }

    public static void keyCategory(KeyMapping.Category category) {
        KEYS.add(event -> event.registerCategory(category));
    }

    public static void keyMapping(KeyMapping key) {
        KEYS.add(event -> event.register(key));
    }

    public static void blockTints(List<BlockTintSource> sources, Block... blocks) {
        BLOCK_TINTS.add(event -> event.register(sources, blocks));
    }

    public static <T extends ParticleOptions> void particleSprites(ParticleType<T> type, ParticleResources.SpriteParticleRegistration<T> registration) {
        PARTICLES.add(event -> event.registerSpriteSet(type, registration));
    }

    public static <T extends ParticleOptions> void particleSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
        PARTICLES.add(event -> event.registerSpecial(type, provider));
    }

    public static void particleGroup(ParticleRenderType group, Function<ParticleEngine, ParticleGroup<?>> factory) {
        PARTICLE_GROUPS.add(event -> event.register(group, factory));
    }

    /** Fabric's {@code HudElementRegistry.addLast}. */
    public static void guiLayerAboveAll(Identifier id, GuiLayer layer) {
        GUI_LAYERS.add(event -> event.registerAboveAll(id, layer));
    }

    public static void renderPipeline(RenderPipeline pipeline) {
        PIPELINES.add(event -> event.registerPipeline(pipeline));
    }

    static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> ENTITY_RENDERERS.forEach(c -> c.accept(event)));
        modBus.addListener((EntityRenderersEvent.AddLayers event) -> LAYERS.forEach(c -> c.accept(event)));
        modBus.addListener((RegisterKeyMappingsEvent event) -> KEYS.forEach(c -> c.accept(event)));
        modBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> BLOCK_TINTS.forEach(c -> c.accept(event)));
        modBus.addListener((RegisterParticleProvidersEvent event) -> PARTICLES.forEach(c -> c.accept(event)));
        modBus.addListener((RegisterParticleGroupsEvent event) -> PARTICLE_GROUPS.forEach(c -> c.accept(event)));
        modBus.addListener((RegisterGuiLayersEvent event) -> GUI_LAYERS.forEach(c -> c.accept(event)));
        modBus.addListener((RegisterRenderPipelinesEvent event) -> PIPELINES.forEach(c -> c.accept(event)));
    }
}
