@file:Suppress("FunctionName")

package com.nexomc.nexo.utils

import com.nexomc.nexo.configs.Message
import com.nexomc.nexo.glyphs.GlyphTag
import com.nexomc.nexo.glyphs.ShiftTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

typealias KyoriComponent = Component

fun Component.serializeStrict(): String = AdventureUtils.STRICT_MINI_MESSAGE.serialize(this)
fun Component.serialize(): String = AdventureUtils.MINI_MESSAGE.serialize(this)
fun String.deserialize(): Component  = AdventureUtils.MINI_MESSAGE.deserialize(when {
    AdventureUtils.containsLegacyCodes(this) -> AdventureUtils.parseLegacy(this)
    else -> this
})
fun Component.toSimpleString(): String {
    return this.toString().replace(Regex(", (obfuscated|bold|strikethrough|underlined|italic)=not_set"), "")
        .replace(Regex(", (color|shadowColor|clickEvent|hoverEvent|insertion|font)=null"), "")
        .replace(Regex("StyleImpl\\{(\\s*)}"), "{}")
        .replace(Regex(",\\s*}"), "}")
        .replace(Regex(", children=\\[]"), "")
}

object AdventureUtils {

    private val legacyRegex = "[&§]\\w".toRegex()
    fun containsLegacyCodes(value: String): Boolean {
        return value.contains(legacyRegex)
    }

    fun reload() {
        MM = MiniMessage.builder().tags(NexoTagResolver).build()
    }

    fun Component.setDefaultStyle(color: TextColor? = NamedTextColor.WHITE) =
        this.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE).colorIfAbsent(color)

    @JvmField
    val STANDARD_MINI_MESSAGE = MiniMessage.miniMessage()
    val STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build()

    private val NexoTagResolver get() = TagResolver.resolver(
        TagResolver.standard(),
        GlyphTag.RESOLVER, ShiftTag.RESOLVER, TagResolver.resolver(
            "prefix", Tag.selfClosingInserting(STANDARD_MINI_MESSAGE.deserialize(Message.PREFIX.toString()))
        )
    )

    @JvmField
    val LEGACY_SERIALIZER = LegacyComponentSerializer.builder().hexColors().useUnusualXRepeatedCharacterHexFormat().build()

    val MINI_MESSAGE get() = MM
    private var MM = MiniMessage.builder().tags(NexoTagResolver).build()


    @JvmStatic
    fun MINI_MESSAGE_PLAYER(player: Player?): MiniMessage {
        return MiniMessage.builder()
            .tags(TagResolver.resolver(TagResolver.standard(), GlyphTag.getResolverForPlayer(player))).build()
    }

    @JvmStatic
    fun parseMiniMessage(message: String, tagResolver: TagResolver?): String {
        return MINI_MESSAGE.serialize(when {
            tagResolver != null -> MINI_MESSAGE.deserialize(message, tagResolver)
            else -> MINI_MESSAGE.deserialize(message)
        }).replace("\\\\(?!u)(?!n)(?!\")".toRegex(), "")
    }

    /**
     * Parses the string by deserializing it to a legacy component, then serializing it to a string via MiniMessage
     * @param message The string to parse
     * @return The parsed string
     */
    @JvmStatic
    fun parseLegacy(message: String): String {
        return MINI_MESSAGE.serialize(LEGACY_SERIALIZER.deserialize(message))
            .replace("\\\\(?!u)(?!n)(?!\")".toRegex(), "")
    }

    /**
     * Parses a string through both legacy and minimessage serializers.
     * This is useful for parsing strings that may contain legacy formatting codes and modern adventure-tags.
     * @param message The component to parse
     * @return The parsed string
     */
    @JvmStatic
    fun parseLegacyThroughMiniMessage(message: String): String {
        return LEGACY_SERIALIZER.serialize(
            MINI_MESSAGE.deserialize(
                MINI_MESSAGE.serialize(
                    LEGACY_SERIALIZER.deserialize(message)
                ).replace("\\\\(?!u)(?!n)(?!\")".toRegex(), "")
            )
        )
    }

    @JvmStatic
    fun tagResolver(string: String, tag: String): TagResolver {
        return TagResolver.resolver(string, Tag.selfClosingInserting(MINI_MESSAGE.deserialize(tag)))
    }

    fun tagResolver(string: String, tag: Component): TagResolver {
        return TagResolver.resolver(string, Tag.selfClosingInserting(tag))
    }
}
