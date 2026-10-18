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
package org.patryk3211.powergrid.electricity.bell;

import org.patryk3211.powergrid.client.ClientSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.patryk3211.powergrid.collections.ModdedSoundEvents;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import net.minecraft.world.phys.Vec3;

public class AlarmBellBlockEntity extends ElectricBlockEntity {
    private ElectricWire wire;

    private boolean hasSoundInstance = false;
    private float prevPitch, prevVolume;

    public AlarmBellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public @Nullable ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.fromConfig(this);
    }

    public void tickAudio() {
        if(getVolume() > 0 && !hasSoundInstance) {
            ClientSounds.alarmBell(this);
            hasSoundInstance = true;
        } else if(getVolume() == 0 && hasSoundInstance) {
            ClientSounds.alarmBellEnd(this, prevVolume, prevPitch);
            hasSoundInstance = false;
        }
        prevVolume = getVolume();
        prevPitch = getPitch();
    }

    @Override
    public void electricalTick() {
        applyPower(wire);
    }

    public float getVolume() {
        double I = Math.abs(wire.current());
        if(I < 0.25)
            return 0;
        return (float) (I * 2.0);
    }

    public float getPitch() {
        double I = Math.abs(wire.current());
        if(I < 0.5)
            return 0.75f;
        return (float) Math.min(0.5 + I * 0.5, 1.25);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        wire = builder.connect(resistance(), builder.terminalNode(0), builder.terminalNode(1));
    }
}
