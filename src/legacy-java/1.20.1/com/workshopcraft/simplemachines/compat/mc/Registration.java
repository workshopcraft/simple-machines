package com.workshopcraft.simplemachines.compat.mc;

import com.google.common.collect.MapMaker;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.workshopcraft.simplemachines.compat.mc.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Map;
import java.util.Set;

/** Registry object constructors for 1.20.1; recipes get their ids from the serializer. */
public final class Registration {
    private static final Map<Object, ResourceLocation> IDS = new MapMaker().weakKeys().makeMap();
    private static final ResourceLocation UNKNOWN = new ResourceLocation("simplemachines", "unknown");

    private Registration() {
    }

    public static ResourceLocation id(Object recipe) {
        return IDS.getOrDefault(recipe, UNKNOWN);
    }

    public static <T> T withId(ResourceLocation id, T recipe) {
        IDS.put(recipe, id);
        return recipe;
    }

    public static <T extends Recipe<?>> RecipeSerializer<T> serializer(MapCodec<T> codec, StreamCodec<FriendlyByteBuf, T> stream) {
        return new RecipeSerializer<>() {
            @Override
            public T fromJson(ResourceLocation id, JsonObject json) {
                return withId(id, codec.codec().parse(JsonOps.INSTANCE, json).getOrThrow(false, e -> {
                }));
            }

            @Override
            public T fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
                return withId(id, stream.decode(buf));
            }

            @Override
            public void toNetwork(FriendlyByteBuf buf, T recipe) {
                stream.encode(buf, recipe);
            }
        };
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntity(BlockEntityType.BlockEntitySupplier<T> factory, Set<Block> blocks) {
        return BlockEntityType.Builder.of(factory, blocks.toArray(new Block[0])).build(null);
    }

    public static <T extends Entity> EntityType<T> entity(EntityType.EntityFactory<T> factory, MobCategory category,
                                                         float width, float height, ResourceLocation id) {
        return EntityType.Builder.of(factory, category).sized(width, height).clientTrackingRange(8).updateInterval(1)
                .fireImmune().build(id.toString());
    }
}
