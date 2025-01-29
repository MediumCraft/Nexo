package com.nexomc.nexo.recipes.builders

import com.mineinabyss.idofront.items.editItemMeta
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.BrewerInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType

class BrewingBuilder(player: Player) : RecipeBuilder(player, "brewing") {
    override fun createInventory(player: Player?, inventoryTitle: String): Inventory {
        return Bukkit.createInventory(player, InventoryType.BREWING, inventoryTitle)
    }

    override fun saveRecipe(name: String, permission: String?) {
        val inventory = inventory as? BrewerInventory ?: return
        val ingredient = inventory.ingredient ?: return
        val input = inventory.fuel ?: ItemStack(Material.POTION).editItemMeta<PotionMeta> { basePotionType = PotionType.WATER }
        val result = inventory.contents.firstOrNull() ?: return
        val newCraftSection = getConfig()!!.createSection(name)
        setItemStack(newCraftSection.createSection("result"), result)
        setItemStack(newCraftSection.createSection("ingredient"), ingredient)
        setItemStack(newCraftSection.createSection("input"), input)
    }

}