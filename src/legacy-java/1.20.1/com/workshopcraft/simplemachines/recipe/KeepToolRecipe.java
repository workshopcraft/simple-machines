package com.workshopcraft.simplemachines.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** 1.20.1 copy: shapeless crafting that hands back the "keep" ingredient (a screwdriver) instead of using it up. */
public class KeepToolRecipe implements CraftingRecipe {
    public static final RecipeSerializer<KeepToolRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public KeepToolRecipe fromJson(ResourceLocation id, JsonObject json) {
            final List<Ingredient> ingredients = new ArrayList<>();
            final JsonArray arr = GsonHelper.getAsJsonArray(json, "ingredients");
            for (JsonElement e : arr) ingredients.add(Ingredient.fromJson(e, false));
            if (ingredients.isEmpty() || ingredients.size() > 9) throw new com.google.gson.JsonParseException("1 to 9 ingredients");
            final CraftingBookCategory category = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
            final JsonObject res = GsonHelper.getAsJsonObject(json, "result");
            final ItemStack result = ShapedRecipe.itemStackFromJson(res);
            // vanilla 1.20.1 drops result nbt; Data120 routes recipes whose result has mod components through here
            if (res.has("nbt") && res.get("nbt").isJsonObject()) {
                result.setTag((net.minecraft.nbt.CompoundTag) com.mojang.serialization.JsonOps.INSTANCE.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, res.get("nbt")));
            }
            return new KeepToolRecipe(id, GsonHelper.getAsString(json, "group", ""), category, result, ingredients,
                    json.has("keep") ? Ingredient.fromJson(json.get("keep"), false) : Ingredient.EMPTY);
        }

        @Override
        public KeepToolRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            final String group = buf.readUtf();
            final CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
            final ItemStack result = buf.readItem();
            final List<Ingredient> ingredients = buf.readList(Ingredient::fromNetwork);
            return new KeepToolRecipe(id, group, category, result, ingredients, Ingredient.fromNetwork(buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, KeepToolRecipe r) {
            buf.writeUtf(r.group);
            buf.writeEnum(r.category);
            buf.writeItem(r.result);
            buf.writeCollection(r.ingredients, (b, i) -> i.toNetwork(b));
            r.keep.toNetwork(buf);
        }
    };

    private final ResourceLocation id;
    private final String group;
    private final CraftingBookCategory category;
    private final ItemStack result;
    private final List<Ingredient> ingredients;
    private final Ingredient keep;

    public KeepToolRecipe(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result, List<Ingredient> ingredients, Ingredient keep) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.result = result;
        this.ingredients = ingredients;
        this.keep = keep;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<KeepToolRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        final NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingredients);
        return list;
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        final StackedContents contents = new StackedContents();
        int count = 0;
        final List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.getContainerSize(); i++) {
            final ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            count++;
            items.add(s);
            contents.accountStack(s, 1);
        }
        if (count != ingredients.size()) return false;
        return count == 1 ? ingredients.get(0).test(items.get(0)) : contents.canCraft(this, null);
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        final NonNullList<ItemStack> left = CraftingRecipe.super.getRemainingItems(input);
        boolean kept = false;
        for (int slot = 0; slot < left.size(); slot++) {
            final ItemStack in = input.getItem(slot);
            if (!kept && !in.isEmpty() && !keep.isEmpty() && keep.test(in)) {
                left.set(slot, in.copyWithCount(1));
                kept = true;
            }
        }
        return left;
    }
}
