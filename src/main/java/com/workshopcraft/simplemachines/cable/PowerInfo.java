package com.workshopcraft.simplemachines.cable;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.gen.SolarPanelBlockEntity;
import com.workshopcraft.simplemachines.gen.WindTurbineBlockEntity;
import com.workshopcraft.simplemachines.machine.AlloySmelterBlockEntity;
import com.workshopcraft.simplemachines.machine.AutoCrafterBlockEntity;
import com.workshopcraft.simplemachines.machine.BatteryBlockEntity;
import com.workshopcraft.simplemachines.machine.ChargerBlockEntity;
import com.workshopcraft.simplemachines.machine.ElectricFurnaceBlockEntity;
import com.workshopcraft.simplemachines.machine.GeneratorBlockEntity;
import com.workshopcraft.simplemachines.machine.HarvesterBlockEntity;
import com.workshopcraft.simplemachines.machine.LavaGeneratorBlockEntity;
import com.workshopcraft.simplemachines.machine.ProcessBlockEntity;
import com.workshopcraft.simplemachines.machine.RollingMillBlockEntity;
import com.workshopcraft.simplemachines.machine.ShearerBlockEntity;
import com.workshopcraft.simplemachines.machine.SourceBlockEntity;
import com.workshopcraft.simplemachines.quarry.QuarryBlockEntity;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What one of our blocks takes and gives, for Voltage, item tooltips and screens. Numbers come from the block entity constants.
 * use and steelUse are FE per unit; out is the most it pushes per tick; chassis means capacity and out scale with the chassis tier.
 */
public record PowerInfo(int accepts, int sends, int out, int use, int steelUse, int night, Unit unit, int capacity, boolean chassis) {
    public enum Unit { NONE, RUNNING, BLOCK, SHEAR, CROP, CHARGE, BURNING, SUN, BLADE, TRANSFORMER, CABLE, WRITE, RELAY, TELEPORT }

    private static Map<Block, PowerInfo> table;

    public static @Nullable PowerInfo of(Block block) {
        if (table == null) table = build();
        return table.get(block);
    }

    /** The tier shown on a machine screen: what it takes in, or what it sends when it only sends. */
    public static int screenTier(Block block) {
        final PowerInfo p = of(block);
        return p == null ? -1 : p.accepts >= 0 ? p.accepts : p.sends;
    }

    private static PowerInfo user(int use, Unit unit, int capacity) {
        return new PowerInfo(Voltage.LV, -1, 0, use, use, 0, unit, capacity, true);
    }

