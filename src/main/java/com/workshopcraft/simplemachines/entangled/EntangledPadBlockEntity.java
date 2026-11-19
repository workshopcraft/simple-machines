package com.workshopcraft.simplemachines.entangled;

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.assembly.Core;
import com.workshopcraft.simplemachines.assembly.Tier;
import com.workshopcraft.simplemachines.cable.Voltage;
import com.workshopcraft.simplemachines.item.PowerTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Jump on a pad to go to the other pad on its code, in any dimension. The pad you jump from pays 2,000 FE plus 20 FE a block
 * of x and z distance, at most 50,000. A private pad only sends its owner.
 */
public class EntangledPadBlockEntity extends EntangledDeviceBlockEntity {
    /** Set by the portal extra to play the short glitch and coloured pop on a successful jump. */
    public static java.util.function.Consumer<Jump> fx = j -> {};

    public record Jump(ServerLevel from, BlockPos fromPos, ServerLevel to, BlockPos toPos, ServerPlayer player, String code) {}

    public static final int CAPACITY = 100_000, BASE = 2_000, PER_BLOCK = 20, MAX_COST = 50_000, COOLDOWN = 60;
    public static final int READY = 0, NO_CODE = 1, ALONE = 2, FULL = 3, LOW = 4;
    public static final int DATA_COUNT = EntangledDeviceBlockEntity.DATA_COUNT + 5;
    private int status = NO_CODE, cost;
    private @Nullable GlobalPos other;
    private @Nullable String joined;
    private long lastTick = -1;
    private final Map<UUID, Long> standing = new HashMap<>();

    private final ContainerData padData = new ContainerData() {
        @Override
        public int get(int id) {
            if (id < EntangledDeviceBlockEntity.DATA_COUNT) return menuData.get(id);
            if (other == null) return 0;
            final BlockPos p = other.pos();
            return switch (id - EntangledDeviceBlockEntity.DATA_COUNT) {
                case 0 -> p.getX() & 0xFFFF;
                case 1 -> p.getX() >>> 16;
                case 2 -> p.getY() & 0xFFFF;
                case 3 -> p.getZ() & 0xFFFF;
                default -> p.getZ() >>> 16;
            };
        }

        @Override
        public void set(int id, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public EntangledPadBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleMachines.MACHINE_ENTITIES.get(Core.PAD), pos, state, 0, EntangledData.PAD, CAPACITY, Voltage.RATE[Voltage.MV], 0);
    }

    public static int cost(BlockPos a, BlockPos b) {
        final double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return (int) Math.min(MAX_COST, BASE + PER_BLOCK * (long) Math.floor(Math.sqrt(dx * dx + dz * dz)));
    }

    public static int dimension(@Nullable ResourceKey<Level> dim) {
        if (dim == null) return 0;
        if (dim == Level.OVERWORLD) return 1;
        if (dim == Level.NETHER) return 2;
        if (dim == Level.END) return 3;
        return 4;
    }

    @Override
    protected int extra(int i) {
        return i == 0 ? status | dimension(other == null ? null : other.dimension()) << 4 : cost;
    }

    @Override
    protected int upkeep() {
        return 0;
    }

