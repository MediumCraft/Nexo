package com.nexomc.nexo.configs

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.fonts.FontListener.ChatHandler
import com.nexomc.nexo.nms.GlyphHandlers
import com.nexomc.nexo.pack.PackObfuscator
import com.nexomc.nexo.pack.server.PackServerType
import com.nexomc.nexo.utils.AdventureUtils
import com.nexomc.nexo.utils.EnumUtils.toEnumOrElse
import com.nexomc.nexo.utils.NexoYaml.Companion.loadConfiguration
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.customarmor.CustomArmorType
import com.nexomc.nexo.utils.logs.Logs
import net.kyori.adventure.text.Component
import org.apache.commons.lang3.EnumUtils
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration

enum class Settings {
    // Generic Plugin stuff
    DEBUG("debug", false, "test"),
    PLUGIN_LANGUAGE("Plugin.language", "english"),
    KEEP_UP_TO_DATE("Plugin.keep_this_up_to_date", true),
    FORMAT_ANVIL("Plugin.formatting.anvil", true),
    FORMAT_SIGNS("Plugin.formatting.signs", true),
    FORMAT_CHAT("Plugin.formatting.chat", true),
    FORMAT_BOOKS("Plugin.formatting.books", true),

    // WorldEdit
    WORLDEDIT_NOTEBLOCKS("WorldEdit.noteblock_mechanic", false),
    WORLDEDIT_STRINGBLOCKS("WorldEdit.stringblock_mechanic", false),
    WORLDEDIT_FURNITURE("WorldEdit.furniture_mechanic", false),

    // Glyphs
    SHOW_PERMISSION_EMOJIS("Glyphs.emoji_list_permission_only", true),
    UNICODE_COMPLETIONS("Glyphs.unicode_completions", true),
    GLYPH_HOVER_TEXT("Glyphs.chat_hover_text", "<glyph_placeholder>"),

    // Chat
    CHAT_HANDLER("Chat.chat_handler", if (VersionUtil.isPaperServer) ChatHandler.MODERN.name else ChatHandler.LEGACY.name),

    // Config Tools
    GENERATE_DEFAULT_CONFIGS("ConfigTools.generate_default_configs", true),
    DISABLE_AUTOMATIC_MODEL_DATA("ConfigTools.disable_automatic_model_data", false),
    DISABLE_AUTOMATIC_GLYPH_CODE("ConfigTools.disable_automatic_glyph_code", false),
    INITIAL_CUSTOM_MODEL_DATA("ConfigTools.initial_custom_model_data", 1000),
    SKIPPED_MODEL_DATA_NUMBERS("ConfigTools.skipped_model_data_numbers", emptyList<Int>()),
    ERROR_ITEM("ConfigTools.error_item", mapOf("material" to Material.PODZOL.name, "excludeFromInventory" to false, "injectId" to false)),
    REMOVE_INVALID_FURNITURE("ConfigTools.remove_invalid_furniture", false),

    // Custom Armor
    CUSTOM_ARMOR_TYPE("CustomArmor.type", if (VersionUtil.atleast("1.21.2")) CustomArmorType.COMPONENT.name else CustomArmorType.TRIMS.name),
    CUSTOM_ARMOR_ASSIGN("CustomArmor.auto_assign_settings", true),

    // ItemUpdater
    UPDATE_ITEMS("ItemUpdater.update_items", true),
    UPDATE_ITEMS_ON_RELOAD("ItemUpdater.update_items_on_reload", true),
    OVERRIDE_ITEM_LORE("ItemUpdater.override_item_lore", false),

    //Misc
    RESET_RECIPES("Misc.reset_recipes", true),
    ADD_RECIPES_TO_BOOK("Misc.add_recipes_to_book", true),
    HIDE_SCOREBOARD_NUMBERS("Misc.hide_scoreboard_numbers", false),
    HIDE_SCOREBOARD_BACKGROUND("Misc.hide_scoreboard_background", false),
    HIDE_TABLIST_BACKGROUND("Misc.hide_tablist_background", false),
    BLOCK_OTHER_RESOURCEPACKS("Misc.block_other_resourcepacks", false),

