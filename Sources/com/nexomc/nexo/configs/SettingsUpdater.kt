package com.nexomc.nexo.configs

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.utils.logs.Logs
import com.nexomc.nexo.utils.printOnFailure
import com.nexomc.nexo.utils.remove
import org.bukkit.configuration.file.YamlConfiguration

class SettingsUpdater {
    fun handleSettingsUpdate() {
        var settings = NexoPlugin.instance().configsManager().settings()
        val oldSettings = settings.saveToString()

        settings = updateKeys(settings, UpdatedSettings.toStringMap())
        settings = removeKeys(settings, RemovedSettings.toMap())

        if (settings.saveToString() == oldSettings) return

        runCatching {
            settings.save(NexoPlugin.instance().dataFolder.absoluteFile.resolve("settings.yml"))
            Logs.logSuccess("Successfully updated settings.yml", true)
        }.printOnFailure()
    }

    private fun updateKeys(settings: YamlConfiguration, newKeyPaths: Map<String, String>): YamlConfiguration {
        newKeyPaths.entries.forEach { (key, value) ->
            if (!settings.contains(key)) return@forEach
            Logs.logWarn("Found outdated setting-path $key. This will be updated.")
            settings.set(value, settings.get(key))
            settings.set(key, null)
        }
        return settings
    }

    private fun removeKeys(settings: YamlConfiguration, keys: Map<String, Array<String>>): YamlConfiguration {
        keys.forEach { key: String, subKeys: Array<String> ->
            if (key in settings) {
                Logs.logWarn("Found outdated setting $key. This will be removed.")
                subKeys.forEach(settings::remove)
            }
            settings.remove(key)
            val parent = settings.getConfigurationSection(key.substringBeforeLast("."))
            if (parent != null && parent.getKeys(false).isEmpty()) settings.set(parent.currentPath!!, null)
        }
        return settings
    }
}
