package com.workshopcraft.simplemachines.portal.block;

import com.mojang.serialization.Codec;
import com.workshopcraft.simplemachines.assembly.Tier;
import com.workshopcraft.simplemachines.cable.Voltage;
import com.workshopcraft.simplemachines.machine.EnergyMenu;
import com.workshopcraft.simplemachines.machine.MachineBlock;
import com.workshopcraft.simplemachines.machine.WorkBlockEntity;
import com.workshopcraft.simplemachines.portal.FrameTier;
import com.workshopcraft.simplemachines.portal.GateFrame;
import com.workshopcraft.simplemachines.portal.GateMenu;
import com.workshopcraft.simplemachines.portal.Portal;
import com.workshopcraft.simplemachines.portal.PortalApi;
import com.workshopcraft.simplemachines.portal.PortalCard;
import com.workshopcraft.simplemachines.portal.PortalConfig;
import com.workshopcraft.simplemachines.portal.PortalData;
import com.workshopcraft.simplemachines.portal.PortalResources;
import com.workshopcraft.simplemachines.portal.PortalRifts;
import com.workshopcraft.simplemachines.portal.PortalTravel;
import com.workshopcraft.simplemachines.portal.world.PortalWorlds;
import com.workshopcraft.simplemachines.portal.world.WorldPreset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Portal Gate Controller: an HV machine in the bottom edge of a gate frame.
 * Slots: Entangled Core, card, three pellet slots. Open takes pellets and FE, makes the target world and fills the
 * opening with a portal field for portal_open_seconds.
 * Jump cards bind: the first jump makes the card's world and binds the card to it (the item's simplemachines:portal_world,
 * or this gate's hidden binding map for hidden cards). A bound card always links back to that world and never makes another.
 * Story cards are a do-over: until the objective is collected every jump makes a new attempt world (same preset, new seed)
 * and the last attempt is marked superseded. After collection a kept story card goes back to the finished world.
 * Closed mode (brief A7; per gate, or every gate with portal_player_programming off): no card slot, only pellets and
 * energy go in, and the destination comes from a hidden card set by the API, a command or a quest.
 * Star systems: once a gate has arrived in a system, each hidden jump card is one planet of that system, the same world
 * on every jump, until the gate departs. Departed means in transit: the gate will not open until it arrives somewhere.
 * A player who comes out on the far side opens a return rift there (PortalRifts), paid for from this gate while it is open.
 */
public class GateControllerBlockEntity extends WorkBlockEntity {
    public static final int CAPACITY = 32_000_000;
    public static final int MAX_IN = Voltage.RATE[Voltage.HV];
    public static final int UPKEEP = 0;
    public static final int SLOT_CORE = 0, SLOT_CARD = 1, FIRST_PELLET = 2, SLOTS = 5;
    public static final int DATA_COUNT = 13;

    public static final int OK = 0, OPEN = 1, NO_FRAME = 2, NO_CORE = 3, NO_CARD = 4, WEAK_FRAME = 5, NO_PELLETS = 6, NO_ENERGY = 7,
            DISABLED = 8, LOCKED = 9, STARTING = 10, NO_WORLD = 11, UNDERFED = 12, NO_TARGET = 13, IN_TRANSIT = 14;
    public static final String[] STATUS = {"ready", "open", "no_frame", "no_core", "no_card", "weak_frame", "no_pellets", "no_energy",
            "disabled", "locked", "starting", "no_world", "underfed", "no_target", "in_transit"};
    /** Ticks the gate stays browned out after the last low voltage feed, and between brownout hits. */
    private static final int UNDERFED_TICKS = 100, BROWNOUT_COOLDOWN = 40;

