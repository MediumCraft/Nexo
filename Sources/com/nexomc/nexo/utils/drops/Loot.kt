package com.nexomc.nexo.utils.drops

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.compatibilities.ecoitems.WrappedEcoItem
import com.nexomc.nexo.compatibilities.mythiccrucible.WrappedCrucibleItem
import com.nexomc.nexo.items.ItemUpdater
import com.nexomc.nexo.utils.safeCast
import com.nexomc.nexo.utils.toIntRangeOrNull
import net.Indyuce.mmoitems.MMOItems
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.math.min
import kotlin.random.Random

class Loot(
    val sourceID: String? = null,
    var itemStack: ItemStack? = null,
    val probability: Double,
    val amount: IntRange,
    val inExplosion: Boolean = false,
    var config: LinkedHashMap<String, Any> = linkedMapOf()
) {

    constructor(config: LinkedHashMap<String, Any>, sourceID: String) : this(
        probability = config.getOrDefault("probability", 1).toString().toDouble(),
        amount = (config.getOrDefault("amount", "") as? String)?.toIntRangeOrNull() ?: IntRange(1, 1),
        inExplosion = config.getOrDefault("in_explosion", false).safeCast<Boolean>() ?: false,
        config = config,
        sourceID = sourceID
    )

    constructor(itemStack: ItemStack?, probability: Double)
            : this(null, itemStack, min(1.0, probability), IntRange(1, 1))

    constructor(sourceID: String?, itemStack: ItemStack?, probability: Double, minAmount: Int, maxAmount: Int)
            : this(sourceID, itemStack, min(1.0, probability), IntRange(minAmount, maxAmount))

    fun itemStack(): ItemStack {
        if (itemStack != null) return ItemUpdater.updateItem(itemStack!!)

        when {
            "nexo_item" in config ->
                itemStack = NexoItems.itemFromId(config["nexo_item"].toString())!!.build()

            "crucible_item" in config ->
                itemStack = WrappedCrucibleItem(config["crucible_item"].toString()).build()

            "mmoitems_id" in config && "mmoitems_type" in config -> {
                val type = config["mmoitems_type"].toString()
                val id = config["mmoitems_id"].toString()
                itemStack = MMOItems.plugin.getItem(type, id)
            }

            "ecoitem" in config -> itemStack = WrappedEcoItem(config["ecoitem"].toString()).build()
            "minecraft_type" in config -> {
                val itemType = config["minecraft_type"].toString()
                val material = Material.getMaterial(itemType)
                itemStack = if (material != null) ItemStack(material) else null
            }

            "minecraft_item" in config -> itemStack = config["minecraft_item"] as? ItemStack
        }

        if (itemStack == null) itemStack = NexoItems.itemFromId(sourceID)?.build()

        return itemStack?.let { ItemUpdater.updateItem(it) } ?: ItemStack(Material.AIR)
    }

    fun itemStack(itemStack: ItemStack?): Loot {
        this.itemStack = itemStack
        return this
    }

    fun dropNaturally(location: Location, amountMultiplier: Int) =
        if (Math.random() <= probability) dropItems(location, amountMultiplier) else 0

    private fun dropItems(location: Location, amountMultiplier: Int): Int {
        val world = location.world ?: return 0
        return getItem(amountMultiplier).also { world.dropItemNaturally(location, it) }.amount
    }

    fun getItem(amountMultiplier: Int): ItemStack {
        return ItemUpdater.updateItem(itemStack().clone()).also {
            it.amount *= amount.plus(1).random() * amountMultiplier
        }
    }
}
