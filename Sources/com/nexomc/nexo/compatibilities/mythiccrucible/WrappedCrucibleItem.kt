package com.nexomc.nexo.compatibilities.mythiccrucible

import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.utils.PluginUtils
import com.nexomc.nexo.utils.logs.Logs
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.configuration.ConfigurationSection

class WrappedCrucibleItem(private val id: String?, val cache: Boolean = true) {

    constructor(section: ConfigurationSection) : this(section.getString("id"), section.getBoolean("cache", true))

    val material = runCatching { MythicBukkit.inst().itemManager.getItemStack(id).type }.getOrNull()

    fun build() = runCatching {
        BukkitAdapter.adapt(MythicBukkit.inst().itemManager.getItem(id).orElseThrow().generateItemStack(1))
    }.onFailure {
        Logs.logError("Failed to load MythicCrucible item $id")
        if (!PluginUtils.isMythicCrucibleEnabled) Logs.logWarn("MythicCrucible is not installed")
        if (Settings.DEBUG.toBool()) Logs.logWarn(it.message!!)
    }.getOrNull()
}
