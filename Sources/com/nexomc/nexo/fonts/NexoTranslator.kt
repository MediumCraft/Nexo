package com.nexomc.nexo.fonts

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.pack.VanillaResourcePack
import com.nexomc.nexo.utils.deserialize
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.Translator
import java.util.*

class NexoTranslator : Translator {

    companion object {
        private val key = Key.key("nexo", "localization")
        private val DEFAULT_LANG_KEY = Settings.DEFAULT_LANGUAGE_KEY.toKey()

        private fun Locale.toKey(): Key = Key.key("${language}_$country".lowercase())

        fun registerTranslations() {
            GlobalTranslator.translator().sources().filter { it.name() == key }.forEach(GlobalTranslator.translator()::removeSource)
            GlobalTranslator.translator().addSource(NexoTranslator())
        }
    }

    override fun translate(key: String, locale: Locale) = null

    override fun translate(component: TranslatableComponent, locale: Locale): Component? {
        val resourcePack = NexoPlugin.instance().packGenerator().resourcePack()
        val lang = resourcePack.language(locale.toKey()) ?: resourcePack.language(DEFAULT_LANG_KEY) ?: return null
        val translation = lang.translation(component.key()) ?: return null
        val vanillaLang = VanillaResourcePack.resourcePack.language(locale.toKey()) ?: VanillaResourcePack.resourcePack.language(DEFAULT_LANG_KEY)
        if (vanillaLang?.translation(component.key()) == translation) return null

        return when {
            component.children().isNotEmpty() -> translation.deserialize().children(component.children())
            else -> translation.deserialize()
        }
    }

    override fun name() = key
}