    //Pack
    PACK_GENERATE_ZIP("Pack.generation.generate_zip", true),
    PACK_OBFUSCATION_TYPE("Pack.obfuscation.type", PackObfuscator.PackObfuscationType.SIMPLE.name),
    PACK_CACHE_OBFUSCATION("Pack.obfuscation.cache", true),
    PACK_IMPORT_DEFAULT("Pack.import.default_assets", true),
    PACK_IMPORT_EXTERNAL("Pack.import.external_packs", true),
    PACK_IMPORT_FROM_LOCATION("Pack.import.from_location", listOf<String>()),
    PACK_IMPORT_EXTERNAL_PACK_ORDER("Pack.import.external_pack_order", listOf<String>()),
    PACK_IMPORT_MODEL_ENGINE("Pack.import.modelengine", true),
    PACK_EXCLUDED_FILE_EXTENSIONS("Pack.generation.excluded_file_extensions", listOf(".zip", ".tar.gz")),

    PACK_VALIDATE_MODELS("Pack.validate.models", true),
    PACK_VALIDATE_FONTS("Pack.validate.fonts", true),
    PACK_VALIDATE_ATLAS("Pack.validate.atlas", true),

    PACK_SERVER_TYPE("Pack.server.type", PackServerType.POLYMATH.name),
    SELFHOST_PACK_SERVER_PORT("Pack.server.selfhost.port", 8082),
    SELFHOST_PUBLIC_ADDRESS("Pack.server.selfhost.public_address"),
    SELFHOST_DISPATCH_THREADS("Pack.server.selfhost.dispatch_threads", 10),
    POLYMATH_SERVER("Pack.server.polymath.server", "atlas.nexomc.com"),
    POLYMATH_SECRET("Pack.server.polymath.secret", "nexomc"),

    PACK_SEND_PRE_JOIN("Pack.dispatch.send_pre_join", VersionUtil.atleast("1.21") && VersionUtil.isPaperServer),
    PACK_SEND_ON_JOIN("Pack.dispatch.send_on_join", !VersionUtil.isPaperServer || VersionUtil.below("1.21")),
    PACK_SEND_RELOAD("Pack.dispatch.send_on_reload", true),
    PACK_SEND_DELAY("Pack.dispatch.delay", -1),
    PACK_SEND_MANDATORY("Pack.dispatch.mandatory", true),
    PACK_SEND_PROMPT("Pack.dispatch.prompt", "<#fa4943>Accept the pack to enjoy a full <b><gradient:#9055FF:#13E2DA>Nexo</b><#fa4943> experience"),


    // Inventory
    NEXO_INV_LAYOUT("NexoInventory.menu_layout", mapOf(
        "nexo_armor" to mapOf(
            "slot" to 1,
            "icon" to "forest_helmet",
            "displayname" to "<green>Nexo Armor</green>"
        ),
        "nexo_furniture" to mapOf(
            "slot" to 2,
            "icon" to "arm_chair",
            "displayname" to "<green>Nexo Furniture</green>"
        ),
        "nexo_tools" to mapOf(
            "slot" to 3,
            "icon" to "forest_axe",
            "displayname" to "<green>Nexo Tools</green>"
        )
    )),
    NEXO_INV_ROWS("NexoInventory.menu_rows", 6),
    NEXO_INV_SIZE("NexoInventory.menu_size", 45),
    NEXO_INV_TITLE("NexoInventory.main_menu_title", "<shift:-37><glyph:menu_items>"),
    NEXO_INV_NEXT_ICON("NexoInventory.next_page_icon", "next_page_icon"),
    NEXO_INV_PREVIOUS_ICON("NexoInventory.previous_page_icon", "previous_page_icon"),
    NEXO_INV_EXIT("NexoInventory.exit_icon", "cancel_icon"),
    NEXO_RECIPE_SHOWCASE_TITLE("NexoInventory.recipe_showcase_title", "<shift:-7><glyph:menu_recipe>")
    ;