    private @Nullable Identifier hiddenCard;
    private List<String> hiddenForced = List.of();
    /**
     * Star systems (Homebound): the system the gate's ship is in, null when it has none. inTransit is between depart and
     * arrive. While in a system each hidden jump card resolves to one planet (systemWorlds), the same on every jump.
     */
    /** Whose research the gate uses for world types (the placer; an older gate is claimed by the first player to use it). */
    private java.util.@Nullable UUID owner;
    private @Nullable String system;
    private boolean inTransit;
    private final java.util.Map<String, String> systemWorlds = new java.util.LinkedHashMap<>();
    private long riftToken;
    /** Hidden card id to the world it is bound to, so applying the same hidden card again goes back to the same world. */
    private final java.util.Map<String, String> hiddenBindings = new java.util.LinkedHashMap<>();
    /** Story card id to its latest attempt world from this gate. */
    private final java.util.Map<String, String> storyAttempts = new java.util.LinkedHashMap<>();
    private boolean enabled = true;
    private boolean closed;
    private @Nullable String worldId;
    private @Nullable ResourceKey<Level> target;
    private @Nullable Identifier nextPreset;
    private @Nullable Identifier rolledFor;
    private int openTicks, openTotal;
    private boolean starting;
    private @Nullable GateFrame frame;
    private int status = NO_FRAME;
    private int needPellets, needEnergy;
    private int scan;

    private final ContainerData gateData = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case 0 -> openTicks;
                case 1 -> openTotal;
                case 2 -> energy.energy & 0xFFFF;
                case 3 -> energy.energy >>> 16;
                case 4 -> energy.capacity / 1000;
                case 5 -> status;
                case 6 -> EnergyMenu.packTier(getBlockState().getBlock());
                case 7 -> needPellets;
                case 8 -> needEnergy / 1000;
                case 9 -> frame == null ? 0 : frame.tier().level;
                case 10 -> frame == null ? 0 : frame.width() << 8 | frame.height();
                case 11 -> flags();
                case 12 -> riftSeconds();
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    /** Bits for the screen: 1 enabled, 2 closed mode, 4 players may open, 8 rift open, 16 rift flickering. */
    private int flags() {
        int f = (enabled ? 1 : 0) | (closedMode() ? 2 : 0) | (playersMayOpen() ? 4 : 0);
        if (level instanceof ServerLevel s) {
            final PortalData.Rift r = PortalRifts.of(s, worldPosition);
            if (r != null) f |= 8 | (r.low ? 16 : 0);
        }
        return f;
    }

    private int riftSeconds() {
        if (!(level instanceof ServerLevel s)) return 0;
        final PortalData.Rift r = PortalRifts.of(s, worldPosition);
        return r == null || r.secondsLeft < 0 ? 0xFFFF : Math.min(0xFFFE, r.secondsLeft);
    }

    public GateControllerBlockEntity(BlockPos pos, BlockState state) {
        super(Portal.GATE_ENTITY, pos, state, SLOTS, CAPACITY, MAX_IN, 0);
    }

    @Override
    protected boolean isOutput(int slot) {
        return false;
    }

    @Override
    protected boolean accepts(ServerLevel level, int slot, ItemStack stack) {
        if (slot == SLOT_CARD && closedMode()) return false;
        return valid(slot, stack);
    }

    public static boolean valid(int slot, ItemStack stack) {
        if (slot == SLOT_CORE) return stack.is(Portal.entangledCore);
        if (slot == SLOT_CARD) return stack.has(Portal.CARD);
        return stack.is(Portal.entangledPellet);
    }

    @Override
    public boolean energyOut(@Nullable Direction side) {
        return false;
    }

    // --- HV only ---
    private int lowSides;
    private int underfed;
    private int brownoutCooldown;

    /**
     * The gate takes HV only. Asked by the energy handler on every insert: true when the neighbour on side sends HV
     * (an HV cable, the HV side of an MV/HV transformer, an HV relay; the same Voltage.sending rule the quarry uses
     * for its MV only input). Anything lower, and anything not from this mod (which counts as LV), is refused and
     * noted, so the next tick browns the gate out. A null side is direct access (commands, tests) and is allowed.
     */
    public boolean acceptsFrom(@Nullable Direction side) {
        if (side == null || level == null) return true;
        if (Voltage.sending(level, worldPosition.relative(side), side.getOpposite()) >= Voltage.HV) return true;
        lowSides |= 1 << side.ordinal();
        return false;
    }

    public boolean underfed() {
        return underfed > 0;
    }

