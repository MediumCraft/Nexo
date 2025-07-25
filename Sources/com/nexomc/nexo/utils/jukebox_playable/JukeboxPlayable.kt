package com.nexomc.nexo.utils.jukebox_playable

import com.nexomc.nexo.utils.AdventureUtils
import com.nexomc.nexo.utils.JsonBuilder
import com.nexomc.nexo.utils.JsonBuilder.plus
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.configuration.ConfigurationSection

class JukeboxPlayable(
    val comparatorOutput: Int,
    val range: Int?,
    val lengthInSeconds: Float,
    val description: Component,
    val soundId: Key,
) {

    constructor(jukeboxSection: ConfigurationSection, soundId: Key) : this(
        jukeboxSection.getInt("comparator_output", 15).coerceIn(0, 15),
        jukeboxSection.getInt("range").takeIf { it > 0 },
        jukeboxSection.getDouble("length_in_seconds").toFloat().coerceAtLeast(1f),
        jukeboxSection.getRichMessage("description") ?: Component.text(soundId.value()),
        soundId
    )

    constructor(jukeboxMap: Map<String, Any>, soundId: Key) : this(
        jukeboxMap["comparator_output"]?.toString()?.toIntOrNull()?.coerceIn(0, 15) ?: 15,
        jukeboxMap["range"]?.toString()?.toIntOrNull()?.takeIf { it > 0 },
        jukeboxMap["length_in_seconds"]?.toString()?.toFloatOrNull()?.coerceAtLeast(1f) ?: 1f,
        AdventureUtils.MINI_MESSAGE.deserialize(jukeboxMap["description"].toString()),
        soundId
    )

    val jukeboxJson by lazy {
        JsonBuilder.jsonObject
            .plus("sound_event", JsonBuilder.jsonObject.plus("sound_id", soundId.asString()).plus("range", range))
            .plus("description", JsonBuilder.jsonObject.plus("text", AdventureUtils.LEGACY_SERIALIZER.serialize(description)))
            .plus("length_in_seconds", lengthInSeconds).plus("comparator_output", comparatorOutput.coerceIn(0..15))
    }
}