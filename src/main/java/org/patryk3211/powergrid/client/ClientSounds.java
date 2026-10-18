/*
 * Copyright 2025 patryk3211
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
package org.patryk3211.powergrid.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.bell.AlarmBellBlockEntity;
import org.patryk3211.powergrid.electricity.bell.AlarmBellSoundInstance;
import org.patryk3211.powergrid.electricity.particles.HvSparkSoundInstance;
import org.patryk3211.powergrid.electricity.particles.SparkSoundOwner;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorBehaviour;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorSoundInstance;

/**
 * ZP5 port (NeoForge 26.3): the sounds block entities start on the client. Building a sound instance and handing it
 * to the sound manager inside a block entity made the verifier load the client's sound classes as soon as the block
 * entity class loaded, which crashed a dedicated server during block entity registration. Callers only reach this
 * class on the client.
 */
public final class ClientSounds {
    private ClientSounds() {}

    public static void alarmBell(AlarmBellBlockEntity be) {
        Minecraft.getInstance().getSoundManager().play(new AlarmBellSoundInstance(be));
    }

    public static void alarmBellEnd(AlarmBellBlockEntity be, float volume, float pitch) {
        var pos = Vec3.atCenterOf(be.getBlockPos());
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModdedSoundEvents.ALARM_BELL_END.getMainEvent(),
                SoundSource.BLOCKS, volume, pitch, be.getLevel().getRandom(), pos.x, pos.y, pos.z));
    }

    public static <T extends BlockEntity & SparkSoundOwner> void hvSpark(T be) {
        Minecraft.getInstance().getSoundManager().play(new HvSparkSoundInstance(be));
    }

    public static void rotor(RotorBehaviour behaviour) {
        Minecraft.getInstance().getSoundManager().play(new RotorSoundInstance(behaviour));
    }
}
