package com.nexomc.nexo.api.events.custom_block.noteblock

import com.nexomc.nexo.api.events.custom_block.NexoCustomBlockDropLootEvent
import com.nexomc.nexo.mechanics.custom_block.noteblock.NoteBlockMechanic
import com.nexomc.nexo.utils.drops.DroppedLoot
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class NexoNoteBlockDropLootEvent(
    override val mechanic: NoteBlockMechanic,
    block: Block,
    player: Player,
    loots: List<DroppedLoot>
) : NexoCustomBlockDropLootEvent(mechanic, block, player, loots) {

    override fun getHandlers() = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
