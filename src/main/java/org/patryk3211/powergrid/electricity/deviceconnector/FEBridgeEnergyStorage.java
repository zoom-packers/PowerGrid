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
package org.patryk3211.powergrid.electricity.deviceconnector;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;

// NeoForge: Team Reborn Energy's EnergyStorage became NeoForge's EnergyHandler (same FE units, int amounts per
// operation); Fabric's SnapshotParticipant became SnapshotJournal.
public class FEBridgeEnergyStorage extends SnapshotJournal<Long> implements EnergyHandler, IFEBridgeHandler {
    private final BlockEntity be;
    public long capacity;
    public long amount;

    public FEBridgeEnergyStorage(BlockEntity be) {
        this.be = be;
    }

    @Override
    protected Long createSnapshot() {
        return this.amount;
    }

    @Override
    protected void revertToSnapshot(Long snapshot) {
        this.amount = snapshot;
    }

    @Override
    protected void onRootCommit(Long originalState) {
        be.setChanged();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        // Insertion not allowed
        TransferPreconditions.checkNonNegative(amount);
        return 0;
    }

    @Override
    public int extract(int maxAmount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(maxAmount);
        int extracted = (int) Math.min(maxAmount, this.amount);
        if(extracted > 0) {
            this.updateSnapshots(transaction);
            this.amount -= extracted;
            return extracted;
        } else {
            return 0;
        }
    }

    @Override
    public long getAmountAsLong() {
        return amount;
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public void setAmount(long amount) {
        this.amount = amount;
    }

    @Override
    public long moveEnergy() {
        if(amount > 0) {
            // Try to move energy
            var facing = be.getBlockState().getValue(BlockStateProperties.FACING);
            var sideStorage = be.getLevel().getCapability(Capabilities.Energy.BLOCK, be.getBlockPos().relative(facing), facing.getOpposite());
            return EnergyHandlerUtil.move(this, sideStorage, Integer.MAX_VALUE, null);
        }
        return 0;
    }

    @Override
    public void setChanged() {
        be.setChanged();
    }
}
