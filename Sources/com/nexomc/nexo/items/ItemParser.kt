package com.nexomc.nexo.items

import com.jeff_media.morepersistentdatatypes.DataType
import com.mineinabyss.idofront.util.toColor
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.compatibilities.ecoitems.WrappedEcoItem
import com.nexomc.nexo.compatibilities.mmoitems.WrappedMMOItem
import com.nexomc.nexo.compatibilities.mythiccrucible.WrappedCrucibleItem
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.mechanics.MechanicsManager
import com.nexomc.nexo.utils.AdventureUtils
import com.nexomc.nexo.utils.AdventureUtils.setDefaultStyle
import com.nexomc.nexo.utils.NexoYaml.Companion.copyConfigurationSection
import com.nexomc.nexo.utils.PotionUtils
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.childSections
import com.nexomc.nexo.utils.filterFastIsInstance
import com.nexomc.nexo.utils.getStringOrNull
import com.nexomc.nexo.utils.logs.Logs
import com.nexomc.nexo.utils.printOnFailure
import com.nexomc.nexo.utils.safeCast
import com.nexomc.nexo.utils.wrappers.AttributeWrapper.fromString
import java.util.UUID
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.EnchantmentWrapper
import org.bukkit.inventory.ItemFlag
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect

class ItemParser(private val section: ConfigurationSection) {
    private val nexoMeta: NexoMeta
    private val itemId: String = section.name
    private val type: Material
    private var mmoItem: WrappedMMOItem? = null
    private var crucibleItem: WrappedCrucibleItem? = null
    private var ecoItem: WrappedEcoItem? = null
    private var templateItem: ItemParser? = null
    var isConfigUpdated = false
        private set

    init {

        if (section.isString("template")) templateItem = ItemTemplate.parserTemplate(section.getString("template")!!)

        section.getConfigurationSection("crucible")?.also { crucibleItem = WrappedCrucibleItem(it) }
            ?: section.getConfigurationSection("mmoitem")?.also { mmoItem = WrappedMMOItem(it) }
            ?: section.getConfigurationSection("ecoitem")?.also { ecoItem = WrappedEcoItem(it) }
            ?: (section.getStringOrNull("crucible_id") ?: section.getStringOrNull("crucible"))?.also { crucibleItem = WrappedCrucibleItem(it) }
            ?: (section.getStringOrNull("ecoitem_id") ?: section.getStringOrNull("ecoitem"))?.also { ecoItem = WrappedEcoItem(it) }

        type = section.getString("material")?.let { material ->
            Material.matchMaterial(material).also {
                if (it == null) Logs.logWarn("$itemId is using invalid material $material, defaulting to PAPER...")
            }
        } ?: templateItem?.type ?: Material.PAPER

        nexoMeta = templateItem?.nexoMeta?.copy() ?: NexoMeta()
        mergeWithTemplateSection().getConfigurationSection("Pack")?.also {
            nexoMeta.packInfo(it)
            nexoMeta.customModelData?.also { CUSTOM_MODEL_DATAS_BY_ID[itemId] = CustomModelData(type, nexoMeta, it) }
        }
    }

    val usesMMOItems: Boolean get() {
        return crucibleItem == null && ecoItem == null && mmoItem != null && mmoItem?.build() != null
    }

    val usesCrucibleItems: Boolean get() {
        return mmoItem == null && ecoItem == null && crucibleItem != null && crucibleItem?.build() != null
    }

    val usesEcoItems: Boolean get() {
        return mmoItem == null && crucibleItem == null && ecoItem != null && ecoItem?.build() != null
    }

    val usesTemplate: Boolean get() {
        return templateItem != null
    }

    fun buildItem(): ItemBuilder {
        val item = crucibleItem?.let(::ItemBuilder) ?: mmoItem?.let(::ItemBuilder) ?: ecoItem?.let(::ItemBuilder) ?: ItemBuilder(type)
        return applyConfig(templateItem?.applyConfig(item) ?: item)
    }

    private fun applyConfig(item: ItemBuilder): ItemBuilder {
        section.getString("itemname", section.getString("displayname"))?.let(AdventureUtils.MINI_MESSAGE::deserialize)?.let {
            if (VersionUtil.atleast("1.20.5")) {
                if ("displayname" in section) isConfigUpdated = true
                item.itemName(it)
            } else item.displayName(it)
        }

        section.getString("customname")?.let(AdventureUtils.MINI_MESSAGE::deserialize)?.let { customName ->
            if (VersionUtil.below("1.20.5")) isConfigUpdated = true
            item.displayName(customName)
        }

        if ("lore" in section) item.lore(section.getStringList("lore").map { AdventureUtils.MINI_MESSAGE.deserialize(it).setDefaultStyle() })
        if ("unbreakable" in section) item.setUnbreakable(section.getBoolean("unbreakable", false))
        if ("color" in section) item.setColor(section.getString("color", "#FFFFFF")!!.toColor())
        if ("trim_pattern" in section) item.setTrimPattern(Key.key(section.getString("trim_pattern", "")!!))

        ComponentParser(section, item).parseComponents()
        parseMiscOptions(item)
        parseVanillaSections(item)
        parseNexoSection(item)
        item.nexoMeta(nexoMeta)
        return item
    }

