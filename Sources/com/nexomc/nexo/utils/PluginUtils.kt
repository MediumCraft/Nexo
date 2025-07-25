package com.nexomc.nexo.utils

import org.bukkit.Bukkit

object PluginUtils {
    val isModelEngineEnabled: Boolean by lazy { isEnabled("ModelEngine") }
    val isVulcanEnabled by lazy { isEnabled("Vulcan") }
    val isSpartanEnabled by lazy { isEnabled("Spartan") }
    val isVacanEnabled by lazy { isEnabled("Vacan") }

    val isMythicHUDEnabled by lazy { isEnabled("MythicHUD") }
    val isBetterHUDEnabled by lazy { isEnabled("BetterHUD") }
    val isTABEnabled by lazy { isEnabled("TAB") || isEnabled("TAB-Bridge") }

    val isMMOItemsEnabled by lazy { isEnabled("MMOItems") }
    val isMythicMobsEnabled: Boolean by lazy { isEnabled("MythicMobs") }
    val isMythicCrucibleEnabled by lazy { isEnabled("MythicCrucible") }

    fun isEnabled(pluginName: String) = Bukkit.getPluginManager().isPluginEnabled(pluginName)
}
