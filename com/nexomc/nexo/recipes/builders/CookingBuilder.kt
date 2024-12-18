package com.nexomc.nexo.recipes.builders

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory

open class CookingBuilder(player: Player, builderName: String) :
    RecipeBuilder(player, builderName) {
    private var cookingTime = 0
    private var experience = 0

    override fun createInventory(player: Player?, inventoryTitle: String): Inventory {
        return Bukkit.createInventory(player, InventoryType.FURNACE, inventoryTitle)
    }

    override fun saveRecipe(name: String, permission: String?) {
        val content = getInventory().contents
        val newCraftSection = getConfig()!!.createSection(name)
        setItemStack(newCraftSection.createSection("result"), content[2]!!)
        setItemStack(newCraftSection.createSection("input"), content[0]!!)
        newCraftSection["cookingTime"] = cookingTime
        newCraftSection["experience"] = experience

        if (!permission.isNullOrEmpty()) newCraftSection["permission"] = permission

        saveConfig()
        close()
    }

    fun setCookingTime(cookingTime: Int) {
        this.cookingTime = cookingTime
    }

    fun setExperience(experience: Int) {
        this.experience = experience
    }
}