    val path: String
    private val defaultValue: Any?
    private var comments = listOf<String>()
    private var inlineComments = listOf<String>()
    private var richComment: Component = Component.empty()

    constructor(path: String) {
        this.path = path
        this.defaultValue = null
    }

    constructor(path: String, defaultValue: Any?) {
        this.path = path
        this.defaultValue = defaultValue
    }

    constructor(path: String, defaultValue: Any?, vararg comments: String) {
        this.path = path
        this.defaultValue = defaultValue
        this.comments = listOf(*comments)
    }

    constructor(path: String, defaultValue: Any?, comments: List<String>, vararg inlineComments: String) {
        this.path = path
        this.defaultValue = defaultValue
        this.comments = comments
        this.inlineComments = listOf(*inlineComments)
    }

    constructor(path: String, defaultValue: Any?, richComment: Component) {
        this.path = path
        this.defaultValue = defaultValue
        this.richComment = richComment
    }

    var value: Any?
        get() = NexoPlugin.instance().configsManager().settings().get(path)
        set(value) {
            setValue(value, true)
        }

    fun setValue(value: Any?, save: Boolean) {
        val settingFile = NexoPlugin.instance().configsManager().settings()
        settingFile.set(path, value)
        runCatching {
            if (save) settingFile.save(NexoPlugin.instance().resourceManager().settingsEntry().file)
        }.onFailure {
            Logs.logError("Failed to apply changes to settings.yml")
        }
    }

    override fun toString() = value.toString()

    fun toString(optionalDefault: String) = value as? String ?: optionalDefault

    fun <E : Enum<E>> toEnum(enumClass: Class<E>, defaultValue: E): E =
        EnumUtils.getEnum(enumClass, toString().uppercase(), defaultValue)

    fun <E : Enum<E>> toEnumOrGet(
        enumClass: Class<E>,
        fallback: (String) -> E
    ): E = toString().toEnumOrElse(enumClass, fallback)

    fun toComponent() = AdventureUtils.MINI_MESSAGE.deserialize(value.toString())

    fun toInt() = toInt(-1)

    /**
     * @param optionalDefault value to return if the path is not an integer
     * @return the value of the path as an int, or the default value if the path is not found
     */
    fun toInt(optionalDefault: Int) = runCatching {
        value.toString().toIntOrNull()
    }.getOrNull() ?: optionalDefault

    fun toBool(defaultValue: Boolean) = value as? Boolean ?: defaultValue

    fun toBool() = value as? Boolean ?: false

    fun toStringList() = NexoPlugin.instance().configsManager().settings().getStringList(path)

    fun toConfigSection() = NexoPlugin.instance().configsManager().settings().getConfigurationSection(path)

    companion object {
        fun validateSettings(): YamlConfiguration {
            val settingsFile = NexoPlugin.instance().dataFolder.toPath().resolve("settings.yml").toFile()
            val settings = if (settingsFile.exists()) loadConfiguration(settingsFile) else YamlConfiguration()

            settings.options().copyDefaults(true).indent(2).parseComments(true)
            settings.addDefaults(defaultSettings())

            runCatching {
                settingsFile.createNewFile()
                settings.save(settingsFile)
            }.onFailure {
                if (settings.getBoolean("debug")) it.printStackTrace()
            }

            return settings
        }

        private fun defaultSettings(): YamlConfiguration {
            val defaultSettings = YamlConfiguration()
            defaultSettings.options().copyDefaults(true).indent(4).parseComments(true)

            entries.forEach { setting: Settings ->
                defaultSettings.set(setting.path, setting.defaultValue)
                defaultSettings.setComments(setting.path, setting.comments)
                defaultSettings.setInlineComments(setting.path, setting.inlineComments)
            }

            return defaultSettings
        }
    }
}