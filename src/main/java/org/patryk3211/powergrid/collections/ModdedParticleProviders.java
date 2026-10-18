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
package org.patryk3211.powergrid.collections;

import org.patryk3211.powergrid.neoforge.client.ClientRegistration;
import net.minecraft.client.particle.ParticleRenderType;
import org.patryk3211.powergrid.electricity.electromagnet.MagnetizationParticle;
import org.patryk3211.powergrid.electricity.electromagnet.MagnetizationParticleData;
import org.patryk3211.powergrid.electricity.particles.SparkParticle;
import org.patryk3211.powergrid.electricity.particles.SparkParticleData;
import org.patryk3211.powergrid.electricity.particles.ZapParticle;
import org.patryk3211.powergrid.electricity.particles.ZapParticleGroup;

public class ModdedParticleProviders {
    public static void register() {
        // NeoForge: Fabric's ParticleProviderRegistry / ParticleGroupRegistry became the RegisterParticleProvidersEvent /
        // RegisterParticleGroupsEvent registrations in ClientRegistration. NeoForge appends a new group to the end of the
        // render order, which keeps Fabric's "ZAP after SINGLE_QUADS" ordering.
        ClientRegistration.particleSprites(ModdedParticles.MAGNETIZATION, MagnetizationParticle.Factory::new);
        ClientRegistration.particleSprites(ModdedParticles.CUBE_SPARK, SparkParticle.Factory::new);
        ClientRegistration.particleSpecial(ModdedParticles.ZAP, new ZapParticle.Factory());
        ClientRegistration.particleGroup(ZapParticleGroup.ZAP, ZapParticleGroup::new);
    }
}
