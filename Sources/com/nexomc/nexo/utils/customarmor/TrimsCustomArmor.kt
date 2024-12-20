package com.nexomc.nexo.utils.customarmor

import com.google.gson.JsonObject
import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.pack.DefaultResourcePackExtractor
import com.nexomc.nexo.utils.NexoDatapack
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.logs.Logs
import com.nexomc.nexo.utils.printOnFailure
import net.kyori.adventure.key.Key
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Tag
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.ArmorMeta
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.Atlas
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.PalettedPermutationsAtlasSource
import team.unnamed.creative.base.Writable
import team.unnamed.creative.texture.Texture
import java.nio.charset.StandardCharsets
import kotlin.collections.set

@Suppress("DEPRECATION")
object TrimsCustomArmor : NexoDatapack("nexo_custom_armor", "Datapack for Nexos Custom Armor trims") {

    private val palleteKey: Key = Key.key("trims/color_palettes/trim_palette")
    private val permutations = linkedMapOf<String, Key>()

    init {
        datapackFile.deleteRecursively()

        permutations["quartz"] = Key.key("trims/color_palettes/quartz")
        permutations["iron"] = Key.key("trims/color_palettes/iron")
        permutations["gold"] = Key.key("trims/color_palettes/gold")
        permutations["diamond"] = Key.key("trims/color_palettes/diamond")
        permutations["netherite"] = Key.key("trims/color_palettes/netherite")
        permutations["redstone"] = Key.key("trims/color_palettes/redstone")
        permutations["copper"] = Key.key("trims/color_palettes/copper")
        permutations["emerald"] = Key.key("trims/color_palettes/emerald")
        permutations["lapis"] = Key.key("trims/color_palettes/lapis")
        permutations["amethyst"] = Key.key("trims/color_palettes/amethyst")
    }

    fun generateTrimAssets(resourcePack: ResourcePack) {
        val armorPrefixes = armorPrefixes(resourcePack)
        writeMCMeta()
        writeVanillaTrimPattern()
        writeCustomTrimPatterns(armorPrefixes)
        writeTrimAtlas(resourcePack, armorPrefixes)
        runCatching {
            copyArmorLayerTextures(resourcePack)
        }.onFailure {
            Logs.logError("Failed to copy armor-layer textures")
            if (Settings.DEBUG.toBool()) it.printStackTrace()
        }

        enableDatapack(true)

        if (isFirstInstall) {
            Logs.logError("Nexos's Custom-Armor datapack could not be found...")
            Logs.logWarn("The first time CustomArmor is enabled in settings.yml")
            Logs.logWarn("you need to restart your server so that the DataPack is enabled...")
            Logs.logWarn("Custom-Armor will not work, please restart your server once!", true)
        } else if (!datapackEnabled) {
            Logs.logError("Nexos's Custom-Armor datapack is not enabled...")
            Logs.logWarn("Custom-Armor will not work, please restart your server!", true)
        } else parseNexoArmorItems()
    }

    private fun writeTrimAtlas(resourcePack: ResourcePack, armorPrefixes: LinkedHashSet<String>) {
        val trimsAtlas = resourcePack.atlas(Key.key("armor_trims"))

        // If for some reason the atlas exists already, we append to it
        if (trimsAtlas != null) {
            val sources = ArrayList(trimsAtlas.sources())
            trimsAtlas.sources().forEach { source ->
                if (source !is PalettedPermutationsAtlasSource) return@forEach

                val textures = source.textures().toMutableList()
                armorPrefixes.forEach { armorPrefix ->
                    textures += Key.key("nexo:trims/entity/humanoid/$armorPrefix")
                    textures += Key.key("nexo:trims/models/armor/$armorPrefix")

                    textures += Key.key("nexo:trims/entity/humanoid_leggings/$armorPrefix")
                    textures += Key.key("nexo:trims/models/armor/${armorPrefix}_leggings")
                }

                textures += Key.key("minecraft:trims/entity/humanoid/chainmail")
                textures += Key.key("minecraft:trims/models/armor/chainmail")

                textures += Key.key("minecraft:trims/entity/humanoid_leggings/chainmail")
                textures += Key.key("minecraft:trims/models/armor/chainmail_leggings")

                sources.remove(source)
                sources += AtlasSource.palettedPermutations(textures, source.paletteKey(), source.permutations())
            }

            resourcePack.atlas(trimsAtlas.toBuilder().sources(sources).build())
        } else {
            val textures = mutableListOf<Key>()
            armorPrefixes.forEach { armorPrefix ->
                textures += Key.key("nexo:trims/entity/humanoid/$armorPrefix")
                textures += Key.key("nexo:trims/models/armor/$armorPrefix")

                textures += Key.key("nexo:trims/entity/humanoid_leggings/$armorPrefix")
                textures += Key.key("nexo:trims/models/armor/${armorPrefix}_leggings")
            }

            textures += Key.key("minecraft:trims/entity/humanoid/chainmail")
            textures += Key.key("minecraft:trims/models/armor/chainmail")

            textures += Key.key("minecraft:trims/entity/humanoid_leggings/chainmail")
            textures += Key.key("minecraft:trims/models/armor/chainmail_leggings")

            resourcePack.atlas(
                Atlas.atlas().key(Key.key("armor_trims")).addSource(
                    AtlasSource.palettedPermutations(textures.toList(), palleteKey, permutations)
                ).build()
            )
        }
    }