    /** A low voltage feed: sparks and a buzz, a share of the stored energy lost, and LV/MV cables on those sides burn out. */
    private void brownout(ServerLevel level, BlockPos pos, int sides) {
        final boolean damage = PortalConfig.bool(level.getServer(), PortalConfig.LOW_VOLTAGE_DAMAGE);
        if (openTicks > 0) close(level);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.5F);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.REDSTONE_TORCH_BURNOUT, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 0.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                24, 0.5, 0.4, 0.5, 0.2);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.02);
        if (!damage) return;
        final int loss = energy.energy * Math.clamp(PortalConfig.integer(level.getServer(), PortalConfig.BROWNOUT_LOSS), 0, 100) / 100;
        if (loss > 0) {
            energy.energy -= loss;
            setChanged();
        }
        for (Direction d : Direction.values()) {
            if ((sides & 1 << d.ordinal()) == 0) continue;
            final BlockPos at = pos.relative(d);
            final BlockState s = level.getBlockState(at);
            if (s.getBlock() instanceof com.workshopcraft.simplemachines.cable.CableBlock cable && cable.tier < Voltage.HV) burnOut(level, at, d);
        }
    }

    /** An LV or MV cable feeding the gate overloads: it is gone (no drop), with smoke, a fizz and a small fire where it was. */
    private static void burnOut(ServerLevel level, BlockPos at, Direction fromGate) {
        level.removeBlock(at, false);
        level.playSound(null, at, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.4F);
        level.playSound(null, at, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 16, 0.25, 0.25, 0.25, 0.03);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
        // a small fire where the cable was, or on top of the block under it
        if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, at, fromGate)) {
            level.setBlockAndUpdate(at, net.minecraft.world.level.block.BaseFireBlock.getState(level, at));
        }
    }

    @Override
    public Tier tier() {
        return Tier.IRON;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        energy.capacity = CAPACITY;
        energy.maxIn = MAX_IN;
        energy.maxOut = 0;
        if (energy.energy > energy.capacity) energy.energy = energy.capacity;
        work(level, pos, state, Tier.IRON);
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state, Tier tier) {
        if (brownoutCooldown > 0) brownoutCooldown--;
        if (lowSides != 0) {
            final int sides = lowSides;
            lowSides = 0;
            final boolean was = underfed > 0;
            underfed = UNDERFED_TICKS;
            if (brownoutCooldown == 0) {
                brownoutCooldown = BROWNOUT_COOLDOWN;
                brownout(level, pos, sides);
            }
            if (!was) refreshStatus(level);
        } else if (underfed > 0 && --underfed == 0) {
            refreshStatus(level);
        }
        if (++scan >= 10 || frame == null && scan >= 5) {
            scan = 0;
            final GateFrame f = GateFrame.find(level, pos, Math.max(2, PortalConfig.integer(level.getServer(), PortalConfig.MAX_OPENING)));
            if (openTicks > 0 && (f == null || !f.equals(frame))) close(level);
            frame = f;
            refreshStatus(level);
        }
        if (openTicks > 0) {
            if (!starting) {
                openTicks--;
                if (openTicks % 20 == 0) placeField(level);
            }
            if (openTicks <= 0 || !enabled) close(level);
        }
        PortalRifts.tickGate(level, this);
        final boolean lit = openTicks > 0 || PortalRifts.of(level, pos) != null;
        if (state.getValue(MachineBlock.LIT) != lit) level.setBlock(pos, state.setValue(MachineBlock.LIT, lit), 3);
        if (level.getGameTime() % 20 == 0) setChanged();
    }

    // --- card and target ---

    /** The hidden card wins; in closed mode it is the only one, and a card item left in the slot is ignored. */
    public @Nullable Identifier activeCard() {
        if (hiddenCard != null || closedMode()) return hiddenCard;
        return items.get(SLOT_CARD).get(Portal.CARD);
    }

    /** Whether the next jump uses a planet of the current star system: a hidden jump card on a gate that uses systems. */
    public boolean systemWorld() {
        if (hiddenCard == null || system == null || !(level instanceof ServerLevel s)) return false;
        final PortalCard def = PortalResources.cards(s.getServer()).get(hiddenCard);
        return def != null && def.kind() == PortalCard.Kind.JUMP;
    }

    /** The world the active card is bound to, or null for a card that has not made one yet. */
    public @Nullable String activeBound() {
        if (systemWorld()) return systemWorlds.get(hiddenCard.toString());
        final Identifier card = activeCard();
        if (card != null && level instanceof ServerLevel s) {
            final PortalCard def = PortalResources.cards(s.getServer()).get(card);
            if (def != null && def.kind() == PortalCard.Kind.STORY) {
                // story cards only go back to an attempt whose objective is done
                final String last = storyAttempts.get(card.toString());
                final PortalData.World w = last == null ? null : PortalData.get(s.getServer()).world(last);
                return w != null && w.collected ? last : null;
            }
        }
        if (hiddenCard != null) return hiddenBindings.get(hiddenCard.toString());
        return items.get(SLOT_CARD).get(Portal.BOUND);
    }

    private void bind(PortalCard card, String world) {
        if (systemWorld()) {
            systemWorlds.put(hiddenCard.toString(), world);
            setChanged();
            return;
        }
        if (card.kind() == PortalCard.Kind.STORY && level instanceof ServerLevel s) {
            final String last = storyAttempts.put(card.id().toString(), world);
            final PortalData data = PortalData.get(s.getServer());
            final PortalData.World old = last == null ? null : data.world(last);
            if (old != null && !old.collected) {
                old.superseded = true;
                data.setDirty();
            }
            setChanged();
            return;
        }
        if (hiddenCard != null) hiddenBindings.put(hiddenCard.toString(), world);
        else items.get(SLOT_CARD).set(Portal.BOUND, world);
        setChanged();
    }

    public java.util.Map<String, String> hiddenBindings() {
        return java.util.Map.copyOf(hiddenBindings);
    }

    public boolean hiddenCardActive() {
        return hiddenCard != null;
    }

    public List<String> activeForced() {
        if (hiddenCard != null) return hiddenForced;
        final List<String> f = items.get(SLOT_CARD).get(Portal.FORCED);
        return f == null ? List.of() : f;
    }

    public void setHiddenCard(@Nullable Identifier card, List<String> forced) {
        hiddenCard = card;
        hiddenForced = card == null ? List.of() : List.copyOf(forced);
        nextPreset = null;
        rolledFor = null;
        setChanged();
        if (level instanceof ServerLevel s) refreshStatus(s);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on && level instanceof ServerLevel s) {
            close(s);
            dropRift(s, PortalRifts.Reason.DROPPED);
        }
        setChanged();
    }

    /** This gate's own closed mode flag (API or command). See closedMode for the one that counts. */
    public boolean closed() {
        return closed;
    }

    /** Closed mode: set on this gate, or on every gate when portal_player_programming is off. */
    public boolean closedMode() {
        return closed || level != null && level.getServer() != null && !PortalConfig.bool(level.getServer(), PortalConfig.PLAYER_PROGRAMMING);
    }

    /** Whether a player may press Open. Always outside closed mode; in closed mode only with portal_closed_mode_player_open. */
    public boolean playersMayOpen() {
        return !closedMode() || level != null && PortalConfig.bool(level.getServer(), PortalConfig.CLOSED_PLAYER_OPEN);
    }

    public long riftToken() {
        return riftToken;
    }

    public void setRiftToken(long token) {
        riftToken = token;
        setChanged();
    }

    public boolean riftOpen() {
        return level instanceof ServerLevel s && PortalRifts.of(s, worldPosition) != null;
    }

    public boolean dropRift(ServerLevel level, PortalRifts.Reason reason) {
        final PortalData.Rift r = PortalRifts.of(level, worldPosition);
        if (r == null) return false;
        PortalRifts.collapse(level.getServer(), r, reason);
        return true;
    }

    // --- owner: research (world types) is per player, so the gate uses its owner's ---

    public java.util.@Nullable UUID owner() {
        return owner;
    }

    public void setOwner(java.util.@Nullable UUID who) {
        owner = who;
        nextPreset = null;
        setChanged();
    }

    /** A gate from before per-player research belongs to the first player who opens its screen or starts a jump. */
    public void claim(ServerPlayer player) {
        if (owner != null) return;
        setOwner(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.simplemachines.portal.gate_claimed"));
    }

    @Override
    public boolean canOpen(net.minecraft.world.entity.player.Player player) {
        if (owner == null && player instanceof ServerPlayer sp) claim(sp);
        return super.canOpen(player);
    }

    // --- star systems ---

    public @Nullable String system() {
        return system;
    }

    public boolean inTransit() {
        return inTransit;
    }

    public java.util.Map<String, String> systemWorlds() {
        return java.util.Map.copyOf(systemWorlds);
    }

    /**
     * The ship leaves its system. Refused (returns a translation key) while someone is still on the far side of the
     * rift, unless force: then the rift collapses and they are stranded. The field closes, the system's planets are
     * retired (deleted once empty) and the gate will not open until arrive.
     */
    public @Nullable String depart(boolean force) {
        if (!(level instanceof ServerLevel s)) return "message.simplemachines.portal.no_gate";
        final PortalData.Rift r = PortalRifts.of(s, worldPosition);
        if (r != null && !force && PortalRifts.away(s.getServer(), r) > 0) return "message.simplemachines.portal.players_away";
        final String old = system;
        if (r != null) PortalRifts.collapse(s.getServer(), r, PortalRifts.Reason.DEPARTED);
        close(s);
        retireSystem(s.getServer());
        system = null;
        inTransit = true;
        status = IN_TRANSIT;
        setChanged();
        PortalApi.fireSystem(GlobalPos.of(s.dimension(), worldPosition), old, false);
        return null;
    }

    /** The ship arrives in a system. Arriving straight from another system leaves that one first (forced). */
    public void arrive(String id) {
        if (!(level instanceof ServerLevel s)) return;
        if (id.equals(system) && !inTransit) return;
        if (system != null) depart(true);
        system = id;
        inTransit = false;
        systemWorlds.clear();
        setChanged();
        refreshStatus(s);
        PortalApi.fireSystem(GlobalPos.of(s.dimension(), worldPosition), id, true);
    }

    /** Marks the planets of the current system as left behind; cleanup deletes each once nobody is in it. */
    private void retireSystem(MinecraftServer server) {
        final PortalData data = PortalData.get(server);
        final GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        for (PortalData.World w : data.worlds()) {
            if (w.oneOff && !w.retired && (systemWorlds.containsValue(w.id) || system != null && system.equals(w.system) && here.equals(w.gate))) {
                w.retired = true;
                data.setDirty();
            }
        }
        systemWorlds.clear();
    }

    /** Holds a rift open beside the landing spot of the world this gate last opened, without anyone travelling. */
    public @Nullable String holdRift() {
        if (!(level instanceof ServerLevel s)) return "message.simplemachines.portal.no_gate";
        final MinecraftServer ms = s.getServer();
        final ServerLevel far = target == null ? null : ms.getLevel(target);
        if (far == null) return "message.simplemachines.portal.rift_no_world";
        final PortalData.World w = worldId == null ? null : PortalData.get(ms).world(worldId);
        final BlockPos landing = PortalTravel.landing(far, w, worldPosition, s);
        PortalRifts.open(ms, GlobalPos.of(s.dimension(), worldPosition), far, landing, worldId, true);
        return null;
    }

    /** Takes FE from the buffer for the rift. False when there is not enough. */
    public boolean drain(int fe) {
        if (energy.energy < fe) return false;
        energy.energy -= fe;
        setChanged();
        return true;
    }

    public int pellets() {
        int n = 0;
        for (int i = FIRST_PELLET; i < SLOTS; i++) if (items.get(i).is(Portal.entangledPellet)) n += items.get(i).getCount();
        return n;
    }

    public boolean takePellet() {
        for (int i = FIRST_PELLET; i < SLOTS; i++) {
            final ItemStack s = items.get(i);
            if (s.is(Portal.entangledPellet)) {
                s.shrink(1);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void setClosed(boolean on) {
        closed = on;
        setChanged();
    }

    public @Nullable String worldId() {
        return worldId;
    }

    public @Nullable ResourceKey<Level> target() {
        return openTicks > 0 && !starting ? target : null;
    }

    public @Nullable GateFrame frame() {
        return frame;
    }

    public ContainerData gateData() {
        return gateData;
    }

    /** The world type the next jump will make: fixed for story cards, rolled once per jump for jump cards. */
    public @Nullable WorldPreset nextPreset(ServerLevel level) {
        final MinecraftServer server = level.getServer();
        final Identifier cardId = activeCard();
        final PortalCard card = cardId == null ? null : PortalResources.cards(server).get(cardId);
        if (card == null) return null;
        if (card.kind() != PortalCard.Kind.JUMP && card.kind() != PortalCard.Kind.STORY) return null;
        final String bound = activeBound();
        final PortalData.World bw = bound == null ? null : PortalData.get(server).world(bound);
        if (bw != null) return PortalResources.presets(server).get(bw.preset);
        if (card.kind() == PortalCard.Kind.STORY) return card.preset() == null ? null : PortalResources.presets(server).get(card.preset());
        for (String f : activeForced()) {
            // a card programmed with a world type always makes that type
            if (f.startsWith("preset:")) {
                final Identifier pid = Identifier.tryParse(f.substring(7));
                if (pid != null && PortalResources.presets(server).containsKey(pid)) return PortalResources.presets(server).get(pid);
            }
        }
        if (nextPreset == null || !cardId.equals(rolledFor) || !PortalResources.presets(server).containsKey(nextPreset)) {
            final FrameTier ft = frame == null ? FrameTier.NETHERITE : frame.tier();
            final PortalData data = PortalData.get(server);
            nextPreset = card.roll(level.getRandom(), PortalResources.presets(server),
                    p -> ft.reaches(p.minFrame()) && (!p.needsResearch() || data.unlocked(owner, "preset:" + p.id())));
            rolledFor = cardId;
            setChanged();
        }
        return nextPreset == null ? null : PortalResources.presets(server).get(nextPreset);
    }

    private void refreshStatus(ServerLevel level) {
        if (openTicks > 0) {
            status = starting ? STARTING : OPEN;
            return;
        }
        status = check(level, null, false);
    }

    /** Works out whether a jump can start; with take, also takes the pellets and FE. */
    private int check(ServerLevel level, @Nullable ServerPlayer player, boolean take) {
        final MinecraftServer server = level.getServer();
        needPellets = 0;
        needEnergy = 0;
        if (!enabled) return DISABLED;
        if (inTransit) return IN_TRANSIT;
        if (underfed > 0) return UNDERFED;
        if (frame == null) return NO_FRAME;
        if (!items.get(SLOT_CORE).is(Portal.entangledCore)) return NO_CORE;
        final Identifier cardId = activeCard();
        final PortalCard card = cardId == null ? null : PortalResources.cards(server).get(cardId);
        if (card == null) return closedMode() ? NO_TARGET : NO_CARD;
        final PortalApi.JumpCost cost;
        FrameTier need = card.minFrame() == null ? FrameTier.IRON : card.minFrame();
        if (card.kind() == PortalCard.Kind.NETHER || card.kind() == PortalCard.Kind.END) {
            cost = PortalApi.cost(server, card.kind() == PortalCard.Kind.END ? 3 : 1, 1, frame.area());
            if (card.kind() == PortalCard.Kind.END && need.level < FrameTier.STEEL.level) need = FrameTier.STEEL;
        } else {
            final WorldPreset p = nextPreset(level);
            if (p == null) return NO_WORLD;
            if (p.minFrame().level > need.level) need = p.minFrame();
            final boolean creative = player != null && player.isCreative();
            if (p.needsResearch() && !creative && !PortalData.get(server).unlocked(owner, "preset:" + p.id())) return LOCKED;
            cost = PortalApi.jumpCost(server, p, frame.area());
        }
        needPellets = cost.pellets();
        needEnergy = cost.energy();
        if (!frame.tier().reaches(need)) return WEAK_FRAME;
        return pay(cost, take);
    }

    private int pay(PortalApi.JumpCost cost, boolean take) {
        if (pellets() < cost.pellets()) return NO_PELLETS;
        if (energy.energy < cost.energy()) return NO_ENERGY;
        if (take) {
            int left = cost.pellets();
            for (int i = FIRST_PELLET; i < SLOTS && left > 0; i++) {
                final ItemStack s = items.get(i);
                if (!s.is(Portal.entangledPellet)) continue;
                final int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
            energy.energy -= cost.energy();
            setChanged();
        }
        return OK;
    }

    /** Starts a jump. Returns a translation key for the problem, or null when it started. */
    public @Nullable String tryOpen(@Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return "message.simplemachines.portal.no_gate";
        if (owner == null && player != null) claim(player);
        if (openTicks > 0) return "message.simplemachines.portal.already_open";
        frame = GateFrame.find(server, worldPosition, Math.max(2, PortalConfig.integer(server.getServer(), PortalConfig.MAX_OPENING)));
        final PortalData.Rift rift = PortalRifts.of(server, worldPosition);
        if (rift != null) return reopen(server, rift);
        final int result = check(server, player, true);
        if (result != OK) {
            status = result;
            return "gui.simplemachines.portal.status." + STATUS[result];
        }
        final MinecraftServer ms = server.getServer();
        final PortalCard card = PortalResources.cards(ms).get(activeCard());
        openTotal = Math.max(5, PortalConfig.integer(ms, PortalConfig.OPEN_SECONDS)) * 20;
        openTicks = openTotal;
        final GlobalPos here = GlobalPos.of(server.dimension(), worldPosition);
        if (card.kind() == PortalCard.Kind.NETHER) {
            target = Level.NETHER;
            worldId = null;
            placeField(server);
        } else if (card.kind() == PortalCard.Kind.END) {
            target = Level.END;
            worldId = null;
            placeField(server);
        } else {
            final WorldPreset preset = nextPreset(server);
            final PortalData data = PortalData.get(ms);
            final String bound = activeBound();
            PortalData.World w = bound == null ? null : data.world(bound);
            if (w == null) {
                w = new PortalData.World(data.newId(), preset.id(), server.getRandom().nextLong(), card.id(),
                        new ArrayList<>(activeForced()), here);
                w.hiddenCard = hiddenCard != null;
                if (systemWorld()) {
                    w.oneOff = true;
                    w.system = system;
                }
                data.put(w);
                bind(card, w.id);
            }
            final PortalData.World world = w;
            worldId = world.id;
            target = PortalWorlds.key(world.id);
            nextPreset = null;
            if (PortalWorlds.level(ms, world.id) != null) {
                placeField(server);
            } else {
                starting = true;
                PortalWorlds.later(s -> {
                    try {
                        PortalWorlds.load(s, world);
                        starting = false;
                        placeField(server);
                    } catch (Exception e) {
                        com.mojang.logging.LogUtils.getLogger().error("Simple Machines could not make portal world {}", world.id, e);
                        starting = false;
                        close(server);
                    }
                });
            }
        }
        status = starting ? STARTING : OPEN;
        setChanged();
        return null;
    }

    /**
     * While a rift is open the gate only opens back to the rift's world, for the same price as a jump to it, so more
     * players can follow. A new destination needs the rift closed first.
     */
    private @Nullable String reopen(ServerLevel server, PortalData.Rift rift) {
        int result = !enabled ? DISABLED : inTransit ? IN_TRANSIT : underfed > 0 ? UNDERFED : frame == null ? NO_FRAME
                : !items.get(SLOT_CORE).is(Portal.entangledCore) ? NO_CORE : OK;
        if (result == OK) {
            final PortalApi.JumpCost cost = PortalApi.cost(server.getServer(), rift.tier, 1, frame.area());
            needPellets = cost.pellets();
            needEnergy = cost.energy();
            result = pay(cost, true);
        }
        if (result != OK) {
            status = result;
            return "gui.simplemachines.portal.status." + STATUS[result];
        }
        target = rift.level;
        worldId = rift.world;
        openTotal = Math.max(5, PortalConfig.integer(server.getServer(), PortalConfig.OPEN_SECONDS)) * 20;
        openTicks = openTotal;
        placeField(server);
        status = OPEN;
        setChanged();
        return null;
    }

    public void close(ServerLevel level) {
        if (frame != null) {
            for (BlockPos p : frame.opening()) if (level.getBlockState(p).getBlock() instanceof PortalFieldBlock) level.removeBlock(p, false);
        }
        PortalTravel.deactivate(level, worldPosition);
        openTicks = 0;
        starting = false;
        status = OK;
        setChanged();
    }

    private void placeField(ServerLevel level) {
        if (frame == null) return;
        final BlockState field = Portal.portalField.defaultBlockState().setValue(PortalFieldBlock.AXIS, frame.axis());
        for (BlockPos p : frame.opening()) {
            final BlockState s = level.getBlockState(p);
            if (s.isAir()) level.setBlock(p, field, 2);
        }
        PortalTravel.activate(level, worldPosition, frame.box());
    }

    /** Sends an entity standing in the field to the gate's target. */
    public void travel(Entity entity) {
        if (!(level instanceof ServerLevel server) || target() == null) return;
        PortalTravel.send(server, worldPosition, entity, target, worldId);
    }

    /** Called when the objective of a world made by this gate is collected. Returns whether a story card was used up. */
    public boolean storySuccess(PortalData.World w) {
        if (w.card == null || !(level instanceof ServerLevel server)) return false;
        final PortalCard card = PortalResources.cards(server.getServer()).get(w.card);
        if (card == null || card.kind() != PortalCard.Kind.STORY) return false;
        final boolean keep = card.keepOnSuccess() != null ? card.keepOnSuccess()
                : PortalConfig.bool(server.getServer(), PortalConfig.KEEP_STORY_CARDS);
        if (keep) return false;
        if (w.hiddenCard) {
            if (!w.card.equals(hiddenCard)) return false;
            setHiddenCard(null, List.of());
            return true;
        }
        final ItemStack s = items.get(SLOT_CARD);
        if (!w.card.equals(s.get(Portal.CARD))) return false;
        s.shrink(1);
        setChanged();
        return true;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        if (slot == SLOT_CARD) {
            nextPreset = null;
            rolledFor = null;
        }
    }

    /** The controller is broken or replaced (not just unloaded): its rift collapses. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel s) {
            dropRift(s, PortalRifts.Reason.GATE_GONE);
            // the system was this controller's; its planets are not reachable any more
            retireSystem(s.getServer());
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel s) PortalTravel.deactivate(s, worldPosition);
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hiddenCard = input.getString("hidden_card").map(Identifier::tryParse).orElse(null);
        hiddenForced = input.read("hidden_forced", Codec.STRING.listOf()).orElse(List.of());
        system = input.getString("system").orElse(null);
        inTransit = input.getBooleanOr("in_transit", false);
        systemWorlds.clear();
        systemWorlds.putAll(input.read("system_worlds", Codec.unboundedMap(Codec.STRING, Codec.STRING)).orElse(java.util.Map.of()));
        riftToken = input.getLongOr("rift_token", 0L);
        hiddenBindings.clear();
        hiddenBindings.putAll(input.read("hidden_bindings", Codec.unboundedMap(Codec.STRING, Codec.STRING)).orElse(java.util.Map.of()));
        storyAttempts.clear();
        storyAttempts.putAll(input.read("story_attempts", Codec.unboundedMap(Codec.STRING, Codec.STRING)).orElse(java.util.Map.of()));
        enabled = input.getBooleanOr("enabled", true);
        closed = input.getBooleanOr("closed", false);
        owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        worldId = input.getString("world").orElse(null);
        target = input.getString("target").map(Identifier::tryParse)
                .map(id -> ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id)).orElse(null);
        nextPreset = input.getString("next_preset").map(Identifier::tryParse).orElse(null);
        rolledFor = input.getString("rolled_for").map(Identifier::tryParse).orElse(null);
        // a field never survives a reload; the gate comes back closed
        openTicks = 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (hiddenCard != null) output.putString("hidden_card", hiddenCard.toString());
        if (!hiddenForced.isEmpty()) output.store("hidden_forced", Codec.STRING.listOf(), hiddenForced);
        if (system != null) output.putString("system", system);
        if (inTransit) output.putBoolean("in_transit", true);
        if (!systemWorlds.isEmpty()) output.store("system_worlds", Codec.unboundedMap(Codec.STRING, Codec.STRING), systemWorlds);
        if (riftToken != 0) output.putLong("rift_token", riftToken);
        if (!hiddenBindings.isEmpty()) output.store("hidden_bindings", Codec.unboundedMap(Codec.STRING, Codec.STRING), hiddenBindings);
        if (!storyAttempts.isEmpty()) output.store("story_attempts", Codec.unboundedMap(Codec.STRING, Codec.STRING), storyAttempts);
        output.putBoolean("enabled", enabled);
        output.putBoolean("closed", closed);
        if (owner != null) output.store("owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        if (worldId != null) output.putString("world", worldId);
        if (target != null) output.putString("target", target.identifier().toString());
        if (nextPreset != null) output.putString("next_preset", nextPreset.toString());
        if (rolledFor != null) output.putString("rolled_for", rolledFor.toString());
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new GateMenu(id, inventory, this, gateData, this);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.simplemachines.portal_gate_controller");
    }
}
