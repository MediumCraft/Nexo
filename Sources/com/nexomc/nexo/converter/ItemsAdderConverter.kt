package com.nexomc.nexo.converter

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.jeff_media.morepersistentdatatypes.DataType
import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.mechanics.custom_block.noteblock.NoteMechanicHelpers
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringMechanicHelpers
import com.nexomc.nexo.pack.VanillaResourcePack
import com.nexomc.nexo.pack.creative.NexoPackReader
import com.nexomc.nexo.utils.*
import com.nexomc.nexo.utils.logs.Logs
import com.nexomc.nexo.utils.wrappers.AttributeWrapper
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.EquipmentSlot
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.NodePath
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.math.BigDecimal
import java.math.RoundingMode

object ItemsAdderConverter {

    val ITEMSADDER_ITEM_KEY = NamespacedKey.fromString("itemsadder")

    private val nexoFolder = NexoPlugin.instance().dataFolder
    private val iaFolder = nexoFolder.resolve("converter", "ItemsAdder")
    private val vanillaModels by lazy { VanillaResourcePack.resourcePack.models().mapFastSet { it.key().asMinimalString().removeSuffix(".json") } }
    private val vanillaTextures by lazy { VanillaResourcePack.resourcePack.textures().mapFastSet { it.key().asMinimalString().removeSuffix(".png") } }