    private fun copyArmorLayerTextures(resourcePack: ResourcePack) {
        LinkedHashSet(resourcePack.textures()).forEach { armorTexture: Texture ->
            val (armorPrefix, armorKey) = armorPrefix(armorTexture) to armorTexture.key().asString()
            when {
                armorKey.endsWith("_armor_layer_1.png") -> {
                    resourcePack.texture(Key.key("nexo:trims/entity/humanoid/$armorPrefix.png"), armorTexture.data())
                    resourcePack.texture(Key.key("nexo:trims/models/armor/$armorPrefix.png"), armorTexture.data())
                }

                armorKey.endsWith("_armor_layer_2.png") -> {
                    resourcePack.texture(Key.key("nexo:trims/entity/humanoid_leggings/$armorPrefix.png"), armorTexture.data())
                    resourcePack.texture(Key.key("nexo:trims/models/armor/${armorPrefix}_leggings.png"), armorTexture.data())
                }
                else -> return@forEach
            }
        }

        fun handleChainmail(chainmail: Key, chainmail2: Key): Pair<Texture?, Texture?> {
            return (resourcePack.texture(chainmail) ?: DefaultResourcePackExtractor.vanillaResourcePack.texture(chainmail))?.also {
                resourcePack.texture(Key.key("trims/entity/humanoid/chainmail.png"), it.data())
                resourcePack.texture(Key.key("trims/models/armor/chainmail.png"), it.data())
            } to (resourcePack.texture(chainmail2) ?: DefaultResourcePackExtractor.vanillaResourcePack.texture(chainmail2))?.also {
                resourcePack.texture(Key.key("trims/entity/humanoid_leggings/chainmail.png"), it.data())
                resourcePack.texture(Key.key("trims/models/armor/chainmail_leggings.png"), it.data())
            }
        }

        // Find the key to vanilla layers based on version, then reuse for both formats
        when {
            VersionUtil.atleast("1.21.2") -> "minecraft:entity/equipment/humanoid%s/chainmail.png".let { Key.key(it.format("")) to Key.key(it.format("_leggings")) }
            else -> "minecraft:models/armor/chainmail_layer_%s.png".let { Key.key(it.format("1")) to Key.key(it.format("2")) }
        }.apply { handleChainmail(first, second) }

        val writable = NexoPlugin.instance().getResource("custom_armor/transparent.png")?.let(Writable::copyInputStream) ?: Writable.EMPTY
        // Replace chainmail layers for 1.20.4->1.21.1 with blanks
        "minecraft:models/armor/chainmail_layer_%s.png".let { Key.key(it.format("1")) to Key.key(it.format("2")) }.apply {
            resourcePack.texture(first, writable)
            resourcePack.texture(second, writable)
        }

        // Replace chainmail layers for 1.21.2+ with blanks
        "minecraft:entity/equipment/humanoid%s/chainmail.png".let { Key.key(it.format("")) to Key.key(it.format("_leggings")) }.apply {
            resourcePack.texture(first, writable)
            resourcePack.texture(second, writable)
        }
    }

