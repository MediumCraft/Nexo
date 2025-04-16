package com.nexomc.nexo.configs

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.compatibilities.mmoitems.WrappedMMOItem
import com.nexomc.nexo.compatibilities.mythiccrucible.WrappedCrucibleItem
import com.nexomc.nexo.converter.NexoConverter
import com.nexomc.nexo.converter.OraxenConverter
import com.nexomc.nexo.fonts.Glyph
import com.nexomc.nexo.fonts.ReferenceGlyph
import com.nexomc.nexo.items.CustomModelData
import com.nexomc.nexo.items.ItemBuilder
import com.nexomc.nexo.items.ItemParser
import com.nexomc.nexo.items.ItemTemplate
import com.nexomc.nexo.utils.AdventureUtils
import com.nexomc.nexo.utils.AdventureUtils.tagResolver
import com.nexomc.nexo.utils.KeyUtils
import com.nexomc.nexo.utils.NexoYaml
import com.nexomc.nexo.utils.NexoYaml.Companion.loadConfiguration
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.associateFastWith
import com.nexomc.nexo.utils.childSections
import com.nexomc.nexo.utils.filterFast
import com.nexomc.nexo.utils.logs.Logs
import com.nexomc.nexo.utils.mapFast
import com.nexomc.nexo.utils.printOnFailure
import com.nexomc.nexo.utils.toFastList
import com.nexomc.nexo.utils.toIntRangeOrNull
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import java.io.File
import java.io.InputStreamReader
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigsManager(private val plugin: JavaPlugin) {
    private var settings: YamlConfiguration? = null
    var mechanics: YamlConfiguration = defaultMechanics
        private set
    var sounds: YamlConfiguration = defaultSounds
        private set
    var language: YamlConfiguration = defaultLanguage
        private set

    fun settings(): YamlConfiguration {
        if (settings == null) settings = Settings.validateSettings()
        return settings!!
    }

    fun settingsFile() = File(plugin.dataFolder, "settings.yml")

    fun validatesConfig() {
        val resourceManager = NexoPlugin.instance().resourceManager()
        settings = Settings.validateSettings()
        mechanics = validate(resourceManager, "mechanics.yml", defaultMechanics)
        sounds = validate(resourceManager, "sounds.yml", defaultSounds)
        plugin.dataFolder.resolve("languages").mkdir()
        language = validate(resourceManager, "languages/${Settings.PLUGIN_LANGUAGE}.yml", defaultLanguage)
        AdventureUtils.reload()

        if (itemsFolder.list().isNullOrEmpty() && Settings.GENERATE_DEFAULT_CONFIGS.toBool()) resourceManager.extractConfigsInFolder("items", "yml")

        if (glyphsFolder.list().isNullOrEmpty()) {
            if (Settings.GENERATE_DEFAULT_CONFIGS.toBool()) resourceManager.extractConfigsInFolder("glyphs", "yml")
            else resourceManager.extractConfiguration("glyphs/nexo_defaults/interface.yml")
        }

        if (!schematicsFolder.exists() && Settings.GENERATE_DEFAULT_CONFIGS.toBool())
            resourceManager.extractConfigsInFolder("schematics", "schem")
    }

    private fun validate(
        resourcesManager: ResourceManager,
        configName: String,
        defaultConfiguration: YamlConfiguration
    ): YamlConfiguration {
        val configurationFile = resourcesManager.extractConfiguration(configName)
        val configuration = loadConfiguration(configurationFile)
        var updated = false

        defaultConfiguration.getKeys(true).forEach { key: String ->
            if (key.startsWith(Settings.NEXO_INV_LAYOUT.path) || configuration.get(key) != null) return@forEach
            updated = true
            Message.UPDATING_CONFIG.log(tagResolver("option", key))
            configuration.set(key, defaultConfiguration.get(key))
        }

        configuration.getKeys(true).filter { it in removedYamlKeys }.forEach { key: String ->
            updated = true
            Message.REMOVING_CONFIG.log(tagResolver("option", key))
            configuration.set(key, null)
        }

        configuration.getKeys(false).associateWith { movedYamlKeys[it] }.forEach { old, new ->
            if (new == null) return@forEach
            updated = true
            Message.UPDATING_CONFIG.log(tagResolver("option", new))
            configuration.set(new, configuration.get(old))
            configuration.set(old, null)
        }

        runCatching {
            if (updated) configuration.save(configurationFile)
        }.onFailure {
            Logs.logError("Failed to save updated configuration file: ${configurationFile.name}")
            if (Settings.DEBUG.toBool()) it.printStackTrace()
        }
        return configuration
    }

    fun parseGlyphConfigs(): Collection<Glyph> {
        val output = mutableListOf<Glyph>()
        val referenceGlyphs = mutableMapOf<String, ConfigurationSection>()
        Glyph.assignedGlyphUnicodes.clear()

        glyphFiles().associateWith(::loadConfiguration).apply {
            entries.flatMap { it.value.childSections().entries }.forEach { (glyphId, glyphSection) ->
                // Reference glyphs do not contain unicodes as they link to a normal glyph only
                if (glyphSection.contains("reference")) referenceGlyphs[glyphId] = glyphSection
                else Glyph.assignedGlyphUnicodes[glyphId] = Glyph.definedUnicodes(glyphSection) ?: return@forEach
            }
        }.onEach { (file, configuration) ->
            var fileChanged = false

            configuration.childSections().entries.onEach { (glyphId, glyphSection) ->
                // Skip reference-glyphs until all normal glyphs are parsed
                if (glyphId in referenceGlyphs) return@onEach

                runCatching {
                    if (!fileChanged || glyphId !in Glyph.assignedGlyphUnicodes) fileChanged = true
                    output += Glyph(glyphSection)
                }.onFailure {
                    Logs.logWarn("Failed to load glyph $glyphId")
                    if (Settings.DEBUG.toBool()) it.printStackTrace()
                }
            }.onEach { (referenceId, _) ->
                if (referenceId !in referenceGlyphs) return@onEach
                val referenceSection = referenceGlyphs[referenceId] ?: return@onEach
                val index = referenceSection.getString("index")?.toIntRangeOrNull() ?: return@onEach
                val glyphId = referenceSection.getString("reference") ?: return@onEach
                val glyph = output.find { it.id == glyphId } ?: return@onEach Logs.logError("Reference-Glyph $referenceId tried referencing a Glyph $glyphId, but it does not exist...")
                val permission = referenceSection.getString("permission") ?: glyph.permission
                val unicodes = glyph.unicodes.joinToString("")

                if (unicodes.length <= index.last || index.first <= 0) {
                    return@onEach Logs.logError("Reference-Glyph $referenceId used invalid index $index, $glyphId has indexes ${1 until unicodes.length}")
                }

                output += ReferenceGlyph(glyph, referenceId, index, permission)
            }

            if (fileChanged && !Settings.DISABLE_AUTOMATIC_GLYPH_CODE.toBool()) runCatching {
                configuration.save(file)
            }.onFailure {
                Logs.logWarn("Failed to save updated glyph file: ${file.name}")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
            }
        }

        runCatching {
            output += com.nexomc.nexo.fonts.RequiredGlyph
        }.printOnFailure()

        return output
    }

    internal fun parseItemConfig(): Object2ObjectLinkedOpenHashMap<File, Object2ObjectLinkedOpenHashMap<String, ItemBuilder>> {
        return Object2ObjectLinkedOpenHashMap<File, Object2ObjectLinkedOpenHashMap<String, ItemBuilder>>().apply {
            for (file: File in itemFiles()) this[file] = parseItemConfig(file)
        }
    }

    fun assignAllUsedCustomModelDatas() {
        val assignedModelDatas = Object2ObjectLinkedOpenHashMap<Material, Object2ObjectLinkedOpenHashMap<Int, Key>>()
        itemFiles().forEach file@{ file ->
            val config = loadConfiguration(file)

            config.getKeys(false).associateFastWith { config.getConfigurationSection(it) }.forEach { (itemId, itemSection) ->
                val packSection = itemSection?.getConfigurationSection("Pack") ?: return@forEach
                val material = Material.getMaterial(itemSection.getString("material", "")!!)
                    ?: WrappedCrucibleItem(itemSection).material
                    ?: WrappedMMOItem(itemSection, true).material
                    ?: return@forEach

                validatePackSection(itemId, packSection)
                val modelData = packSection.getInt("custom_model_data", -1).takeUnless { it == -1 } ?: return@forEach
                val model = (packSection.getString("model")?.takeUnless(String::isNullOrEmpty) ?: itemId).takeIf(Key::parseable)?.let(Key::key) ?: KeyUtils.MALFORMED_KEY_PLACEHOLDER

                CustomModelData.DATAS[material]?.entries?.find { it.value == modelData && it.key != model }?.key?.asString()?.also { existingModel ->
                    Logs.logError("<red>$itemId</red> in <red>${file.path}</red> is using CustomModelData <yellow>$modelData</yellow>, which is already assigned to <red>$existingModel")
                } ?: also {
                    assignedModelDatas.getOrPut(material) { Object2ObjectLinkedOpenHashMap<Int, Key>() }[modelData] = model
                    CustomModelData.DATAS.getOrPut(material) { Object2ObjectLinkedOpenHashMap() }[model] = modelData
                }
            }
        }
    }

    fun parseAllItemTemplates() {
        itemFiles().mapFast(::loadConfiguration).forEach { configuration ->
            configuration.childSections().values.filterFast { it.isBoolean("template") }.forEach(ItemTemplate::register)
        }
    }

    private fun validatePackSection(itemId: String, packSection: ConfigurationSection) {
        val model = packSection.getString("model")?.takeUnless { it.isEmpty() } ?: itemId

        if (!Key.parseable(model)) {
            Logs.logWarn("Found invalid model in NexoItem <blue>$itemId</blue>: <aqua>$model")
            Logs.logWarn("Model-paths must only contain characters <yellow>[a-z0-9/._-]")
        }

        packSection.getStringList("textures").forEach { texture: String ->
            if (Key.parseable(texture)) return@forEach
            Logs.logWarn("Found invalid texture in NexoItem <blue>$itemId</blue>: <aqua>$texture")
            Logs.logWarn("Texture-paths must only contain characters <yellow>[a-z0-9/._-]")
        }
    }

    private val ERROR_ITEM by lazy { ItemBuilder(Material.PODZOL) }
    private fun parseItemConfig(itemFile: File): Object2ObjectLinkedOpenHashMap<String, ItemBuilder> {
        if (NexoPlugin.instance().converter().oraxenConverter.convertItems) OraxenConverter.processItemConfigs(itemFile)
        NexoConverter.processItemConfigs(itemFile)
        val config = loadConfiguration(itemFile)
        val parseMap = Object2ObjectLinkedOpenHashMap<String, ItemParser>()

        config.childSections().forEach { itemId, section ->
            if (!ItemTemplate.isTemplate(itemId)) parseMap[itemId] = ItemParser(section)
        }

        var configUpdated = false
        val map = Object2ObjectLinkedOpenHashMap<String, ItemBuilder>()
        parseMap.forEach { (itemId, itemParser) ->
            map[itemId] = runCatching {
                itemParser.buildItem()
            }.onFailure {
                Logs.logError("ERROR BUILDING ITEM \"$itemId\" from file ${itemFile.path}")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
                else it.message?.let(Logs::logWarn)
            }.getOrNull() ?: ERROR_ITEM.itemName(Component.text(itemId))
            if (itemParser.isConfigUpdated) configUpdated = true
        }

        if (configUpdated) {
            config.childSections().forEach { _, section ->
                when {
                    VersionUtil.atleast("1.20.5") -> section.getString("displayname")?.also {
                        section.set("itemname", it)
                        section.set("displayname", null)
                    }
                    else -> section.getString("itemname")?.also {
                        section.set("displayname", it)
                        section.set("itemname", null)
                    }
                }
                section.getMapList("AttributeModifiers").takeUnless { it.isEmpty() }?.map {
                    it.remove("key")
                    it.remove("name")
                    it.remove("uuid")
                }
            }

            runCatching {
                config.save(itemFile)
            }.printOnFailure(true)
        }

        return map
    }

    private fun itemFiles(): List<File> = itemsFolder.walkBottomUp().filter { it.extension == "yml" && it.readText().isNotEmpty() }.filter(NexoYaml::isValidYaml).sorted().toFastList()

    private fun glyphFiles(): List<File> = glyphsFolder.walkBottomUp().filter { it.extension == "yml" && it.readText().isNotEmpty() }.filter(NexoYaml::isValidYaml).sorted().toFastList()

    companion object {
        private val defaultMechanics: YamlConfiguration = extractDefault("mechanics.yml")
        private val defaultSounds: YamlConfiguration = extractDefault("sounds.yml")
        private val defaultLanguage: YamlConfiguration = extractDefault("languages/english.yml")
        private val itemsFolder: File = NexoPlugin.instance().dataFolder.resolve("items")
        private val glyphsFolder: File = NexoPlugin.instance().dataFolder.resolve("glyphs")
        val schematicsFolder: File = NexoPlugin.instance().dataFolder.resolve("schematics")

        private fun extractDefault(source: String): YamlConfiguration {
            return NexoPlugin.instance().getResource(source)?.use {
                runCatching {
                    YamlConfiguration.loadConfiguration(InputStreamReader(it))
                }.onFailure {
                    Logs.logError("Failed to extract default file: $source")
                    if (Settings.DEBUG.toBool()) it.printStackTrace()
                }.getOrNull()
            } ?: YamlConfiguration()
        }

        private val movedYamlKeys = mapOf(
            "noteblock" to "custom_blocks.noteblock",
            "stringblock" to "custom_blocks.stringblock",
            "chorusblock" to "custom_blocks.chorusblock",
        )

        private val removedYamlKeys = listOf(
            "armorpotioneffects",
            "consumable_potion_effects",
            "consumable",
            "music_disc",
            "durability",
            "efficiency",
            "aura",
            "hat",
            "skin",
            "skinnable",
            "lifeleech",
            "bigmining",
            "bottledexp",
            "bedrockbreak",
            "watering",
            "block",
            "repair.oraxen_durability_only",
            "noteblock.custom_block_sounds",
            "stringblock.custom_block_sounds",
            "chorusblock.custom_block_sounds",
            "noteblock.tool_types",
            "stringblock.tool_types",
            "chorusblock.tool_types",
            "detect_viabackwards"
        )
    }
}