    private static Map<Block, PowerInfo> build() {
        final Map<Block, PowerInfo> m = new IdentityHashMap<>();
        final Map<Core, Block> mc = SimpleMachines.MACHINES;
        m.put(mc.get(Core.FURNACE), user(ElectricFurnaceBlockEntity.PER_TICK, Unit.RUNNING, ElectricFurnaceBlockEntity.CAPACITY));
        m.put(mc.get(Core.ROLLING), user(RollingMillBlockEntity.PER_TICK, Unit.RUNNING, RollingMillBlockEntity.CAPACITY));
        m.put(mc.get(Core.ALLOY), user(AlloySmelterBlockEntity.PER_TICK, Unit.RUNNING, AlloySmelterBlockEntity.CAPACITY));
        m.put(mc.get(Core.CRUSHER), user(ProcessBlockEntity.PER_TICK, Unit.RUNNING, ProcessBlockEntity.CAPACITY));
        m.put(mc.get(Core.SAW), user(ProcessBlockEntity.PER_TICK, Unit.RUNNING, ProcessBlockEntity.CAPACITY));
        m.put(mc.get(Core.COMPRESSOR), user(ProcessBlockEntity.COMPRESSOR_PER_TICK, Unit.RUNNING, ProcessBlockEntity.COMPRESSOR_CAPACITY));
        m.put(mc.get(Core.CRAFTER), user(AutoCrafterBlockEntity.PER_TICK, Unit.RUNNING, AutoCrafterBlockEntity.CAPACITY));
        m.put(mc.get(Core.STONE), user(SourceBlockEntity.Kind.STONE.perTick, Unit.RUNNING, SourceBlockEntity.CAPACITY));
        m.put(mc.get(Core.WATER), user(SourceBlockEntity.Kind.WATER.perTick, Unit.RUNNING, SourceBlockEntity.CAPACITY));
        m.put(mc.get(Core.HIVE), user(SourceBlockEntity.Kind.HIVE.perTick, Unit.RUNNING, SourceBlockEntity.CAPACITY));
        m.put(mc.get(Core.SHEARER), user(ShearerBlockEntity.PER_SHEAR, Unit.SHEAR, ShearerBlockEntity.CAPACITY));
        m.put(mc.get(Core.HARVESTER), user(HarvesterBlockEntity.PER_CROP, Unit.CROP, HarvesterBlockEntity.CAPACITY));
        m.put(mc.get(Core.PROGRAMMER), user(com.workshopcraft.simplemachines.entangled.EntangledProgrammerBlockEntity.PER_WRITE, Unit.WRITE,
                com.workshopcraft.simplemachines.entangled.EntangledProgrammerBlockEntity.CAPACITY));
        for (Core c : new Core[]{Core.BUFFER, Core.TANK}) m.put(mc.get(c), user(com.workshopcraft.simplemachines.entangled.EntangledDeviceBlockEntity.UPKEEP,
                Unit.RUNNING, com.workshopcraft.simplemachines.entangled.EntangledDeviceBlockEntity.CAPACITY));
        for (int v = 0; v < Core.RELAYS.length; v++) m.put(mc.get(Core.RELAYS[v]), new PowerInfo(v, v, Voltage.RATE[v],
                com.workshopcraft.simplemachines.entangled.EntangledDeviceBlockEntity.UPKEEP, com.workshopcraft.simplemachines.entangled.EntangledDeviceBlockEntity.UPKEEP,
                0, Unit.RELAY, com.workshopcraft.simplemachines.entangled.EntangledRelayBlockEntity.CAPACITY[v], true));
        m.put(mc.get(Core.PAD), new PowerInfo(Voltage.MV, -1, 0, com.workshopcraft.simplemachines.entangled.EntangledPadBlockEntity.BASE,
                com.workshopcraft.simplemachines.entangled.EntangledPadBlockEntity.PER_BLOCK, 0, Unit.TELEPORT,
                com.workshopcraft.simplemachines.entangled.EntangledPadBlockEntity.CAPACITY, true));
        m.put(mc.get(Core.CHARGER), user(ChargerBlockEntity.RATE, Unit.CHARGE, ChargerBlockEntity.CAPACITY));
        m.put(mc.get(Core.QUARRY), new PowerInfo(Voltage.MV, -1, 0, QuarryBlockEntity.PER_BLOCK, QuarryBlockEntity.PER_BLOCK, 0, Unit.BLOCK,
                QuarryBlockEntity.BUFFER, true));
        m.put(mc.get(Core.BATTERY), new PowerInfo(Voltage.LV, Voltage.LV, BatteryBlockEntity.RATE, 0, 0, 0, Unit.NONE, BatteryBlockEntity.CAPACITY, true));
        m.put(mc.get(Core.GENERATOR), new PowerInfo(-1, Voltage.LV, GeneratorBlockEntity.MAX_OUT, GeneratorBlockEntity.PER_TICK,
                GeneratorBlockEntity.STEEL_PER_TICK, 0, Unit.BURNING, GeneratorBlockEntity.CAPACITY, true));
        m.put(mc.get(Core.LAVA), new PowerInfo(-1, Voltage.LV, LavaGeneratorBlockEntity.MAX_OUT, LavaGeneratorBlockEntity.PER_TICK,
                LavaGeneratorBlockEntity.STEEL_PER_TICK, 0, Unit.BURNING, LavaGeneratorBlockEntity.CAPACITY, true));
        for (int i = 0; i < SimpleMachines.SOLAR_PANELS.size(); i++) {
            m.put(SimpleMachines.SOLAR_PANELS.get(i), new PowerInfo(-1, SolarPanelBlockEntity.VOLTAGE[i], SolarPanelBlockEntity.OUT[i],
                    SolarPanelBlockEntity.DAY[i], SolarPanelBlockEntity.DAY[i], SolarPanelBlockEntity.NIGHT[i], Unit.SUN, SolarPanelBlockEntity.BUFFER[i], false));
        }
        m.put(SimpleMachines.windTurbine, new PowerInfo(-1, Voltage.LV, WindTurbineBlockEntity.OUT, WindTurbineBlockEntity.MAX, WindTurbineBlockEntity.MAX, 0,
                Unit.BLADE, WindTurbineBlockEntity.CAPACITY, false));
        for (Block b : new Block[]{SimpleMachines.lvMvTransformer, SimpleMachines.mvHvTransformer}) {
            final int high = ((TransformerBlock) b).high;
            final int rate = TransformerBlockEntity.rate(high);
            m.put(b, new PowerInfo(high - 1, high, rate, rate, rate, 0, Unit.TRANSFORMER, rate * 10, false));
        }
        for (Block b : SimpleMachines.CABLES) {
            final int t = ((CableBlock) b).tier;
            m.put(b, new PowerInfo(t, t, Voltage.RATE[t], 0, 0, 0, Unit.CABLE, 0, false));
        }
        //? if portal {
        com.workshopcraft.simplemachines.portal.Portal.powerInfo(m);
        //?}
        m.remove(null);
        return m;
    }
}
