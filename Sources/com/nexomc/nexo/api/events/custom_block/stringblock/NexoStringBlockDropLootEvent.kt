package com.nexomc.nexo.api.events.custom_block.stringblock

import com.nexomc.nexo.api.events.custom_block.NexoCustomBlockDropLootEvent
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringBlockMechanic
import com.nexomc.nexo.utils.drops.DroppedLoot
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class NexoStringBlockDropLootEvent(
    override val mechanic: StringBlockMechanic,
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
