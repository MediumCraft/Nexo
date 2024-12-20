package com.nexomc.nexo.items

import com.mineinabyss.idofront.util.toColor
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.compatibilities.ecoitems.WrappedEcoItem
import com.nexomc.nexo.compatibilities.mythiccrucible.WrappedCrucibleItem
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.nms.NMSHandlers
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.logs.Logs
import net.Indyuce.mmoitems.MMOItems
import net.kyori.adventure.key.Key
import org.apache.commons.lang3.EnumUtils
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.damage.DamageType
import org.bukkit.entity.EntityType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemRarity
import org.bukkit.inventory.ItemStack
import org.bukkit.tag.DamageTypeTags

@Suppress("UnstableApiUsage")
class ComponentParser(section: ConfigurationSection, private val itemBuilder: ItemBuilder) {
    private val componentSection: ConfigurationSection? = section.getConfigurationSection("Components")
    private val itemId: String = section.name

    companion object {
        val usedInItemModel = mutableSetOf<Key>()
    }

    fun parseComponents() {
        if (componentSection == null || VersionUtil.below("1.20.5")) return

        if ("max_stack_size" in componentSection)
            itemBuilder.maxStackSize(componentSection.getInt("max_stack_size").coerceIn(1..99))

        if ("enchantment_glint_override" in componentSection)
            itemBuilder.setEnchantmentGlindOverride(componentSection.getBoolean("enchantment_glint_override"))

        if ("durability" in componentSection) {
            itemBuilder.isDamagedOnBlockBreak = componentSection.getBoolean("durability.damage_block_break")
            itemBuilder.isDamagedOnEntityHit = componentSection.getBoolean("durability.damage_entity_hit")
            itemBuilder.setDurability(componentSection.getInt("durability.value").coerceAtLeast(componentSection.getInt("durability", 1)))
        }
        if ("rarity" in componentSection) itemBuilder.setRarity(EnumUtils.getEnum(ItemRarity::class.java, componentSection.getString("rarity")))
        if ("fire_resistant" in componentSection) itemBuilder.setFireResistant(componentSection.getBoolean("fire_resistant"))
        if ("hide_tooltip" in componentSection) itemBuilder.setHideToolTip(componentSection.getBoolean("hide_tooltip"))

        componentSection.getConfigurationSection("food")?.let { food: ConfigurationSection ->
            NMSHandlers.handler().foodComponent(itemBuilder, food)
        }
        parseToolComponent()

        if (VersionUtil.below("1.21")) return

        componentSection.getConfigurationSection("jukebox_playable")?.let { jukeboxSection ->
            ItemStack(itemBuilder.type).itemMeta.jukeboxPlayable.also {
                it.isShowInTooltip = jukeboxSection.getBoolean("show_in_tooltip")
                it.songKey = jukeboxSection.getString("song_key")?.let(NamespacedKey::fromString) ?: return@also
            }.let(itemBuilder::setJukeboxPlayable)
        }

        if (VersionUtil.below("1.21.2")) return
        componentSection.getConfigurationSection("equippable")?.let { equippable: ConfigurationSection ->
            parseEquippableComponent(itemBuilder, equippable)
        }

        componentSection.getConfigurationSection("use_cooldown")?.let { cooldownSection: ConfigurationSection ->
                ItemStack(Material.PAPER).itemMeta.useCooldown.also {
                    val group = (cooldownSection.getString("group") ?: "nexo:${NexoItems.idFromItem(itemBuilder)}")
                    if (group.isNotEmpty()) it.cooldownGroup = NamespacedKey.fromString(group)
                    it.cooldownSeconds = cooldownSection.getDouble("seconds", 1.0).coerceAtLeast(0.0).toFloat()
                }.apply(itemBuilder::setUseCooldownComponent)
            }

        componentSection.getConfigurationSection("use_remainder")?.let { parseUseRemainderComponent(itemBuilder, it) }
        componentSection.getString("damage_resistant")?.let(NamespacedKey::fromString)?.let { damageResistantKey ->
            itemBuilder.setDamageResistant(Bukkit.getTag(DamageTypeTags.REGISTRY_DAMAGE_TYPES, damageResistantKey, DamageType::class.java))
        }

        componentSection.getString("tooltip_style")?.let(NamespacedKey::fromString)?.apply(itemBuilder::setTooltipStyle)

        componentSection.getString("item_model")?.let(NamespacedKey::fromString)?.also {
            usedInItemModel += Key.key(it.toString())
        }?.apply(itemBuilder::setItemModel)

        if ("enchantable" in componentSection) itemBuilder.setEnchantable(componentSection.getInt("enchantable"))
        if ("glider" in componentSection) itemBuilder.setGlider(componentSection.getBoolean("glider"))

        componentSection.getConfigurationSection("consumable")?.let { consumableSection ->
            NMSHandlers.handler().consumableComponent(itemBuilder, consumableSection)
        }

        val repairableWith = componentSection.getStringList("repairable").takeIf { it.isNotEmpty() } ?: listOf(componentSection.getString("repairable"))
        repairableWith.filterNotNull().takeIf { it.isNotEmpty() }?.let { repairable ->
            NMSHandlers.handler().repairableComponent(itemBuilder, repairable)
        }

        if (VersionUtil.below("1.21.4")) return
        componentSection.getConfigurationSection("custom_model_data")?.let { cmdSection ->
            ItemStack(itemBuilder.type).itemMeta.customModelDataComponent.also { cmdComponent ->
                cmdComponent.colors = cmdSection.getStringList("colors").mapNotNull { it.toColor() }
                cmdComponent.floats = cmdSection.getStringList("colors").mapNotNull { it.toFloatOrNull() }
                cmdComponent.strings = cmdSection.getStringList("strings").filterNotNull()
                cmdComponent.flags = cmdSection.getStringList("flags").mapNotNull { it.toBooleanStrictOrNull() }
            }.let(itemBuilder::setCustomModelDataComponent)
        }
    }