    private fun parseMiscOptions(item: ItemBuilder) {
        if (section.getBoolean("injectId", true)) item.customTag(NexoItems.ITEM_ID, PersistentDataType.STRING, itemId)

        nexoMeta.noUpdate = section.getBoolean("no_auto_update", false)
        nexoMeta.disableEnchanting = section.getBoolean("disable_enchanting", false)
        nexoMeta.excludedFromInventory = section.getBoolean("excludeFromInventory", false)
        nexoMeta.excludedFromCommands = section.getBoolean("excludeFromCommands", false)
    }

    @Suppress("DEPRECATION")
    private fun parseVanillaSections(item: ItemBuilder) {
        val section = mergeWithTemplateSection()

        if ("ItemFlags" in section) for (itemFlag in section.getStringList("ItemFlags"))
            item.addItemFlags(ItemFlag.valueOf(itemFlag))

        section.getList("PotionEffects")?.filterFastIsInstance<LinkedHashMap<String, Any>>()?.forEach {
            PotionUtils.getEffectType(it["type"].safeCast())?.also { v -> it["effect"] = v.key.key }
            item.addPotionEffect(PotionEffect(it))
        }

        runCatching {

            section.getList("PersistentData")?.filterFastIsInstance<LinkedHashMap<String, Any>>()?.forEach { attributeJson ->
                val key = (attributeJson["key"] as String).split(":").let { NamespacedKey(it.first(), it.last()) }

                // Resolve the PersistentDataType using reflection or a registry
                val dataTypeField = DataType::class.java.getDeclaredField(attributeJson["type"] as String)
                val dataType = dataTypeField.get(null).safeCast<PersistentDataType<Any, Any>>() ?: return@forEach
                val value = attributeJson["value"] ?: return@forEach

                item.customTag(key, dataType, value)
            }

        }.printOnFailure(true)

        section.getList("AttributeModifiers")?.filterFastIsInstance<LinkedHashMap<String, Any>>()?.forEach { attributeJson ->
            attributeJson.putIfAbsent("uuid", UUID.randomUUID().toString())
            attributeJson.putIfAbsent("name", "nexo:modifier")
            attributeJson.putIfAbsent("key", "nexo:modifier")
            val attributeModifier = AttributeModifier.deserialize(attributeJson)
            val attribute = fromString((attributeJson["attribute"] as String)) ?: return@forEach
            item.addAttributeModifiers(attribute, attributeModifier)
        }

        section.getConfigurationSection("Enchantments")?.getKeys(false)?.forEach { enchant: String ->
            item.addEnchant(
                EnchantmentWrapper.getByKey(NamespacedKey.minecraft(enchant)) ?: return@forEach,
                section.getConfigurationSection("Enchantments")!!.getInt(enchant)
            )
        }
    }

    private fun parseNexoSection(item: ItemBuilder) {
        val mechanicsSection = mergeWithTemplateSection().getConfigurationSection("Mechanics")

        mechanicsSection?.childSections()?.forEach { mechanicId, section ->
            val mechanic = MechanicsManager.getMechanicFactory(mechanicId)?.parse(section) ?: return@forEach
            for (modifier in mechanic.itemModifiers) modifier.apply(item)
        }

        if (!nexoMeta.containsPackInfo) return
        val customModelData = CUSTOM_MODEL_DATAS_BY_ID[section.name]?.customModelData
            ?: nexoMeta.takeIf { !item.hasItemModel() && !item.hasCustomModelDataComponent() }?.model?.let {
                CustomModelData.generateId(it, type).also { cmd ->
                    isConfigUpdated = true
                    if (!Settings.DISABLE_AUTOMATIC_MODEL_DATA.toBool())
                        section.getConfigurationSection("Pack")?.set("custom_model_data", cmd)
                }
            } ?: return

        item.customModelData(customModelData)
        nexoMeta.customModelData = customModelData
    }

    private fun mergeWithTemplateSection(): ConfigurationSection {
        if (templateItem == null) return section

        return YamlConfiguration().createSection(section.name).also {
            copyConfigurationSection(templateItem!!.section, it)
            copyConfigurationSection(section, it)
        }
    }

    companion object {
        val CUSTOM_MODEL_DATAS_BY_ID = mutableMapOf<String, CustomModelData>()
    }
}
