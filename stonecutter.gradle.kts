plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.2-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    properties {
        tags(version, loader)
    }

    constants {
        match(loader, "fabric", "neoforge", "forge")
        // 2.3 ship blocks: src/ship is only built for these versions (buildSrc Ship.kt)
        put("ship", Ship.enabled(version))
    }

    replacements.string(current.parsed < "1.21.11") {
        replace("Identifier", "ResourceLocation")
    }

    // 26.x useItemOn returns InteractionResult; 1.21.1 gets a bridge to ItemInteractionResult
    replacements.regex(current.parsed >= "1.20.5" && current.parsed < "1.21.2") {
        replace(
            "protected InteractionResult useItemOn\\(",
            "protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack s$, BlockState b$, Level l$, BlockPos p$, Player e$, InteractionHand h$, BlockHitResult r$) { return com.workshopcraft.simplemachines.compat.mc.Interactions.item(useItemOn$(s$, b$, l$, p$, e$, h$, r$)); }\n    protected InteractionResult useItemOn$(",
            "protected net\\.minecraft\\.world\\.ItemInteractionResult useItemOn\\(.*\\n    protected InteractionResult useItemOn\\$\\(",
            "protected InteractionResult useItemOn("
        )
        replace(
            "@Override\\n    protected void affectNeighborsAfterRemoval\\(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston\\) \\{",
            "@Override\n    protected void onRemove(BlockState state, net.minecraft.world.level.Level l$, BlockPos pos, BlockState n$, boolean movedByPiston) { com.workshopcraft.simplemachines.compat.mc.Removal.onRemove(l$, pos, state, n$); super.onRemove(state, l$, pos, n$, movedByPiston); if (!state.is(n$.getBlock()) && l$ instanceof ServerLevel s$) affectNeighborsAfterRemoval(state, s$, pos, movedByPiston); }\n    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {",
            "@Override\\n    protected void onRemove\\(BlockState state, net\\.minecraft.*\\n    protected void affectNeighborsAfterRemoval\\(",
            "@Override\n    protected void affectNeighborsAfterRemoval("
        )
    }

    // 1.21.1 stand-ins live in compat.mc under src/legacy-java/1.21.1
    replacements.string(current.parsed < "1.21.2") {
        val mc = "com.workshopcraft.simplemachines.compat.mc"
        replace("net.minecraft.world.level.storage.ValueInput", "$mc.ValueInput")
        replace("net.minecraft.world.level.storage.ValueOutput", "$mc.ValueOutput")
        replace("net.minecraft.core.component.DataComponentGetter", "$mc.DataComponentGetter")
        replace("ContainerHelper.loadAllItems(", "$mc.ItemContainers.loadAllItems(")
        replace("ContainerHelper.saveAllItems(", "$mc.ItemContainers.saveAllItems(")
        replace("extends BlockEntity {", "extends $mc.CompatBlockEntity {")
        replace("extends BlockEntity implements", "extends $mc.CompatBlockEntity implements")
        replace("extends BaseContainerBlockEntity ", "extends $mc.CompatContainerBlockEntity ")
        replace("net.minecraft.world.inventory.ContainerInput", "net.minecraft.world.inventory.ClickType")
        replace("ContainerInput ", "ClickType ")
        replace("ContainerInput.", "ClickType.")
        replace("extends AbstractContainerMenu ", "extends $mc.CompatMenu ")
        replace("player.sendOverlayMessage(", "$mc.Players.overlay(player, ")
        replace("p.sendOverlayMessage(", "$mc.Players.overlay(p, ")
        replace("net.minecraft.world.level.saveddata.SavedDataType", "$mc.SavedDataType")
        replace("extends SavedData {", "extends $mc.CodecSavedData {")
        replace("server.getDataStorage().computeIfAbsent(TYPE)", "TYPE.get(server)")
        replace("Containers.updateNeighboursAfterDestroy(state, level, pos);", "((net.minecraft.world.level.Level) level).updateNeighbourForOutputSignal(pos, state.getBlock());")
        replace("getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)", "getAnalogOutputSignal(BlockState state, Level level, BlockPos pos)")
        replace("protected boolean propagatesSkylightDown(BlockState state)", "protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter g$, net.minecraft.core.BlockPos p$)")
        replace(".typeHolder()", ".getItemHolder()")
        replace("level.isBrightOutside()", "level.isDay()")
        replace("ChunkPos.containing(", "new ChunkPos(")
        replace("player.getInventory().getSelectedSlot()", "player.getInventory().selected")
        replace("recipe.value().assemble(input)", "recipe.value().assemble(input, level.registryAccess())")
        replace("r.value().assemble(in)", "r.value().assemble(in, level.registryAccess())")
        replace(".value().cookingTime()", ".value().getCookingTime()")
        replace("_SOUND.location()", "_SOUND.getLocation()")
        replace(".getCraftingRemainder() != null", ".hasCraftingRemainingItem()")
        replace(", p.getYRot(), p.getXRot(), true)", ", p.getYRot(), p.getXRot())")
        replace("level.sendParticles(viewer, particle, false, false,", "level.sendParticles(viewer, particle, (boolean) false,")
        replace("crop.getCloneItemStack(level, p, false)", "crop.getBlock().getCloneItemStack(level, p, crop)")
        replace("((Shearable) mob).shear(level, SoundSource.BLOCKS, new ItemStack(Items.SHEARS))", "((Shearable) mob).shear(SoundSource.BLOCKS)")
        replace("level.recipeAccess()", "level.getRecipeManager()")
        replace(".getMinY()", ".getMinBuildHeight()")
        replace("BuiltInRegistries.ITEM.getValue(", "BuiltInRegistries.ITEM.get(")
        replace("BuiltInRegistries.FLUID.getValue(", "BuiltInRegistries.FLUID.get(")
        replace("InteractionResult.TRY_WITH_EMPTY_HAND", "$mc.Interactions.TRY_WITH_EMPTY_HAND")
    }
    // 1.20.1: no item components, codecs or split block use hooks; stand-ins in compat.mc under src/legacy-java/1.20.1
    replacements.regex(current.parsed < "1.20.5") {
        replace("(?m)^(\\s+)protected boolean propagatesSkylightDown\\(BlockState state\\)", "$1public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter getter_, net.minecraft.core.BlockPos pos_)", "(?m)^(\\s+)public boolean propagatesSkylightDown\\(BlockState state, net\\.minecraft\\.world\\.level\\.BlockGetter getter_, net\\.minecraft\\.core\\.BlockPos pos_\\)", "$1protected boolean propagatesSkylightDown(BlockState state)")
        replace("@Override(\\s+)protected (?!boolean propagatesSkylightDown)", "@Override$1public /*p*/ ", "@Override(\\s+)public /\\*p\\*/ ", "@Override$1protected ")
        replace(
            "(?<![\\w.])(?!components\\b|ViewerRecipes\\b)([a-zA-Z_]\\w*(?:\\.\\w+\\([^()]*\\))*)\\.(get|set|has|remove|getOrDefault)\\(((?:[\\w.]*\\.)?(?:SimpleMachines|DataComponents)\\.[A-Z_]+)",
            "com.workshopcraft.simplemachines.compat.mc.Comps.$2($1, $3",
            "com\\.workshopcraft\\.simplemachines\\.compat\\.mc\\.Comps\\.(\\w+)\\(([^,]+), ",
            "$2.$1("
        )
        replace("(?<!\\w)Ingredient\\.CONTENTS_STREAM_CODEC", "com.workshopcraft.simplemachines.compat.mc.codec.Ingredients.STREAM_CODEC", "com\\.workshopcraft\\.simplemachines\\.compat\\.mc\\.codec\\.Ingredients\\.STREAM_CODEC", "Ingredient.CONTENTS_STREAM_CODEC")
        replace("(?<!\\w)Ingredient\\.CODEC", "com.workshopcraft.simplemachines.compat.mc.codec.Ingredients.CODEC", "com\\.workshopcraft\\.simplemachines\\.compat\\.mc\\.codec\\.Ingredients\\.CODEC", "Ingredient.CODEC")
        replace("(\\w+)\\.(?:getRecipeManager|recipeAccess)\\(\\)\\.getRecipes\\(\\)", "com.workshopcraft.simplemachines.compat.mc.RecipeCheck.all($1.getRecipeManager())", "com\\.workshopcraft\\.simplemachines\\.compat\\.mc\\.RecipeCheck\\.all\\((\\w+)\\.getRecipeManager\\(\\)\\)", "$1.getRecipeManager().getRecipes()")
        replace("(?<![\\w.])([\\w]+(?:\\.\\w+\\(\\))*)\\.consume\\((\\w+), (\\w+)\\)", "com.workshopcraft.simplemachines.compat.mc.Stacks.consume($1, $2, $3)", "com\\.workshopcraft\\.simplemachines\\.compat\\.mc\\.Stacks\\.consume\\((\\w+), (\\w+), (\\w+)\\)", "$1.consume($2, $3)")
        replace("(?m)^(\\s+)protected (?=[\\w<>?, .\\[\\]]+ (?:updateShape|getShape|getCollisionShape|getVisualShape|getOcclusionShape|onPlace|onRemove|tick|randomTick|rotate|mirror|getRenderShape|useShapeForLightOcclusion|hasAnalogOutputSignal|getAnalogOutputSignal|neighborChanged|isSignalSource|getSignal|getDirectSignal|entityInside|canSurvive|getMenuProvider|spawnAfterBreak|getDrops|skipRendering|attack|triggerEvent|isPathfindable|getShadeBrightness|getCloneItemStack)\\()", "$1public /*p*/ ", "(?m)^(\\s+)public /\\*p\\*/ ", "$1protected ")
        replace("(?<![\\w.])(\\w+)\\.removeLast\\(\\)", "$1.remove($1.size() - 1)", "(?<![\\w.])(\\w+)\\.remove\\(\\1\\.size\\(\\) - 1\\)", "$1.removeLast()")
        replace("(\\w+)\\.componentsMatch\\([\\w.]*DataComponentPatch\\.EMPTY\\)", "!$1.hasNbt()", "!(\\w+)\\.hasNbt\\(\\)", "$1.componentsMatch(DataComponentPatch.EMPTY)")
        replace("(?<!\\w)(\\w+)\\.addVertex\\((?=[^;]*\\bcolour\\b)", "com.workshopcraft.simplemachines.compat.mc.Verts.add($1, ", "com\\.workshopcraft\\.simplemachines\\.compat\\.mc\\.Verts\\.add\\((\\w+), ", "$1.addVertex(")
        replace("(?:Identifier|ResourceLocation)\\.(?:fromNamespaceAndPath|withDefaultNamespace|parse)\\(", "new ResourceLocation(", "new ResourceLocation\\(", "ResourceLocation.parse(")
    }
    replacements.string(current.parsed >= "1.20.5" && current.parsed < "1.21.2") {
        replace("b.getContent()", "b.content")
        replace("bucket.getContent()", "bucket.content")
    }
    replacements.string(current.parsed < "1.20.5") {
        val mc = "com.workshopcraft.simplemachines.compat.mc"
        replace("b.getContent()", "$mc.Buckets.fluid(b)")
        replace("bucket.getContent()", "$mc.Buckets.fluid(bucket)")
        replace("net.minecraft.core.component.", "$mc.component.")
        replace("TooltipContext context", "net.minecraft.world.level.Level context")
        replace("net.minecraft.world.item.crafting.CraftingInput;", "$mc.CraftingInput;")
        replace("net.minecraft.world.level.block.CrafterBlock;", "$mc.CrafterBlock;")
        replace("craft.result.onCraftedBySystem(level);", "/* onCraftedBySystem */")
        replace("net.minecraft.network.protocol.common.custom.CustomPacketPayload", "$mc.CustomPacketPayload")
        replace("net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> getEquipSound()", "net.minecraft.sounds.SoundEvent getEquipSound()")
        replace("super.useItemOn$(", "super.useItemOnDefault(")
        replace("BlockTags.INCORRECT_FOR_NETHERITE_TOOL", "$mc.Stacks.INCORRECT_FOR_NETHERITE_TOOL")
        replace("BlockTags.INCORRECT_FOR_IRON_TOOL", "BlockTags.NEEDS_DIAMOND_TOOL")
        replace("pose.transformNormal(", "pose.normal().transform(")
        replace("sp.getU(uv[i][0] / 16F)", "sp.getU(uv[i][0])")
        replace("sp.getV(uv[i][1] / 16F)", "sp.getV(uv[i][1])")
        replace("ItemStack.OPTIONAL_CODEC", "$mc.Stacks.OPTIONAL_CODEC")
        replace("ItemStack.hashItemAndComponents(", "$mc.Stacks.hash(")
        replace("stack.limitSize(getMaxStackSize(stack))", "$mc.Stacks.limit(this, stack)")
        replace("DataFixTypes.SAVED_DATA_COMMAND_STORAGE", "DataFixTypes.SAVED_DATA")
        replace("Math.clamp(", "net.minecraft.util.Mth.clamp(")
        replace("SoundEvents.PLAYER_TELEPORT", "SoundEvents.ENDERMAN_TELEPORT")
        replace("server.getServerDirectory().resolve(", "server.getServerDirectory().toPath().resolve(")
        replace("player.blockInteractionRange()", "$mc.Players.reach(player)")
        replace(".withColor(Voltage.COLOR[tier])", ".withStyle(s$ -> s$.withColor(Voltage.COLOR[tier]))")
        replace("SoundEvents.CROSSBOW_LOADING_MIDDLE.value()", "SoundEvents.CROSSBOW_LOADING_MIDDLE")
        replace("net.minecraft.world.item.component.", "$mc.item.")
        replace("net.minecraft.network.codec.", "$mc.codec.")
        replace("RegistryFriendlyByteBuf", "FriendlyByteBuf")
        replace("net.minecraft.world.item.crafting.RecipeHolder", "$mc.RecipeHolder")
        replace("net.minecraft.world.item.crafting.RecipeInput", "$mc.RecipeInput")
        replace("net.minecraft.world.item.crafting.SingleRecipeInput", "$mc.SingleRecipeInput")
        replace("RecipeManager.CachedCheck<", "$mc.RecipeCheck<")
        replace("RecipeManager.createCheck(", "$mc.RecipeCheck.create(")
        replace("ItemStack.isSameItemSameComponents(", "ItemStack.isSameItemSameTags(")
        replace("UUIDUtil.STREAM_CODEC", "$mc.codec.ByteBufCodecs.UUID")
        replace("extends Block {", "extends $mc.CompatBlock {")
        replace("extends Block implements", "extends $mc.CompatBlock implements")
        replace("extends BaseEntityBlock ", "extends $mc.CompatEntityBlock ")
    }
}