    private fun parseUseRemainderComponent(item: ItemBuilder, remainderSection: ConfigurationSection) {
        val amount = remainderSection.getInt("amount", 1)

        val result = when {
            "nexo_item" in remainderSection ->
                NexoItems.itemFromId(remainderSection.getString("nexo_item"))?.build()?.let(ItemUpdater::updateItem)
            "crucible_item" in remainderSection -> WrappedCrucibleItem(remainderSection.getString("crucible_item")).build()
            "mmoitems_id" in remainderSection && remainderSection.isString("mmoitems_type") ->
                MMOItems.plugin.getItem(remainderSection.getString("mmoitems_type"), remainderSection.getString("mmoitems_id"))

            "ecoitem_id" in remainderSection -> WrappedEcoItem(remainderSection.getString("ecoitem_id")).build()
            "minecraft_type" in remainderSection ->
                ItemStack(Material.getMaterial(remainderSection.getString("minecraft_type", "") ?: return) ?: return)
            else -> remainderSection.getItemStack("minecraft_item")
        }

        result?.amount = amount
        item.setUseRemainder(result)
    }

    private fun parseEquippableComponent(item: ItemBuilder, equippableSection: ConfigurationSection) {
        val equippableComponent = ItemStack(itemBuilder.type).itemMeta.equippable

        val slot = equippableSection.getString("slot")
        runCatching {
            equippableComponent.slot = EquipmentSlot.valueOf(slot!!)
        }.onFailure {
            Logs.logWarn("Error parsing equippable-component in $itemId...")
            Logs.logWarn("Invalid \"slot\"-value $slot")
            Logs.logWarn("Valid values are: ${EquipmentSlot.entries.joinToString()}")
        }.getOrNull() ?: return

        val entityTypes = equippableSection.getStringList("allowed_entity_types").mapNotNull { EnumUtils.getEnum(EntityType::class.java, it) }
        if ("allowed_entity_types" in equippableSection) equippableComponent.allowedEntities = entityTypes.ifEmpty { null }
        if ("damage_on_hurt" in equippableSection) equippableComponent.isDamageOnHurt = equippableSection.getBoolean("damage_on_hurt", true)
        if ("dispensable" in equippableSection) equippableComponent.isDispensable = equippableSection.getBoolean("dispensable", true)
        if ("swappable" in equippableSection) equippableComponent.isSwappable = equippableSection.getBoolean("swappable", true)

        equippableSection.getString("model")?.let(NamespacedKey::fromString)?.apply(equippableComponent::setModel)
        equippableSection.getString("camera_overlay")?.let(NamespacedKey::fromString)?.apply(equippableComponent::setCameraOverlay)
        equippableSection.getString("equip_sound")?.let(Key::key)?.let(Registry.SOUNDS::get)?.apply(equippableComponent::setEquipSound)

        item.setEquippableComponent(equippableComponent)
    }

