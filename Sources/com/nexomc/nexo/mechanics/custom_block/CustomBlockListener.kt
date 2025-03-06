package com.nexomc.nexo.mechanics.custom_block

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.api.events.custom_block.chorusblock.NexoChorusBlockInteractEvent
import com.nexomc.nexo.api.events.custom_block.noteblock.NexoNoteBlockInteractEvent
import com.nexomc.nexo.api.events.custom_block.stringblock.NexoStringBlockInteractEvent
import com.nexomc.nexo.mechanics.custom_block.chorusblock.ChorusBlockMechanic
import com.nexomc.nexo.mechanics.custom_block.noteblock.NoteBlockMechanic
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringBlockMechanic
import com.nexomc.nexo.mechanics.limitedplacing.LimitedPlacing.LimitedPlacingType
import com.nexomc.nexo.utils.BlockHelpers
import com.nexomc.nexo.utils.EventUtils.call
import com.nexomc.nexo.utils.to
import io.th0rgal.protectionlib.ProtectionLib
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.AbstractWindCharge
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class CustomBlockListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun PlayerInteractEvent.callInteract() {
        val block = clickedBlock?.takeIf { action == Action.RIGHT_CLICK_BLOCK } ?: return

        val customBlockEvent = when (val mechanic = NexoBlocks.customBlockMechanic(block.blockData)) {
            is NoteBlockMechanic ->
                NexoNoteBlockInteractEvent(mechanic, player, item, hand!!, block, blockFace, action)
            is StringBlockMechanic ->
                NexoStringBlockInteractEvent(mechanic, player, item, hand!!, block, blockFace, action)
            is ChorusBlockMechanic ->
                NexoChorusBlockInteractEvent(mechanic, player, item, hand!!, block, blockFace, action)
            else -> return
        }

        if (!customBlockEvent.call()) setUseInteractedBlock(Event.Result.DENY)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun NexoNoteBlockInteractEvent.onInteractedNexoBlock() {
        if (!ProtectionLib.canInteract(player, block.location)) isCancelled = true
        else if (!player.isSneaking && mechanic.hasClickActions()) {
            mechanic.runClickActions(player)
            isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun NexoStringBlockInteractEvent.onInteractedNexoBlock() {
        if (!ProtectionLib.canInteract(player, block.location)) isCancelled = true
        else if (!player.isSneaking && mechanic.hasClickActions()) {
            mechanic.runClickActions(player)
            isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun PlayerInteractEvent.onLimitedPlacing() {
        val (block, item) = (clickedBlock ?: return) to (item ?: return)

        if (action != Action.RIGHT_CLICK_BLOCK) return
        val mechanic = NexoBlocks.customBlockMechanic(NexoItems.idFromItem(item))
        if (mechanic == null || !mechanic.hasLimitedPlacing()) return

        if (!player.isSneaking && BlockHelpers.isInteractable(block)) return

        val limitedPlacing = mechanic.limitedPlacing
        val belowPlaced = block.getRelative(blockFace).getRelative(BlockFace.DOWN)

        when {
            limitedPlacing!!.isNotPlacableOn(block, blockFace) -> setCancelled(true)
            limitedPlacing.isRadiusLimited -> {
                val (rad, amount) = limitedPlacing.radiusLimitation!!.let { it.radius to it.amount }
                var count = 0
                for (x in -rad..rad) for (y in -rad..rad) for (z in -rad..rad) {
                    val relativeMechanic = NexoBlocks.stringMechanic(block.getRelative(x, y, z))
                    if (relativeMechanic == null || relativeMechanic.itemID != mechanic.itemID) continue
                    count++
                }
                if (count >= amount) setCancelled(true)
            }
            limitedPlacing.type == LimitedPlacingType.ALLOW ->
                if (!limitedPlacing.checkLimited(belowPlaced)) setCancelled(true)
            limitedPlacing.type == LimitedPlacingType.DENY ->
                if (limitedPlacing.checkLimited(belowPlaced)) setCancelled(true)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun PlayerInteractEvent.onPrePlacingCustomBlock() {
        val itemID = NexoItems.idFromItem(item)
        val (placedAgainst, item, hand) = (clickedBlock ?: return) to (item ?: return) to (hand ?: return)
        if (action != Action.RIGHT_CLICK_BLOCK) return

        var mechanic: CustomBlockMechanic? = NexoBlocks.customBlockMechanic(itemID) ?: return
        if (!player.isSneaking && BlockHelpers.isInteractable(placedAgainst)) return

        // Change mechanic according to subMechanic changes
        when (mechanic) {
            is NoteBlockMechanic -> mechanic = mechanic.directional?.directionMechanic(blockFace, player) ?: mechanic.directional?.parentMechanic ?: mechanic
            is StringBlockMechanic -> {
                mechanic = mechanic.randomPlace().randomOrNull()?.let(NexoBlocks::stringMechanic) ?: mechanic
                if (placedAgainst.getRelative(blockFace).isLiquid) return
            }
        }

        CustomBlockHelpers.makePlayerPlaceBlock(player, hand, item, placedAgainst, blockFace, mechanic, mechanic!!.blockData)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockBreakEvent.onBreakingCustomBlock() {
        if (!NexoBlocks.isCustomBlock(block)) return
        isDropItems = false
        if (!NexoBlocks.remove(block.location, player)) isCancelled = true

        val blockAbove = block.getRelative(BlockFace.UP)
        if (isCancelled || !NexoBlocks.isNexoStringBlock(blockAbove)) return
        NexoBlocks.remove(blockAbove.location, player)
    }

    @EventHandler
    fun EntityExplodeEvent.onEntityExplosion() {
        val customBlocks = blockList().mapNotNull { block ->
            NexoBlocks.customBlockMechanic(block.blockData)?.let { m -> block to m }
        }.toMap()

        customBlocks.forEach { (block, mechanic) ->
            if (!mechanic.isBlastResistant && entity !is AbstractWindCharge) block.type = Material.AIR
            mechanic.breakable.drop.explosionDrops.spawns(block.location, ItemStack(Material.AIR))
        }
        blockList().removeAll(customBlocks.keys)
    }

    @EventHandler
    fun BlockExplodeEvent.onBlockExplosion() {
        val customBlocks = blockList().mapNotNull { block ->
            NexoBlocks.customBlockMechanic(block.blockData)?.let { m -> block to m }
        }.toMap()

        customBlocks.forEach { (block, mechanic) ->
            if (!mechanic.isBlastResistant) block.type = Material.AIR
            mechanic.breakable.drop.explosionDrops.spawns(block.location, ItemStack(Material.AIR))
        }
        blockList().removeAll(customBlocks.keys)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockPistonRetractEvent.onPiston() {
        if (blocks.any { NexoBlocks.customBlockMechanic(it.location)?.immovable == true }) isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockPistonExtendEvent.onPiston() {
        if (blocks.any { NexoBlocks.customBlockMechanic(it.location)?.immovable == true }) isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun BlockPlaceEvent.onPlacingVanilla() {
        if (blockPlaced.type != Material.TRIPWIRE && blockPlaced.type != Material.NOTE_BLOCK) return
        if (NexoBlocks.isCustomBlock(itemInHand)) return

        // Placing string, meant for the first blockstate as invisible string
        blockPlaced.setBlockData(blockPlaced.type.createBlockData(), false)
    }

    private val matArray = arrayOf(Material.NOTE_BLOCK, Material.STRING, Material.TRIPWIRE, Material.CHORUS_PLANT)
    @EventHandler(priority = EventPriority.LOWEST)
    fun InventoryCreativeEvent.onMiddleClick() {
        if (click != ClickType.CREATIVE || cursor?.type !in matArray) return
        val player = inventory.holder as? Player ?: return

        val block = player.rayTraceBlocks(6.0)?.hitBlock ?: return
        val mechanic = NexoBlocks.customBlockMechanic(block.blockData)
            ?: NexoBlocks.stringMechanic(block.getRelative(BlockFace.DOWN))?.takeIf { it.isTall } ?: return

        val item = (mechanic as? NoteBlockMechanic)?.directional?.parentBlock?.let(NexoItems::itemFromId)?.build() ?: NexoItems.itemFromId(mechanic.itemID)!!.build()
        val itemId = NexoItems.idFromItem(item)

        for (i in 0..8) {
            if (player.inventory.getItem(i) == null) continue
            if (NexoItems.idFromItem(player.inventory.getItem(i)) == itemId) {
                player.inventory.heldItemSlot = i
                isCancelled = true
                return
            }
        }
        cursor = item
    }
}
