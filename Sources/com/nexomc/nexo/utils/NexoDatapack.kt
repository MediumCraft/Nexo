package com.nexomc.nexo.utils

import com.google.gson.JsonObject
import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.nms.NMSHandlers
import com.nexomc.nexo.utils.logs.Logs
import io.papermc.paper.datapack.DatapackManager
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.packs.DataPack

@Suppress("DEPRECATION")
open class NexoDatapack(key: String, description: String) {

    val defaultWorld = Bukkit.getWorlds().first()
    val isFirstInstall: Boolean get() = Bukkit.getDataPackManager().dataPacks.mapNotNull(DataPack::getKey).none(datapackKey::equals)
    val datapackEnabled: Boolean get() = Bukkit.getDataPackManager().getEnabledDataPacks(defaultWorld).mapNotNull(DataPack::getKey)
        .filter(datapackKey::equals).plus(Bukkit.getDataPackManager().getDisabledDataPacks(defaultWorld)
            .mapNotNull(DataPack::key).filter(datapackKey::equals)).isNotEmpty()
    private val datapackMeta = JsonObject().apply {
        add("pack", JsonObject().apply {
            addProperty("pack_format", NMSHandlers.handler().datapackFormat())
            addProperty("description", description)
        })
    }
    val datapackKey = Key.key("minecraft:file/$key")
    val datapackFile = defaultWorld.worldFolder.resolve("datapacks/$key")

    fun writeMCMeta() {
        datapackFile.resolve("pack.mcmeta").apply {
            parentFile.mkdirs()
        }.writeText(datapackMeta.toString())
    }

    internal fun enableDatapack(enabled: Boolean) {
        if (!VersionUtil.isPaperServer) return
        if (!VersionUtil.atleast("1.21.1")) return Logs.logWarn("Could not enable ${datapackKey.value()} datapack, use /datapack-command")
        Bukkit.getDatapackManager().getPack(datapackKey.value())?.takeUnless { it.isEnabled == enabled }?.isEnabled = enabled
    }
}