    private fun parseToolComponent() {
        val toolSection = componentSection!!.getConfigurationSection("tool") ?: return
        val toolComponent = ItemStack(Material.PAPER).itemMeta.tool
        toolComponent.damagePerBlock = toolSection.getInt("damage_per_block", 1).coerceAtLeast(0)
        toolComponent.defaultMiningSpeed = toolSection.getDouble("default_mining_speed", 1.0).toFloat().coerceAtLeast(0f)

        for (ruleEntry: Map<*, *> in toolSection.getMapList("rules")) {
            val speed = ruleEntry["speed"]?.toString()?.toFloatOrNull() ?: 1f
            val correctForDrops = ruleEntry["correct_for_drops"]?.toString()?.toBoolean()
            val materials = mutableSetOf<Material>()
            val tags = mutableSetOf<Tag<Material>>()

            runCatching {
                val material = Material.valueOf(ruleEntry["material"]?.toString() ?: return@runCatching)
                if (material.isBlock) materials += material
            }.onFailure {
                Logs.logWarn("Error parsing rule-entry in $itemId")
                Logs.logWarn("Malformed \"material\"-section")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
            }

            runCatching {
                val materialIds = ruleEntry["materials"] as? List<*> ?: return@runCatching
                materialIds.asSequence().filterIsInstance<String>().mapNotNull(Material::matchMaterial)
                    .filter { it.isBlock }.forEach { materials += it }
            }.onFailure {
                Logs.logWarn("Error parsing rule-entry in $itemId")
                Logs.logWarn("Malformed \"materials\"-section")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
            }

            runCatching {
                val tagKey = ruleEntry["tag"]?.toString()?.let(NamespacedKey::fromString) ?: return@runCatching
                tags += Bukkit.getTag(Tag.REGISTRY_BLOCKS, tagKey, Material::class.java)!!
            }.onFailure {
                Logs.logWarn("Error parsing rule-entry in $itemId")
                Logs.logWarn("Malformed \"tag\"-section")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
            }

            runCatching {
                (ruleEntry["tags"] as? List<*>)?.filterIsInstance<String>()?.forEach { tagString: String ->
                    val tagKey = NamespacedKey.fromString(tagString) ?: return@forEach
                    tags += Bukkit.getTag(Tag.REGISTRY_BLOCKS, tagKey, Material::class.java) ?: return@forEach
                }
            }.onFailure {
                Logs.logWarn("Error parsing rule-entry in $itemId")
                Logs.logWarn("Malformed \"material\"-section")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
            }

            if (materials.isNotEmpty()) toolComponent.addRule(materials, speed, correctForDrops)
            for (tag in tags) toolComponent.addRule(tag, speed, correctForDrops)
        }

        itemBuilder.setToolComponent(toolComponent)
    }
}