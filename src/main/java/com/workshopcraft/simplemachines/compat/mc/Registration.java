package com.workshopcraft.simplemachines.compat.mc;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/** Registry object constructors that changed shape between 1.21.1 and 26.x. */
public final class Registration {
    private Registration() {}

    public static <T extends Recipe<?>> RecipeSerializer<T> serializer(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> stream) {
        //? if >=26.1 {
        return new RecipeSerializer<>(codec, stream);
        //?} else {
        /*return new RecipeSerializer<>() {
            @Override
            public MapCodec<T> codec() {
                return codec;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
                return stream;
            }
        };
        *///?}
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntity(BlockEntityType.BlockEntitySupplier<T> factory, Set<Block> blocks) {
        //? if >=26.1 {
        return new BlockEntityType<>(factory, blocks);
        //?} else {
        /*return BlockEntityType.Builder.of(factory, blocks.toArray(new Block[0])).build(null);
        *///?}
    }

    public static <T extends Entity> EntityType<T> entity(EntityType.EntityFactory<T> factory, MobCategory category,
                                                         float width, float height, Identifier id) {
        final EntityType.Builder<T> b = EntityType.Builder.of(factory, category).sized(width, height)
                .clientTrackingRange(8).updateInterval(1).fireImmune();
        //? if >=26.1 {
        return b.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        //?} else {
        /*return b.build(id.toString());
        *///?}
    }
}