    private fun parseNexoArmorItems() {
        // No need to log for all 4 armor pieces, so skip to minimise log spam
        val skippedArmorType = mutableListOf<String>()
        NexoItems.items().forEach { itemBuilder ->
            val itemID = NexoItems.idFromItem(itemBuilder)
            val itemStack = itemBuilder.build()
            val armorPrefix = StringUtils.substringBeforeLast(itemID, "_")
            var changed = false

            if (armorPrefix in skippedArmorType) return@forEach
            if (!Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(itemBuilder.type)) return@forEach
            if (!itemStack.hasItemMeta() || itemStack.itemMeta !is ArmorMeta) return@forEach
            if (!itemStack.type.name.startsWith("CHAINMAIL")) return@forEach

            if (ItemFlag.HIDE_ARMOR_TRIM !in itemBuilder.itemFlags()) {
                Logs.logWarn("Item $itemID does not have the HIDE_ARMOR_TRIM flag set.")

                if (Settings.CUSTOM_ARMOR_ASSIGN.toBool()) {
                    itemBuilder.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM)
                    changed = true
                    if (Settings.DEBUG.toBool()) Logs.logInfo("Assigned HIDE_ARMOR_TRIM flag to $itemID", true)
                } else Logs.logWarn("Custom Armors are recommended to have the HIDE_ARMOR_TRIM flag set.", true)
            }
            if (itemBuilder.equippable?.model != null) {
                Logs.logWarn("Item $itemID is using an equippable-component with model")
                Logs.logWarn("CustomArmor is set to be done via TRIMS, this will break it")
                Logs.logWarn("Recommend swapping to COMPONENT-type or remove model-field from item")
            }
            if (!itemBuilder.hasTrimPattern()) {
                val trimPattern = Registry.TRIM_PATTERN[NamespacedKey.fromString("nexo:$armorPrefix")!!]
                when {
                    trimPattern == null -> {
                        Logs.logError("Could not get trim-pattern for $itemID: nexo:$armorPrefix")
                        Logs.logWarn("Ensure that the  DataPack is enabled `/datapack list` and restart your server")
                        skippedArmorType += armorPrefix
                    }

                    !Settings.CUSTOM_ARMOR_ASSIGN.toBool() -> {
                        Logs.logWarn("Item $itemID does not have a trim pattern set.")
                        Logs.logWarn("Custom Armor will not work unless a trim pattern is set.", true)
                        skippedArmorType += armorPrefix
                    }

                    else -> {
                        itemBuilder.setTrimPattern(trimPattern.key())
                        changed = true
                        Logs.logWarn("Item $itemID does not have a trim pattern set.")
                        Logs.logInfo("Assigned trim pattern " + trimPattern.key().asString() + " to " + itemID, true)
                    }
                }
            }

            if (changed) itemBuilder.save()
        }
    }

    private fun armorPrefixes(resourcePack: ResourcePack) =
        LinkedHashSet(resourcePack.textures().map(::armorPrefix).filter(String::isNotBlank))

    private fun armorPrefix(texture: Texture): String {
        val textureKey = texture.key().asString()
        return when {
            textureKey.endsWith("_armor_layer_1.png") -> textureKey.substringBefore("_armor_layer_1.png").substringAfterLast("/")
            textureKey.endsWith("_armor_layer_2.png") -> textureKey.substringBefore("_armor_layer_2.png").substringAfterLast("/")
            else -> ""
        }
    }

    private fun writeVanillaTrimPattern() {
        val vanillaArmorJson = datapackFile.resolve("data/minecraft/trim_pattern/chainmail.json")
        vanillaArmorJson.parentFile.mkdirs()
        val vanillaTrimPattern = JsonObject()
        val description = JsonObject()
        description.addProperty("translate", "trim_pattern.minecraft.chainmail")
        vanillaTrimPattern.add("description", description)
        vanillaTrimPattern.addProperty("asset_id", "minecraft:chainmail")
        vanillaTrimPattern.addProperty("template_item", "minecraft:debug_stick")

        runCatching {
            vanillaArmorJson.createNewFile()
            FileUtils.writeStringToFile(vanillaArmorJson, vanillaTrimPattern.toString(), StandardCharsets.UTF_8)
        }.printOnFailure()
    }

    private fun writeCustomTrimPatterns(armorPrefixes: LinkedHashSet<String>) {
        armorPrefixes.forEach { armorPrefix ->
            val armorJson = datapackFile.resolve("data/nexo/trim_pattern/$armorPrefix.json")
            armorJson.parentFile.mkdirs()

            val trimPattern = JsonObject().apply {
                add("description", JsonObject().apply {
                    addProperty("translate", "trim_pattern.nexo.$armorPrefix")
                })
                addProperty("asset_id", "nexo:$armorPrefix")
                addProperty("template_item", "minecraft:debug_stick")
            }.toString()

            runCatching {
                armorJson.createNewFile()
                FileUtils.writeStringToFile(armorJson, trimPattern, StandardCharsets.UTF_8)
            }.printOnFailure()
        }
    }
}