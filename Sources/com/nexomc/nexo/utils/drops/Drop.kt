package com.nexomc.nexo.utils.drops

import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.mechanics.furniture.FurnitureHelpers
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic
import com.nexomc.nexo.mechanics.misc.itemtype.ItemTypeMechanicFactory
import com.nexomc.nexo.utils.BlockHelpers.isLoaded
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.deserialize
import com.nexomc.nexo.utils.getLinkedMapListOrNull
import com.nexomc.nexo.utils.wrappers.EnchantmentWrapper
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import kotlin.random.Random

data class Drop(
    val hierarchy: List<String>? = null,
    val loots: MutableList<Loot>,
    val isSilktouch: Boolean,
    val isFortune: Boolean,
    val sourceID: String,
    val minimalType: String? = null,
    val bestTool: String? = null,
) {

    val isEmpty get() = loots.isEmpty() && !isSilktouch
    val explosionDrops: Drop get() = Drop(loots.filter(Loot::inExplosion).toMutableList(), silktouch = false,
        fortune = false,
        sourceID = sourceID
    )

    constructor(loots: MutableList<Loot>, silktouch: Boolean, fortune: Boolean, sourceID: String) :
            this(loots = loots, isSilktouch = silktouch, isFortune = fortune, sourceID = sourceID)

    fun getItemType(itemInHand: ItemStack): String? {
        val itemID = NexoItems.idFromItem(itemInHand)
        val factory = ItemTypeMechanicFactory.get()
        when {
            factory.isNotImplementedIn(itemID) -> {
                val content = itemInHand.type.toString().split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                return if (content.size >= 2) content[0] else ""
            }
            else -> return factory.getMechanic(itemID)?.itemType
        }
    }

    fun canDrop(itemInHand: ItemStack?): Boolean {
        return minimalType.isNullOrEmpty() || isToolEnough(itemInHand) && isTypeEnough(itemInHand)
    }

    fun isTypeEnough(itemInHand: ItemStack?): Boolean {
        if (!minimalType.isNullOrEmpty()) {
            val itemType = if (itemInHand == null) "" else getItemType(itemInHand)
            return itemType!!.isNotEmpty() && itemType in hierarchy!!
                    && (hierarchy.indexOf(itemType) >= hierarchy.indexOf(minimalType))
        } else return true
    }

    fun isToolEnough(itemInHand: ItemStack?): Boolean {
        if (bestTool.isNullOrEmpty()) return true
        val itemID = NexoItems.idFromItem(itemInHand)
        val type = (itemInHand?.type ?: Material.AIR).name
        return when (bestTool) {
            itemID, type -> true
            else -> type.endsWith("_${bestTool.uppercase()}")
        }
    }

    fun getDiff(item: ItemStack) = if (minimalType == null) 0 else hierarchy!!.indexOf(getItemType(item)) - hierarchy.indexOf(minimalType)

    fun spawns(location: Location, itemInHand: ItemStack): List<DroppedLoot> {
        if (!canDrop(itemInHand) || !location.isLoaded) return listOf()
        val baseItem = NexoItems.itemFromId(sourceID)?.build()

        if (baseItem != null && isSilktouch && itemInHand.itemMeta?.hasEnchant(EnchantmentWrapper.SILK_TOUCH) == true) {
            location.world.dropItemNaturally(location.toCenterLocation(), baseItem)
            return listOf(DroppedLoot(Loot(sourceID, baseItem, 1.0, 1..1), 1))
        } else return dropLoot(loots, location.toCenterLocation(), fortuneMultiplier(itemInHand))
    }

    fun furnitureSpawns(baseEntity: ItemDisplay, itemInHand: ItemStack) {
        val baseItem = NexoItems.itemFromId(sourceID)?.build()
        val location = baseEntity.location.toCenterLocation().takeIf { it.isWorldLoaded } ?: return
        val furnitureItem = FurnitureHelpers.furnitureItem(baseEntity) ?: NexoItems.itemFromId(sourceID)?.build() ?: return
        furnitureItem.editMeta { itemMeta: ItemMeta ->
            baseItem?.itemMeta?.takeIf(ItemMeta::hasDisplayName)?.let { itemMeta.displayName(it.displayName()) }
            baseEntity.persistentDataContainer.get(FurnitureMechanic.DISPLAY_NAME_KEY, PersistentDataType.STRING)?.also { itemMeta.displayName(it.deserialize()) }
        }

        if (VersionUtil.atleast("1.21.2")) runCatching {
            furnitureItem.resetData(DataComponentTypes.BLOCK_DATA)
        }

        if (!canDrop(itemInHand)) return

        when {
            baseItem != null && isSilktouch && itemInHand.itemMeta?.hasEnchant(EnchantmentWrapper.SILK_TOUCH) == true ->
                location.world.dropItemNaturally(location, baseItem)
            else -> {
                val loots = loots.map { if (it.itemStack() == baseItem) Loot(sourceID, furnitureItem, it.probability, it.amount) else it }
                dropLoot(loots, location, fortuneMultiplier(itemInHand))
            }
        }
    }

    fun fortuneMultiplier(itemInHand: ItemStack) =
        (1..(itemInHand.itemMeta?.takeIf { isFortune }?.getEnchantLevel(EnchantmentWrapper.FORTUNE)?.plus(1) ?: 1)).randomOrNull() ?: 1

    fun dropLoot(loots: List<Loot>, location: Location, fortuneMultiplier: Int) = loots.mapNotNull { loot ->
        loot.dropNaturally(location, fortuneMultiplier).takeIf { it > 0 }?.let { amount -> DroppedLoot(loot, amount) }
    }

    /**
     * Get the loots that will drop based on a given Player
     * @param player the player that triggered this drop
     * @return the loots that will drop
     */
    fun lootToDrop(player: Player): List<Loot> {
        val itemInHand = player.inventory.itemInMainHand

        return loots.filter { loot -> canDrop(itemInHand) && Random.nextDouble() < loot.probability }
    }

    companion object {
        @JvmStatic
        fun createDrop(toolTypes: List<String>?, dropSection: ConfigurationSection, sourceID: String): Drop {
            val loots = dropSection.getLinkedMapListOrNull("loots")?.mapTo(mutableListOf()) { Loot(it, sourceID) }
                ?: mutableListOf(Loot(sourceID, 1.0))

            return Drop(
                toolTypes, loots, dropSection.getBoolean("silktouch"),
                dropSection.getBoolean("fortune"), sourceID,
                dropSection.getString("minimal_type", ""), dropSection.getString("best_tool", "")
            )
        }

        @JvmStatic
        fun emptyDrop() = Drop(ArrayList(), silktouch = false, fortune = false, sourceID = "")

        @JvmStatic
        fun emptyDrop(loots: MutableList<Loot>) = Drop(loots, silktouch = false, fortune = false, sourceID = "")
    }
}