    fun convert() {
        val iaConverter = NexoPlugin.instance().converter().itemsadderConverter
        if (!iaFolder.exists() || FileUtils.isEmpty(iaFolder)) return

        Logs.logInfo("Starting conversion of ItemsAdder-setup...")

        val configFiles = (iaFolder.resolve("contents").listFiles { file -> file.isDirectory }
            ?.flatMap { it.walkBottomUp().filter { it.extension == "yml" } } ?: emptyList())
            .plusFast(iaFolder.resolve("data", "items_packs").walkBottomUp().filter { it.extension == "yml" }.toFastList())
        val storageFolder = iaFolder.resolve("storage")
        val noteBlockVariationCache = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK)
            .file(storageFolder.resolve("real_blocks_note_ids_cache.yml")).build().load()
        val stringBlockVariationCache = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK)
            .file(storageFolder.resolve("real_wire_ids_cache.yml")).build().load()
        val glyphCharCache = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK)
            .file(storageFolder.resolve("font_images_unicode_cache.yml")).build().load()
        val customModelDataCache = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK)
            .file(storageFolder.resolve("items_ids_cache.yml")).build().load()

        val nexoIaPack = nexoFolder.resolve("pack/external_packs/ItemsAdder").apply { resolve("assets").mkdirs() }
        if (iaConverter.convertResourcePack) {

            // ItemsAdder/data/resource_pack/[assets]
            iaFolder.resolve("data", "resource_pack").takeIf { it.exists() }?.copyRecursively(nexoIaPack, true)

            // ItemsAdder/contents/(resourcepack|resource_pack)/[assets]
            iaFolder.resolve("contents").listDirectories().flatMapFastNotNull {
                listOf(it.resolve("resourcepack"), it.resolve("resource_pack"))
            }.forEach { resourcePack ->
                val target = nexoIaPack.takeIf { resourcePack.resolve("assets").exists() } ?: nexoIaPack.resolve("assets")
                if (resourcePack.exists()) resourcePack.copyRecursively(target, true)
            }

            // ItemsAdder/contents/namespace/(assets/)(textures/models)...
            iaFolder.resolve("contents").listFiles { file -> file.isDirectory && file.name != "resourcepack" && file.name != "resource_pack" }?.forEach { namespace ->
                namespace.listFiles { file -> file.isDirectory && file.name != "configs" && file.name != "resourcepack" && file.name != "resource_pack" }?.forEach { dir ->
                    val target = if (dir.name == "assets") nexoIaPack.resolve("assets") else nexoIaPack.resolve("assets", namespace.name, dir.name)
                    dir.copyRecursively(target, true)
                }
            }

            runCatching {
                // Filter out all vanilla models and textures
                NexoPackReader.INSTANCE.readFile(nexoIaPack).apply {
                    models().removeIf {
                        if (it.key().asMinimalString().removeSuffix(".json") !in vanillaModels) return@removeIf false
                        it in VanillaResourcePack.resourcePack.models()
                    }
                    textures().removeIf {
                        if (it.key().asMinimalString().removeSuffix(".png") !in vanillaTextures) return@removeIf false
                        it in VanillaResourcePack.resourcePack.textures()
                    }
                }
            }.onFailure {
                Logs.logWarn("Failed to read IA-Pack and filter out vanilla models & textures")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
            }
        }

        val registeredItemIds = mutableListOf<String>()
        val recipeNodes = mutableMapOf<String, List<ConfigurationNode>>()
        if (iaConverter.convertItems) configFiles.forEach { itemFile ->
            val iaLoader = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK).file(itemFile).build()
            val iaNode = runCatching { iaLoader.load() }.getOrElse {
                Logs.logError("Failed to load & convert ${itemFile.path}")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
                return@forEach
            }
            val namespace = iaNode.node("info", "namespace").string

            val nexoItemFile = nexoFolder.resolve("items/itemsadder/$namespace/${itemFile.name}")
            val nexoItemLoader = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK).file(nexoItemFile).build()
            val nexoItemNode = nexoItemLoader.load()

            val armorLayerPaths = iaNode.node("armors_rendering").childrenMap().plus(iaNode.node("equipments").childrenMap()).mapNotNull { (armorId, armorNode) ->
                val layer1 = (armorNode.node("layer_1").string ?: armorNode.node("layer_2").string)?.prependIfMissing("$namespace:") ?: return@mapNotNull null
                val layer2 = armorNode.node("layer_2").string?.prependIfMissing("$namespace:") ?: layer1
                armorId.toString() to (layer1 to layer2)
            }.toFastMap()

            iaNode.node("items").takeUnless { it.virtual() }?.childrenMap()?.map { it.key.toString() to it.value }?.forEach { (id, iaItem) ->
                val itemId = (id.takeUnless { it in registeredItemIds } ?: "${namespace}_$id".also {
                    iaConverter.changedItemIds["$namespace:$id"] = it
                    Logs.logWarn("Found duplicate Item-ID <i>$id</i>, remapping to <i>$it")
                }).also(registeredItemIds::add)
                var nexoItem: ConfigurationNode = nexoItemNode.node(itemId)

                iaItem.node("display_name").ifExists {
                    nexoItem.node("itemname").set(it.string?.deserialize()?.serialize())
                }
                nexoItem.node("material").set(iaItem.node("resource", "material").getString("PAPER"))
                iaItem.node("lore").ifExists {
                    nexoItem.node("lore").set(it.stringListOrNull?.map(AdventureUtils::parseLegacy))
                }
                //TODO Implement serializable enchantment handler here https://itemsadder.devs.beer/plugin-usage/adding-content/item-properties/basic#enchants
                if (!iaItem.node("enchantments").virtual()) nexoItem.node("Enchantments").set(iaItem.node("enchantments"))

                iaItem.convertAttributeModifiers(iaItem.node("attribute_modifiers"), nexoItem)
                nexoItem.node("unbreakable").mergeFrom(iaItem.node("durability", "unbreakable"))
                nexoItem.node("ItemFlags").mergeFrom(iaItem.node("item_flags"))

                iaItem.node("nbt").takeIf { it.string != null }?.ifExists { nbtNode ->
                    val nbtJson = runCatching { JsonParser.parseString(nbtNode.string) }.getOrNull()?.asJsonObject?.asMap() ?: return@ifExists
                    val persistentData = nexoItem.node("PersistentData")
                    nbtJson.forEach json@{ (key, element) ->
                        when (element) {
                            is JsonPrimitive -> {
                                val (type, value) = when {
                                    element.isBoolean -> DataType.BOOLEAN.complexType.simpleName.uppercase() to element.asBoolean
                                    element.isString -> DataType.STRING.complexType.simpleName.uppercase() to element.asString
                                    element.isNumber -> DataType.FLOAT.complexType.simpleName.uppercase() to element.asFloat
                                    else -> return@json Logs.logWarn("Failed to convert NBT-Entry $key in $itemId, $element")
                                }
                                persistentData.appendListNode().apply {
                                    node("key").set(key)
                                    node("type").set(type)
                                    node("value").set(value)
                                }
                            }
                        }
                    }
                }

                iaItem.node("specific_properties", "armor").ifExists { armorNode ->
                    val slot = runCatching { EquipmentSlot.valueOf(armorNode.node("slot").string!!.uppercase()) }.getOrNull() ?: return@ifExists
                    val (material, suffix) = when(slot) {
                        EquipmentSlot.HEAD -> Material.CHAINMAIL_HELMET.name to "helmet"
                        EquipmentSlot.CHEST -> Material.CHAINMAIL_CHESTPLATE.name to "chestplate"
                        EquipmentSlot.LEGS -> Material.CHAINMAIL_LEGGINGS.name to "leggings"
                        EquipmentSlot.FEET -> Material.CHAINMAIL_BOOTS.name to "boots"
                        else -> return@ifExists
                    }
                    // Attempt to correct item-material
                    nexoItemNode.node("material").set(material)

                    // Attempt to correct item-id
                    val prefix = nexoItem.key().toString()
                        .filter(Key::allowedInValue) // Remove special characters
                        .substringBefore(suffix)
                        .removeSuffix("_")
                    val newId = "${prefix}_$suffix".takeUnless { it == itemId } ?: return@ifExists

                    iaConverter.changedItemIds["$namespace:$id"] = newId
                    Logs.logWarn("Found malformed CustomArmor Item-ID <i>$id</i>, remapping to <i>$newId")
                    nexoItem = nexoItem.renameNode(newId) ?: nexoItem
                }

                (iaItem.node("template").string ?: iaItem.node("variant_of").string)?.let {
                    nexoItem.node("template").set(it)
                }

                nexoItem.node("Mechanics").let { mechanicsNode ->

                    // Custom Block properties
                    val iaBlockNode = iaItem.node("specific_properties", "block", "placed_model", "type")
                    if (!iaBlockNode.virtual()) when (val type = iaBlockNode.string) {
                        "REAL_NOTE" -> mechanicsNode.node("custom_block").apply {
                            node("type").set("NOTEBLOCK")
                            // Subtract 256 for IA offset
                            val legacyData = NoteMechanicHelpers.legacyBlockData(noteBlockVariationCache.node("$namespace:$itemId").int.minus(256))
                            val modernVariation = legacyData?.let(NoteMechanicHelpers::modernCustomVariation) ?: return@apply
                            node("custom_variation").set(modernVariation)
                        }
                        "REAL_WIRE" -> mechanicsNode.node("custom_block").apply {
                            node("type").set("STRINGBLOCK")
                            node("is_tall").set(iaItem.node("specific_properties", "block", "shift_up").boolean)
                            val legacyBlockData = StringMechanicHelpers.legacyBlockData(stringBlockVariationCache.node("$namespace:$itemId").int.minus(1008))
                            val modernVariation = StringMechanicHelpers.modernCustomVariation(legacyBlockData)
                            node("custom_variation").set(modernVariation)
                        }
                        else -> {
                            Logs.logError("Failed to properly migrate $itemId to Nexo...")
                            Logs.logWarn("      Item is a Custom-Block with unsupported type $type")
                            null
                        }
                    }?.let { blockNode ->
                        // Common custom block properties
                        val blockModel = when {
                            iaItem.node("resource", "generate").boolean -> itemId
                            else -> iaItem.node("resource", "model_path").string
                        }
                        blockNode.node("model").set(blockModel)
                        blockNode.node("hardness").set(iaItem.node("block", "hardness").getDouble(2.0))
                        blockNode.convertBlockSounds(iaItem.node("specific_properties", "block"))
                        blockNode.convertPlaceableOn(iaItem, true)
                    }

                    // Furniture Properties
                    mechanicsNode.node("furniture").takeIf { iaItem.hasChild("behaviours", "furniture") }?.let { furnitureNode ->
                        val iaFurniture = iaItem.node("behaviours", "furniture")
                        val iaDisplayTransform = iaFurniture.node("display_transformation")
                        val transform = when {
                            iaFurniture.node("entity").string == "ARMOR_STAND" -> "HEAD"
                            iaFurniture.node("entity").string == "ITEM_FRAME" -> "FIXED"
                            iaDisplayTransform.node("adjust_legacy_model").string == "armor_stand" -> "HEAD"
                            iaDisplayTransform.node("adjust_legacy_model").string == "item_frame" -> "FIXED"
                            else -> "NONE"
                        }
                        furnitureNode.node("properties", "display_transform").set(iaDisplayTransform.node("transform").getString(transform))
                        iaDisplayTransform.node("translation").ifExists {
                            furnitureNode.node("properties", "translation").set(it.childrenMap().values.joinToString(",") {
                                it.double.toString().removeSuffix(".0")
                            })
                        }
                        iaDisplayTransform.node("scale").ifExists {
                            furnitureNode.node("properties", "scale").set(it.childrenMap().values.joinToString(",") {
                                it.getDouble(if (iaFurniture.node("entity").string == "ITEM_FRAME") 0.5 else 1.0).toString().removeSuffix(".0")
                            })
                        }
                        iaDisplayTransform.node("adjust_legacy_model").string?.also {
                            val (scale, translation) = when (it) {
                                "armor_stand" -> "0.65,0.65,0.65" to "0,1.7,0"
                                "item_frame" -> "0.5,0.5,0.5" to "0,0,0"
                                else -> return@also
                            }
                            furnitureNode.node("properties", "scale").set(scale)
                            furnitureNode.node("properties", "translation").set(translation)
                            if (it == "item_frame") furnitureNode.node("properties", "right_rotation").set("0.707,0,0,-0.707")
                        }

                        // Solid hitbox
                        if (iaFurniture.node("solid").boolean) {
                            val xOffset = iaFurniture.node("hitbox", "width_offset").double
                            val xSize = iaFurniture.node("hitbox", "width").int
                            val yOffset = iaFurniture.node("hitbox", "height_offset").double
                            val ySize = iaFurniture.node("hitbox", "height").int
                            val zOffset = iaFurniture.node("hitbox", "length_offset").double
                            val zSize = iaFurniture.node("hitbox", "length").int

                            val barriers = mutableListOf<String>()
                            for (x in 0 until xSize) for (y in 0 until ySize) for (z in 0 until zSize)
                                barriers += "${x.plus(xOffset)},${y.plus(yOffset)},${z.plus(zOffset)}"

                            furnitureNode.node("hitbox", "barriers").set(barriers)

                            if (iaFurniture.node("hitbox").childrenList().any { it.getInt(1) != 1 })
                                furnitureNode.node("hitbox").set(iaFurniture.node("hitbox").getList(Int::class.java))
                            else furnitureNode.node("barrier").set(true)
                        } else {
                            val iaHitbox = iaFurniture.node("hitbox")
                            val xOffset = iaHitbox.node("width_offset").double.toString().removeSuffix(".0")
                            val yOffset = iaHitbox.node("height_offset").double.toString().removeSuffix(".0")
                            val zOffset = iaHitbox.node("length_offset").double.toString().removeSuffix(".0")
                            val width = iaHitbox.node("width").getDouble(1.0).toString().removeSuffix(".0")
                            val height = iaHitbox.node("height").getDouble(1.0).toString().removeSuffix(".0")

                            furnitureNode.node("hitbox", "interactions").set(listOf("$xOffset,$yOffset,$zOffset $width,$height"))
                        }

                        iaFurniture.node("light_level").ifExists {
                            val lightOffset = BigDecimal(iaFurniture.node("hitbox", "height").double.minus(iaFurniture.node("hitbox", "height_offset").double)).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                            furnitureNode.node("lights").set(listOf("0,$lightOffset,0 ${it.int.coerceIn(0..15)}"))
                        }

                        iaItem.node("behaviours", "furniture_sit").ifExists {
                            val sitHeight = BigDecimal(it.node("sit_height").double - 0.6).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                            furnitureNode.node("seats").set(listOf("0,$sitHeight,0"))
                        }

                        furnitureNode.convertBlockSounds(furnitureNode)
                        furnitureNode.convertPlaceableOn(furnitureNode.node("placeable_on"), false)
                    }
                }

                // Pack settings
                nexoItem.node("Pack").let { packNode ->
                    val blockNode = iaItem.node("specific_properties", "block", "placed_model", "type").string
                    when (blockNode) {
                        "REAL_NOTE" -> packNode.node("parent_model").set("block/cube_all")
                        "REAL_WIRE" -> packNode.node("parent_model").set("block/cross")
                    }
                    iaItem.node("resource", "model_path").ifExists {
                        val path = (if (":" in it.string!!) it.string else "$namespace:${it.string}")?.takeUnless { it in vanillaModels }
                            ?.removePrefix("minecraft:")?.removeSuffix(".json") ?: return@ifExists
                        packNode.node("model").set(path)
                    } ?: iaItem.node("resource", "textures").ifExists {
                        val textures = it.getList(String::class.java)!!.map { (it.takeIf { ":" in it } ?: "$namespace:$it")
                            .removePrefix("minecraft:").removeSuffix(".png") }
                            .filterNot(vanillaTextures::contains)
                        when {
                            textures.isEmpty() -> return@ifExists
                            textures.size == 1 -> packNode.node("texture").set(textures.first())
                            else -> packNode.node("textures").set(textures)
                        }
                    }

                    customModelDataCache.node(iaItem.node("resource", "material").string).childrenMap()["${namespace}:$itemId"]?.ifExists {
                        packNode.node("custom_model_data").set(it.int)
                    } ?: iaItem.node("resource", "model_id").ifExists {
                        packNode.node("custom_model_data").set(it.int)
                    }

                    val prefix = if (!packNode.node("model").empty()) "model" else "texture"
                    val path = when {
                        packNode.hasChild("model") -> iaItem.node("resource", "model_path").string
                        packNode.hasChild("textures") -> (packNode.node("textures").getList(String::class.java)?.firstOrNull() ?: itemId)
                        else -> itemId
                    }?.prependIfMissing("$namespace:")?.removeSuffix(".png") ?: itemId.prependIfMissing("$namespace:")

                    when (nexoItem.node("material").string) {
                        "BOW" -> packNode.node("pulling_${prefix}s").set(List(3) { "${path}_$it" })
                        "CROSSBOW" -> {
                            packNode.node("pulling_${prefix}s").set(List(3) { "${path}_$it" })
                            packNode.node("charged_$prefix").set("${path}_charged")
                            packNode.node("firework_$prefix").set("${path}_firework")
                        }
                        "FISHING_ROD" -> packNode.node("cast_$prefix").set("${path}_cast")
                        "SHIELD" -> packNode.node("blocking_$prefix").set("${path}_blocking")
                        //"BUNDLE" -> packNode.node("bundle_filled_$prefix").set(path + "_filled")
                        else -> {}
                    }

                    //Custom-Armor
                    iaItem.node("equipment", "id").ifExists {
                        armorLayerPaths.get(it.string)?.also { (layer1, layer2) ->
                            packNode.node("CustomArmor", "layer1").set(layer1)
                            packNode.node("CustomArmor", "layer2").set(layer2)
                        }
                    }
                    iaItem.node("specific_properties", "armor", "custom_armor").ifExists {
                        armorLayerPaths.get(it.string)?.also { (layer1, layer2) ->
                            packNode.node("CustomArmor", "layer1").set(layer1)
                            packNode.node("CustomArmor", "layer2").set(layer2)
                        }
                    }
                }
            }

            val nexoGlyphFile = nexoFolder.resolve("glyphs/itemsadder/$namespace/${itemFile.name}")
            val nexoGlyphLoader = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK).file(nexoGlyphFile).build()
            val nexoGlyphNode = nexoGlyphLoader.load()

            // Glyphs
            iaNode.node("font_images").childrenMap().map { it.key to it.value }.forEach { (fontImageId, iaFontImage) ->
                nexoGlyphNode.node(fontImageId, "texture").set(iaFontImage.node("path").string?.let { if (":" in it) it else "$namespace:$it" })
                nexoGlyphNode.node(fontImageId, "height").set(iaFontImage.node("scale_ratio"))
                nexoGlyphNode.node(fontImageId, "ascent").set(iaFontImage.node("y_position"))
                nexoGlyphNode.node(fontImageId, "chat", "permission").set(iaFontImage.node("permission"))
                nexoGlyphNode.node(fontImageId, "char").set(glyphCharCache.node("$namespace:$fontImageId"))
            }

            iaNode.node("recipes").childrenMap().forEach recipes@{ (type, node) ->
                val type = when (type) {
                    "crafting_table" -> "shaped"
                    "cooking" -> "furnace"
                    else -> return@recipes
                }
                recipeNodes.compute(type) { _, nodes ->
                    return@compute (nodes ?: listOf()).plus(node)
                }
            }

            if (!nexoItemNode.empty()) nexoItemLoader.save(nexoItemNode)
            if (!nexoGlyphNode.empty()) nexoGlyphLoader.save(nexoGlyphNode)
        }

        val nexoRecipeFiles = nexoFolder.resolve("recipes")
        recipeNodes.forEach { (type, recipeNodes) ->
            val nexoRecipeFile = nexoRecipeFiles.resolve("$type.yml")
            val nexoRecipeLoader = YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK).file(nexoRecipeFile).build()
            val nexoRecipeNode = nexoRecipeLoader.load()

            recipeNodes.forEach { recipeNode ->
                recipeNode.childrenMap().forEach { (recipeId, recipeNode) ->
                    // Remove 'enabled' key
                    recipeNode.removeChild("enabled")

                    // Rename 'pattern' to 'shape' and adjust the rows
                    recipeNode.node("pattern").renameNode("shape")?.ifExists {
                        it.set(it.getList(String::class.java)?.map { row -> row.replace("X", " ") })
                    }

                    // Process the result node
                    recipeNode.node("result").ifExists result@{
                        val itemNode = it.removeChildNode("item")
                        val itemValue = itemNode.string ?: return@result
                        when {
                            ":" in itemValue -> {
                                it.removeChildNode("item")
                                it.node("nexo_item").set(iaConverter.changedItemIds[itemValue] ?: itemValue.substringAfter(":"))
                            }

                            Material.matchMaterial(itemValue.uppercase()) != null -> it.node("minecraft_type")
                                .set(itemValue.uppercase())

                            else -> it.node("nexo_item").set(iaConverter.changedItemIds[itemValue] ?: itemValue.substringAfter(":"))
                        }
                    }

                    // Process ingredients
                    recipeNode.node("ingredients").childrenMap().forEach ingredients@{ (_, valueNode) ->
                        val ingredientString = valueNode.string ?: return@ingredients
                        valueNode.set(
                            when {
                                ":" in ingredientString -> mapOf("nexo_item" to (iaConverter.changedItemIds[ingredientString] ?: ingredientString).substringAfter(":"))
                                ingredientString.uppercase() == ingredientString -> mapOf("minecraft_type" to ingredientString)
                                else -> ingredientString
                            }
                        )
                    }

                    // Cooking-recipes
                    recipeNode.node("ingredient").ifExists {
                        val ingredientString = it.string ?: return@ifExists
                        it.set(
                            when {
                                ":" in ingredientString -> mapOf("nexo_item" to (iaConverter.changedItemIds[ingredientString] ?: ingredientString).substringAfter(":"))
                                ingredientString.uppercase() == ingredientString -> mapOf("minecraft_type" to ingredientString)
                                else -> ingredientString
                            }
                        )
                    }

                    recipeNode.node("exp").renameNode("experience")
                    recipeNode.node("cook_time").renameNode("cookingTime")
                    recipeNode.removeChild("machines")

                    nexoRecipeNode.node(recipeId).set(recipeNode)
                }
            }

            // Set the final node
            if (!nexoRecipeNode.empty()) nexoRecipeLoader.save(nexoRecipeNode)
        }

        iaFolder.deleteRecursively()
        Logs.logSuccess("Finished conversion of ItemsAdder- to Nexo-setup!.")
    }

    private fun ConfigurationNode.convertBlockSounds(iaSoundParent: ConfigurationNode) {
        node("block_sounds").set(iaSoundParent.node("sound")).let {
            it?.childrenList()?.forEach {
                it.childrenList().filter { it.path() == NodePath.path("name") }.forEach {
                    it.node("sound").set(it.node("name")); it.removeChild("name")
                }
            }
        }
    }

    private fun ConfigurationNode.convertPlaceableOn(placeableOn: ConfigurationNode, default: Boolean) {
        val limitedPlacing = node("limited_placing")
        placeableOn.node("ceiling").ifExists {
            limitedPlacing.node("roof").set(it)
        }
        placeableOn.node("walls").ifExists {
            limitedPlacing.node("wall").set(it)
        }
        placeableOn.node("floor").ifExists {
            limitedPlacing.node("floor").set(it)
        }
    }

    private fun ConfigurationNode.convertAttributeModifiers(attributeModifiersNode: ConfigurationNode, nexoItem: ConfigurationNode) {
        attributeModifiersNode.ifExists { _ ->
            // Create a new node under `nexoItem`
            val nexoAttributesNode = nexoItem.node("AttributeModifiers")

            // Convert the modifiers and add them as children to the new node
            attributeModifiersNode.childrenMap().forEach { (slotKey, attributesNode) ->
                attributesNode.childrenMap().forEach { (attributeKey, valueNode) ->
                    val attributeName = AttributeWrapper.fromString(
                        attributeKey.toString().replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
                    )?.toString()

                    if (attributeName == null) {
                        Logs.logWarn("Failed to get attribute for $attributeKey in item ${this.key()}")
                        return@forEach
                    }

                    // Add a new entry to the list node
                    nexoAttributesNode.appendListNode().apply {
                        node("amount").set(valueNode.double)
                        node("attribute").set(attributeName)
                        node("operation").set(0)
                        node("slot").set(slotKey.toString().uppercase())
                    }
                }
            }
        }
    }

}