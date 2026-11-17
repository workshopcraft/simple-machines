import java.io.File

// Forward-only text rules that take the 1.21.1 overlays down to 1.20.1; mirrors the < 1.20.5 stonecutter replacements.
object Downport {
    private const val MC = "com.workshopcraft.simplemachines.compat.mc"

    private val regex: List<Pair<Regex, String>> = listOf(
        "@Override(\\s+)protected " to "@Override$1public ",
        "(?<![\\w.])(?!components\\b|ViewerRecipes\\b)([a-zA-Z_]\\w*(?:\\.\\w+\\([^()]*\\))*)\\.(get|set|has|remove|getOrDefault)\\(((?:[\\w.]*\\.)?(?:SimpleMachines|DataComponents|Portal|Ship)\\.[A-Z_]+)" to "$MC.Comps.$2($1, $3",
        "(?<!\\w)Ingredient\\.CONTENTS_STREAM_CODEC" to "$MC.codec.Ingredients.STREAM_CODEC",
        "(?<!\\w)Ingredient\\.CODEC_NONEMPTY" to "$MC.codec.Ingredients.CODEC_NONEMPTY",
        "(?<!\\w)Ingredient\\.CODEC" to "$MC.codec.Ingredients.CODEC",
        "(\\w+)\\.(?:getRecipeManager|recipeAccess)\\(\\)\\.getRecipes\\(\\)" to "$MC.RecipeCheck.all($1.getRecipeManager())",
        "(?<![\\w.])(\\w+)\\.removeLast\\(\\)" to "$1.remove($1.size() - 1)",
        "(?:net\\.minecraft\\.resources\\.)?(?:Identifier|ResourceLocation)\\.(?:fromNamespaceAndPath|withDefaultNamespace|parse)\\(" to "new net.minecraft.resources.ResourceLocation(",
        "RecipeType\\.createRecipeHolderType\\(" to "$MC.jei.JeiTypes.holder(",
        "(\\w+)\\.blitSprite\\(" to "$MC.Gui.blitSprite($1, ",
        "(\\w+)\\.transformNormal\\(([^,]+), ([^,]+), ([^,]+), new Vector3f\\(\\)\\)" to "$1.normal().transform(new Vector3f($2, $3, $4))",
        "Minecraft\\.getInstance\\(\\)\\.getTimer\\(\\)\\.getGameTimeDeltaPartialTick\\(\\w+\\)" to "Minecraft.getInstance().getFrameTime()",
        "(?<!\\w)(\\w+)\\.addVertex\\((?=[^;]*\\b(?:colour|tint)\\b)" to "$MC.Verts.add($1, ",
        "\\.get([UV])\\((\\w+) / 16F\\)" to ".get$1($2)",
        "(\\w+)\\.getTextureDiffuseColor\\(\\)" to "$MC.Colors.diffuse($1)",
    ).map { Regex(it.first) to it.second }

    private val strings: List<Pair<String, String>> = listOf(
        "net.minecraft.core.component." to "$MC.component.",
        "net.minecraft.world.item.component." to "$MC.item.",
        "net.minecraft.network.codec." to "$MC.codec.",
        "RegistryFriendlyByteBuf" to "FriendlyByteBuf",
        "net.minecraft.world.item.crafting.RecipeHolder" to "$MC.RecipeHolder",
        "net.minecraft.world.item.crafting.RecipeInput" to "$MC.RecipeInput",
        "net.minecraft.world.item.crafting.SingleRecipeInput" to "$MC.SingleRecipeInput",
        "net.minecraft.world.item.crafting.CraftingInput" to "$MC.CraftingInput",
        "ItemStack.isSameItemSameComponents(" to "ItemStack.isSameItemSameTags(",
        ".getFirst()" to ".get(0)",
        "TooltipContext context" to "net.minecraft.world.level.Level context",
    )

    fun apply(text: String): String {
        var s = text
        for ((r, to) in regex) s = r.replace(s, to)
        for ((from, to) in strings) s = s.replace(from, to)
        return s
    }

    fun copy(from: File, to: File): File {
        if (to.exists()) to.deleteRecursively()
        if (from.isDirectory) from.walk().filter { it.isFile }.forEach { f ->
            val out = File(to, f.relativeTo(from).path)
            out.parentFile.mkdirs()
            out.writeText(if (f.name.endsWith(".java")) apply(f.readText()) else f.readText())
        }
        return to
    }
}
