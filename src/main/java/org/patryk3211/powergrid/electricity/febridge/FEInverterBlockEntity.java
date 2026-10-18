package org.patryk3211.powergrid.electricity.febridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.Direction;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FEInverterBlockEntity extends ElectricBlockEntity {
    private final InverterEnergyStorage storage = new InverterEnergyStorage();
    private ProvidedVoltageSourceCoupling outputSource;
    private CRSeriesWire control;
    private float prevThrottling;

    public FEInverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private float rawThrottling() {
        return Mth.clamp((float) (control.capacitorVoltage() / ModdedConfigs.server().electricity.feInverterControlVoltage.get()), 0, 1);
    }

    protected float inputThrottling() {
        return Mth.clamp((rawThrottling() + prevThrottling) * 0.5f, 0, 1);
    }

    public static int energyBufferSize() {
        return ModdedConfigs.server().electricity.feInverterBufferSize.get();
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        prevThrottling = rawThrottling() * 0.5f + prevThrottling * 0.5f;
        double power = -outputSource.getCurrent() * outputSource.getVoltage();
        power -= outputSource.getCurrent() * outputSource.getCurrent() * outputSource.getResistance();
        setUnsaved();
        if(power < 0)
            return;
        int fe = (int) Math.ceil(ModdedConfigs.server().electricity.forgeEnergyPerWatt.getF() * power);
        useEnergy(fe);
    }

    protected float outputVoltage() {
        return (storedEnergy() / ModdedConfigs.server().electricity.forgeEnergyPerVolt.getF() * (1 - inputThrottling()));
    }

    protected float outputResistance() {
        float V = outputVoltage();
        float W = storedEnergy() / ModdedConfigs.server().electricity.forgeEnergyPerWatt.getF();
        float R = V * V / (2 * W);
        if(W <= 0 || R <= 0)
            return 1000;
        return R;
    }

    @Override
    protected void write(ValueOutput tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putFloat("ControlVoltage", (float) control.capacitorVoltage());
        tag.putFloat("PrevThrottle", prevThrottling);
        tag.putInt("Energy", (int) storage.amount);
    }

    @Override
    protected void read(ValueInput tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        control.setVoltage(tag.getFloatOr("ControlVoltage", 0.0f));
        prevThrottling = tag.getFloatOr("PrevThrottle", 0.0f);
        storage.amount = tag.getIntOr("Energy", 0);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(3);
        outputSource = new ProvidedVoltageSourceCoupling(builder.terminalNode(0), builder.terminalNode(1), 1);
        outputSource.setVoltageProvider(this::outputVoltage);
        outputSource.setResistanceProvider(this::outputResistance);
        builder.add(outputSource);
        control = new CRSeriesWire(
                ModdedConfigs.server().electricity.feInverterControlCapacitance.get(),
                10000, builder.terminalNode(2), builder.terminalNode(1));
        builder.connect(100000, builder.terminalNode(2), builder.terminalNode(1));
        builder.add(control);
    }

    protected void useEnergy(int amount) {
        storage.amount = Math.max(0, storage.amount - amount);
        setChanged();
    }

    protected int storedEnergy() {
        return (int) storage.amount;
    }

    // NeoForge: the energy capability is only exposed on the facing side, and capability caches (Pipez, AE2) keep a
    // result until it is invalidated, so a wrench rotation that keeps this block entity must invalidate them
    @Override
    public void setBlockState(BlockState state) {
        var oldFacing = getBlockState().getValue(FEInverterBlock.FACING);
        super.setBlockState(state);
        if (state.getValue(FEInverterBlock.FACING) != oldFacing && level != null)
            invalidateCapabilities();
    }

    public EnergyHandler getEnergyStorage(Direction side) {
        if(side != null && side != getBlockState().getValue(FEInverterBlock.FACING))
            return null;
        return storage;
    }

    // NeoForge: Team Reborn Energy's EnergyStorage -> NeoForge EnergyHandler, SnapshotParticipant -> SnapshotJournal
    public class InverterEnergyStorage extends SnapshotJournal<Long> implements EnergyHandler {
        public long amount;

        @Override
        protected Long createSnapshot() {
            return amount;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            amount = snapshot;
        }

        @Override
        protected void onRootCommit(Long originalState) {
            setChanged();
        }

        @Override
        public int insert(int maxAmount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(maxAmount);
            int inserted = (int) Math.min(maxAmount, getCapacityAsLong() - amount);
            if(inserted <= 0)
                return 0;
            updateSnapshots(transaction);
            amount += inserted;
            return inserted;
        }

        @Override
        public int extract(int maxAmount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(maxAmount);
            return 0;
        }

        @Override
        public long getAmountAsLong() {
            return amount;
        }

        @Override
        public long getCapacityAsLong() {
            return energyBufferSize();
        }
    }
}