    @Override
    protected boolean lit() {
        return status == READY;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        super.serverTick(level, pos, state);
        energy.maxIn = Voltage.RATE[Voltage.MV];
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state, Tier tier) {
        final long now = level.getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        update(level, now);
        super.work(level, pos, state, tier);
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1))) {
            if (!(p instanceof ServerPlayer sp) || p.isPassenger() || p.isSpectator()) continue;
            if (Math.floor(p.getX()) != pos.getX() || Math.floor(p.getZ()) != pos.getZ()) continue;
            final double feet = p.getY() - (pos.getY() + 1);
            if (p.onGround() && Math.abs(feet) < 0.01) standing.put(p.getUUID(), now);
            else if (!p.onGround() && feet > 0.01 && now - standing.getOrDefault(p.getUUID(), -100L) <= 4) {
                standing.remove(p.getUUID());
                jump(level, sp, now);
            }
        }
        if (!standing.isEmpty()) standing.values().removeIf(t -> now - t > 40);
    }

    private void update(ServerLevel level, long now) {
        final EntangledData data = EntangledData.get(level.getServer());
        final String key = link == null ? null : EntangledData.key(EntangledData.PAD, link);
        if (joined != null && !joined.equals(key)) leave(data);
        other = null;
        cost = 0;
        if (key == null) {
            status = NO_CODE;
            return;
        }
        final EntangledData.Channel ch = data.channel(EntangledData.PAD, link);
        final GlobalPos me = GlobalPos.of(level.dimension(), worldPosition);
        if (!ch.pads.contains(me)) {
            if (ch.pads.size() >= EntangledData.MAX_PADS && (now + worldPosition.hashCode()) % 20 == 0) prune(level.getServer(), ch, key);
            if (ch.pads.size() >= EntangledData.MAX_PADS) {
                status = FULL;
                return;
            }
            ch.pads.add(me);
            data.setDirty();
        }
        joined = key;
        for (GlobalPos p : ch.pads) if (!p.equals(me)) other = p;
        if (other == null) {
            status = ALONE;
            return;
        }
        cost = cost(worldPosition, other.pos());
        status = energy.energy >= cost ? READY : LOW;
    }

    /** Drops pads in loaded chunks that are no longer pads on this code. */
    private static void prune(MinecraftServer server, EntangledData.Channel ch, String key) {
        ch.pads.removeIf(p -> {
            final ServerLevel l = server.getLevel(p.dimension());
            if (l == null) return true;
            if (!l.isLoaded(p.pos())) return false;
            return !(l.getBlockEntity(p.pos()) instanceof EntangledPadBlockEntity pad && key.equals(pad.key()));
        });
    }

    private @Nullable String key() {
        return link == null ? null : EntangledData.key(EntangledData.PAD, link);
    }

    private void leave(EntangledData data) {
        final EntangledData.Channel ch = data.byKey(joined);
        if (ch != null && level != null && ch.pads.remove(GlobalPos.of(level.dimension(), worldPosition))) data.setDirty();
        joined = null;
    }

    private static void tell(Player p, String key, Object... args) {
        p.sendOverlayMessage(Component.translatable("message.simplemachines." + key, args).withStyle(ChatFormatting.RED));
    }

    private void jump(ServerLevel level, ServerPlayer p, long now) {
        if (!EntangledLink.allows(link, p)) {
            tell(p, "pad_not_owner");
            return;
        }
        switch (status) {
            case NO_CODE -> { tell(p, "pad_no_code"); return; }
            case ALONE -> { tell(p, "pad_alone"); return; }
            case FULL -> { tell(p, "pad_full", EntangledData.MAX_PADS); return; }
            case LOW -> { tell(p, "pad_low", PowerTooltip.num(cost)); return; }
            default -> {}
        }
        final EntangledData data = EntangledData.get(level.getServer());
        if (data.padCooldown.getOrDefault(p.getUUID(), 0L) > now) {
            tell(p, "pad_cooldown");
            return;
        }
        final GlobalPos to = other;
        final ServerLevel there = to == null ? null : level.getServer().getLevel(to.dimension());
        if (to == null || there == null) return;
        // loads the far chunk long enough to check the pad is still there
        there.getChunk(to.pos());
        if (!(there.getBlockEntity(to.pos()) instanceof EntangledPadBlockEntity pad && joined != null && joined.equals(pad.key()))) {
            final EntangledData.Channel ch = data.byKey(joined);
            if (ch != null && ch.pads.remove(to)) data.setDirty();
            tell(p, "pad_gone");
            return;
        }
        final Vec3 dest = Vec3.atBottomCenterOf(to.pos().above());
        if (!there.noCollision(p, p.getBoundingBox().move(dest.subtract(p.position())))) {
            tell(p, "pad_blocked");
            return;
        }
        energy.energy -= cost;
        setChanged();
        data.padCooldown.put(p.getUUID(), now + COOLDOWN);
        if (data.padCooldown.size() > 64) data.padCooldown.values().removeIf(t -> t <= now);
        fx.accept(new Jump(level, worldPosition, there, to.pos(), p, link == null ? "" : link.digits()));
        level.playSound(null, worldPosition.above(), SoundEvents.PLAYER_TELEPORT, SoundSource.BLOCKS, 1, 1);
        p.teleportTo(there, dest.x, dest.y, dest.z, Set.of(), p.getYRot(), p.getXRot(), true);
        p.resetFallDistance();
        there.playSound(null, to.pos().above(), SoundEvents.PLAYER_TELEPORT, SoundSource.BLOCKS, 1, 1);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (joined != null && level instanceof ServerLevel server) leave(EntangledData.get(server.getServer()));
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.simplemachines.entangled_pad");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new EntangledPadMenu(id, this, padData);
    }
}
