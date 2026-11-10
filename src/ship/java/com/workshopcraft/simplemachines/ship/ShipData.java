package com.workshopcraft.simplemachines.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.workshopcraft.simplemachines.SimpleMachines;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World-wide ship state: area states for locked doors, start cryopods and the occupant list position. */
public class ShipData extends SavedData {
    public static final Codec<ShipData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("areas", Map.of()).forGetter(d -> d.areas),
            GlobalPos.CODEC.listOf().optionalFieldOf("start_pods", List.of()).forGetter(d -> d.startPods),
            Codec.INT.optionalFieldOf("next_occupant", 0).forGetter(d -> d.nextOccupant)
    ).apply(i, ShipData::new));
    public static final SavedDataType<ShipData> TYPE = new SavedDataType<>(SimpleMachines.id("ship"), ShipData::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    final Map<String, String> areas = new LinkedHashMap<>();
    final List<GlobalPos> startPods = new ArrayList<>();
    int nextOccupant;

    public ShipData() {
    }

    private ShipData(Map<String, String> areas, List<GlobalPos> pods, int next) {
        this.areas.putAll(areas);
        this.startPods.addAll(pods);
        this.nextOccupant = next;
    }

    public static ShipData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public String area(String key) {
        return areas.getOrDefault(key, "");
    }

    public void setArea(String key, String state) {
        if (state == null || state.isEmpty()) areas.remove(key);
        else areas.put(key, state);
        setDirty();
    }

    public Map<String, String> areas() {
        return Map.copyOf(areas);
    }

    public List<GlobalPos> startPods() {
        return List.copyOf(startPods);
    }

    public void addStartPod(GlobalPos pos) {
        if (!startPods.contains(pos)) {
            startPods.add(pos);
            setDirty();
        }
    }

    public void removeStartPod(GlobalPos pos) {
        if (startPods.remove(pos)) setDirty();
    }

    int takeOccupant() {
        setDirty();
        return nextOccupant++;
    }
}
