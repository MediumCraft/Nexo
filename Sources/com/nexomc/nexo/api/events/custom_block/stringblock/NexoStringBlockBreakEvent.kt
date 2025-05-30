package com.nexomc.nexo.api.events.custom_block.stringblock

import com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringBlockMechanic
import com.nexomc.nexo.utils.drops.Drop
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

class NexoStringBlockBreakEvent(
    override val mechanic: StringBlockMechanic,
    block: Block,
    player: Player
) : NexoBlockBreakEvent(mechanic, block, player), Cancellable {

    constructor(mechanic: StringBlockMechanic, block: Block, player: Player, drop: Drop?) : this(mechanic, block, player) {
        this.drop = drop ?: Drop.emptyDrop()
    }

    init {
        drop = mechanic.breakable.drop
    }

    override fun getHandlers() = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
