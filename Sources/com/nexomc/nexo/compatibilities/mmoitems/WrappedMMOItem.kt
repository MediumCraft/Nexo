package com.nexomc.nexo.compatibilities.mmoitems

import com.nexomc.nexo.utils.PluginUtils.isEnabled
import com.nexomc.nexo.utils.logs.Logs
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.api.ItemTier
import net.Indyuce.mmoitems.api.Type
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

class WrappedMMOItem(
    private var type: Type? = null,
    private var id: String? = null,
    private var level: Int = 0,
    private var tier: ItemTier? = null,
    var cache: Boolean = true,
) {

    constructor(section: ConfigurationSection) : this() {
        when {
            !isEnabled("MMOItems") -> Logs.logError("MMOItems is not installed")
            else -> {
                type = MMOItems.plugin.types.get(section.getString("type"))
                id = section.getString("id")

                // Check if template exists
                if (!MMOItems.plugin.templates.hasTemplate(type, id)) {
                    Logs.logError("Failed to load MMOItem $id")
                    Logs.logError("Template does not exist")
                }

                level = section.getInt("level", 1)
                tier = MMOItems.plugin.tiers[section.getString("tier")]
                cache = section.getBoolean("cache", true)
            }
        }
    }

    private fun template(): MMOItemTemplate? {
        return MMOItems.plugin.templates.getTemplate(type, id) ?: run {
            Logs.logError("Failed to load MMOItem $id, Template does not exist")
            null
        }
    }

    fun build(): ItemStack? {
        if (isEnabled("MMOItems")) {
            template()?.let { templateItem ->
                return templateItem.newBuilder().build().newBuilder().build()
            } ?: Logs.logError("Failed to load MMOItem $id, Item does not exist")
        } else Logs.logWarn("MMOItems is not installed")
        return null
    }
}
