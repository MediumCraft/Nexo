package com.nexomc.nexo.utils

import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.utils.logs.Logs
import java.io.File
import net.kyori.adventure.key.Key
import org.apache.commons.lang3.EnumUtils
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

fun ConfigurationSection.childSections(): Map<String, ConfigurationSection> {
    return getValues(false).filterValues { it is ConfigurationSection }.mapValues { it.value as ConfigurationSection }
}

fun ConfigurationSection.toMap(): Map<String, Any> {
    return getKeys(false).associateWith { get(it)!! }
}

fun ConfigurationSection.getStringListOrNull(key: String): List<String>? {
    return getStringList(key).filterNot { it.isNullOrEmpty() }.takeIf { it.isNotEmpty() }
}

fun ConfigurationSection.getStringOrNull(key: String): String? {
    return (get(key) as? String)?.ifEmpty { null }
}

fun ConfigurationSection.getKey(key: String): Key? {
    return runCatching { getString(key)?.let(Key::key) }.getOrNull()
}

fun ConfigurationSection.getKey(key: String, default: String): Key? {
    return runCatching { getString(key, default)?.let(Key::key) }.getOrNull()
}

fun ConfigurationSection.getKey(key: String, default: Key): Key {
    return runCatching { getString(key)?.let(Key::key) }.getOrNull() ?: default
}

fun ConfigurationSection.getKeyList(key: String): List<Key> {
    return runCatching { getStringList(key).mapNotNull { runCatching { Key.key(it) }.getOrNull() } }.getOrDefault(listOf())
}

fun ConfigurationSection.getKeyListOrNull(key: String): List<Key>? {
    return runCatching { getStringList(key).mapNotNull { runCatching { Key.key(it) }.getOrNull() } }.getOrDefault(listOf()).ifEmpty { null }
}

fun ConfigurationSection.getNamespacedKey(key: String, default: String): NamespacedKey {
    return runCatching { getString(key)?.let(NamespacedKey::fromString) }.getOrNull() ?: NamespacedKey.fromString(default)!!
}

fun ConfigurationSection.getNamespacedKey(key: String, default: NamespacedKey): NamespacedKey {
    return runCatching { getString(key)?.let(NamespacedKey::fromString) }.getOrNull() ?: default
}

fun ConfigurationSection.getNamespacedKey(key: String): NamespacedKey? {
    return runCatching { getString(key)?.let(NamespacedKey::fromString) }.getOrNull()
}

fun <T : Enum<T>> ConfigurationSection.getEnum(key: String, enum: Class<T>): T? {
    return EnumUtils.getEnum(enum, key)
}

class NexoYaml : YamlConfiguration() {
    override fun load(file: File) {
        runCatching {
            super.load(file)
        }.onFailure {
            Logs.logError("Error loading YAML configuration file: " + file.name)
            if (Settings.DEBUG.toBool()) it.printStackTrace()
        }
    }

    companion object {
        fun isValidYaml(file: File) = runCatching {
            YamlConfiguration().load(file)
        }.onFailure {
            Logs.logError("Error loading YAML configuration file: " + file.path)
            Logs.logError("Ensure that your config is formatted correctly:")
            if (Settings.DEBUG.toBool()) it.printStackTrace()
            else it.message?.let(Logs::logWarn)
        }.getOrNull() != null

        fun loadConfiguration(file: File) = runCatching {
            YamlConfiguration().apply { load(file) }
        }.onFailure {
            Logs.logError("Error loading YAML configuration file: " + file.name)
            Logs.logError("Ensure that your config is formatted correctly:")
            if (Settings.DEBUG.toBool()) it.printStackTrace()
            else it.message?.let(Logs::logWarn)
        }.getOrNull() ?: YamlConfiguration()

        fun saveConfig(file: File, section: ConfigurationSection) {
            runCatching {
                val config = loadConfiguration(file)
                config[section.currentPath!!] = section
                config.save(file)
            }.onFailure {
                Logs.logError("Error saving YAML configuration file: " + file.name)
                if (Settings.DEBUG.toBool()) it.printStackTrace()
                else it.message?.let(Logs::logWarn)
            }
        }

        fun copyConfigurationSection(source: ConfigurationSection, target: ConfigurationSection) {
            source.getKeys(false).forEach { key ->
                val (sourceValue, targetValue) = source[key] to target[key]

                if (sourceValue is ConfigurationSection) {
                    val targetSection = targetValue as? ConfigurationSection ?: target.createSection(key)
                    copyConfigurationSection(sourceValue, targetSection)
                } else target[key] = sourceValue
            }
        }
    }
}
