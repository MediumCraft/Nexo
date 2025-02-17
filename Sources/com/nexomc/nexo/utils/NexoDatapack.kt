package com.nexomc.nexo.utils

import com.google.gson.JsonObject
import com.nexomc.nexo.nms.NMSHandlers
import com.nexomc.nexo.utils.logs.Logs
import io.papermc.paper.datapack.Datapack
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit

open class NexoDatapack(key: String, description: String) {

    val defaultWorld = Bukkit.getWorlds().first()
    val isFirstInstall: Boolean get() = Bukkit.getDatapackManager().packs.mapNotNull(Datapack::getName).none(datapackKey::equals)
    val datapackEnabled: Boolean get() = Bukkit.getDatapackManager().getPack(datapackKey.value())?.isEnabled ?: false
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
        SchedulerUtils.callSyncMethod {
            if (!VersionUtil.isPaperServer) return@callSyncMethod
            if (VersionUtil.below("1.21.1")) return@callSyncMethod Logs.logWarn("Could not enable ${datapackKey.value()} datapack, use /datapack-command")
            Bukkit.getDatapackManager().refreshPacks()
            Bukkit.getDatapackManager().getPack(datapackKey.value())?.takeUnless { it.isEnabled == enabled }?.isEnabled = enabled
        }
    }
}
