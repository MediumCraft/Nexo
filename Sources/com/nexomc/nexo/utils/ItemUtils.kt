package com.nexomc.nexo.utils

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.components.FoodComponent

object ItemUtils {

    fun itemStacks(vararg materials: Material): List<ItemStack> {
        return materials.map(::ItemStack)
    }

    /**
     * Used to correctly damage the item in the player's hand based on broken block
     * Only handles it if the block is a NexoBlock or NexoFurniture
     *
     * @param player    the player that broke the NexoBlock or NexoFurniture
     * @param itemStack the item in the player's hand
     */
    @JvmStatic
    fun damageItem(player: Player, itemStack: ItemStack) {
        // If the item is not a tool, it will not be damaged, example flint&steel should not be damaged
        if (isTool(itemStack)) player.damageItemStack(itemStack, 1)
    }

    @JvmStatic
    fun isTool(itemStack: ItemStack): Boolean {
        return isTool(itemStack.type)
    }

    fun isTool(material: Material): Boolean {
        return material.toString().endsWith("_AXE")
                || material.toString().endsWith("_PICKAXE")
                || material.toString().endsWith("_SHOVEL")
                || material.toString().endsWith("_HOE")
                || material.toString().endsWith("_SWORD")
                || material == Material.TRIDENT
    }

    @JvmStatic
    fun isMusicDisc(itemStack: ItemStack): Boolean {
        if (VersionUtil.atleast("1.21")) return itemStack.hasItemMeta() && itemStack.itemMeta.hasJukeboxPlayable()
        return itemStack.type.name.startsWith("MUSIC_DISC")
    }

    @JvmStatic
    fun getUsingConvertsTo(itemMeta: ItemMeta): ItemStack? {
        if (VersionUtil.below("1.21")) return null
        if (VersionUtil.atleast("1.21.2")) return if (itemMeta.hasUseRemainder()) itemMeta.useRemainder else null

        return runCatching {
            FoodComponent::class.java.getMethod("getUsingConvertsTo").invoke(itemMeta.food) as? ItemStack
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    @JvmStatic
    fun setUsingConvertsTo(foodComponent: FoodComponent, replacement: ItemStack?) {
        if (!VersionUtil.matchesServer("1.21.1")) return
        runCatching {
            FoodComponent::class.java.getMethod("setUsingConvertsTo", ItemStack::class.java).invoke(foodComponent, replacement)
        }.onFailure { it.printStackTrace() }
    }
}
