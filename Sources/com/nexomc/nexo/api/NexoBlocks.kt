package com.nexomc.nexo.api

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.events.custom_block.noteblock.NexoNoteBlockBreakEvent
import com.nexomc.nexo.api.events.custom_block.noteblock.NexoNoteBlockDropLootEvent
import com.nexomc.nexo.api.events.custom_block.stringblock.NexoStringBlockBreakEvent
import com.nexomc.nexo.api.events.custom_block.stringblock.NexoStringBlockDropLootEvent
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic
import com.nexomc.nexo.mechanics.custom_block.noteblock.NoteBlockMechanic
import com.nexomc.nexo.mechanics.custom_block.noteblock.NoteBlockMechanicFactory
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringBlockMechanic
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringBlockMechanicFactory
import com.nexomc.nexo.mechanics.custom_block.stringblock.StringMechanicHelpers
import com.nexomc.nexo.mechanics.custom_block.stringblock.sapling.SaplingMechanic
import com.nexomc.nexo.mechanics.storage.StorageMechanic
import com.nexomc.nexo.mechanics.storage.StorageType
import com.nexomc.nexo.utils.BlockHelpers
import com.nexomc.nexo.utils.BlockHelpers.getPersistentDataContainer
import com.nexomc.nexo.utils.ItemUtils.damageItem
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.drops.Drop
import com.jeff_media.morepersistentdatatypes.DataType
import com.nexomc.nexo.mechanics.custom_block.noteblock.NoteMechanicHelpers
import com.nexomc.nexo.utils.EventUtils.call
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.NoteBlock
import org.bukkit.block.data.type.Tripwire
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object NexoBlocks {
    /**
     * Get all NexoItem ID's that have either a NoteBlockMechanic or a StringBlockMechanic
     *
     * @return A set of all NexoItem ID's that have either a NoteBlockMechanic or a StringBlockMechanic
     */
    @JvmStatic
    fun blockIDs() = NexoItems.itemNames().filter(::isCustomBlock).toSet()

    /**
     * Get all NexoItem ID's that have a NoteBlockMechanic
     *
     * @return A set of all NexoItem ID's that have a NoteBlockMechanic
     */
    @JvmStatic
    fun noteBlockIDs() = NexoItems.itemNames().filter(::isNexoNoteBlock).toSet()

    /**
     * Get all NexoItem ID's that have a StringBlockMechanic
     *
     * @return A set of all NexoItem ID's that have a StringBlockMechanic
     */
    @JvmStatic
    fun stringBlockIDs() = NexoItems.itemNames().filter(::isNexoStringBlock).toSet()

    /**
     * Check if a block is an instance of an NexoBlock
     *
     * @param block The block to check
     * @return true if the block is an instance of an NexoBlock, otherwise false
     */
    @JvmStatic
    fun isCustomBlock(block: Block?) = when (block?.type) {
        Material.NOTE_BLOCK -> noteBlockMechanic(block) != null
        Material.TRIPWIRE -> stringMechanic(block) != null
        else -> false
    }

    @JvmStatic
    fun isCustomBlock(itemStack: ItemStack?) = isCustomBlock(NexoItems.idFromItem(itemStack))

    /**
     * Check if an itemID is an instance of an NexoBlock
     *
     * @param itemId The ID to check
     * @return true if the itemID is an instance of an NexoBlock, otherwise false
     */
    @JvmStatic
    fun isCustomBlock(itemId: String?) =
        NexoItems.hasMechanic(itemId, "noteblock") || NexoItems.hasMechanic(itemId, "stringblock")

    /**
     * Check if a block is an instance of a NoteBlock
     *
     * @param block The block to check
     * @return true if the block is an instance of an NoteBlock, otherwise false
     */
    @JvmStatic
    fun isNexoNoteBlock(block: Block) = block.type == Material.NOTE_BLOCK && noteBlockMechanic(block) != null

    /**
     * Check if an itemID has a NoteBlockMechanic
     *
     * @param itemID The itemID to check
     * @return true if the itemID has a NoteBlockMechanic, otherwise false
     */
    @JvmStatic
    fun isNexoNoteBlock(itemID: String?) = NoteBlockMechanicFactory.instance()?.isNotImplementedIn(itemID) == false

    @JvmStatic
    fun isNexoNoteBlock(item: ItemStack?) = isNexoNoteBlock(NexoItems.idFromItem(item))

    /**
     * Check if a block is an instance of a StringBlock
     *
     * @param block The block to check
     * @return true if the block is an instance of a StringBlock, otherwise false
     */
    @JvmStatic
    fun isNexoStringBlock(block: Block) = block.type == Material.TRIPWIRE && stringMechanic(block) != null

    /**
     * Check if an itemID has a StringBlockMechanic
     *
     * @param itemID The itemID to check
     * @return true if the itemID has a StringBlockMechanic, otherwise false
     */
    @JvmStatic
    fun isNexoStringBlock(itemID: String?) = StringBlockMechanicFactory.instance()?.isNotImplementedIn(itemID) == false

    @JvmStatic
    fun place(itemID: String?, location: Location) {
        when {
            isNexoNoteBlock(itemID) -> placeNoteBlock(location, itemID)
            isNexoStringBlock(itemID) -> placeStringBlock(location, itemID)
        }
    }

    /**
     * Get the BlockData assosiated with
     *
     * @param itemID The ItemID of the NexoBlock
     * @return The BlockData assosiated with the ItemID, can be null
     */
    @JvmStatic
    fun blockData(itemID: String?): BlockData? {
        return when {
            isNexoNoteBlock(itemID) -> NoteBlockMechanicFactory.instance()?.getMechanic(itemID)?.blockData
            isNexoStringBlock(itemID) -> StringBlockMechanicFactory.instance()?.getMechanic(itemID)?.blockData
            else -> null
        }
    }

    @JvmStatic
    private fun placeNoteBlock(location: Location, itemID: String?) {
        val block = location.block
        NoteBlockMechanicFactory.setBlockModel(block, itemID)
        val pdc = getPersistentDataContainer(block)
        val mechanic = noteBlockMechanic(block) ?: return

        if (mechanic.storage()?.storageType == StorageType.STORAGE) {
            pdc.set<ByteArray, Array<ItemStack>>(StorageMechanic.STORAGE_KEY, DataType.ITEM_STACK_ARRAY, emptyArray())
        }
        NoteMechanicHelpers.checkNoteBlockAbove(location)
    }

    @JvmStatic
    private fun placeStringBlock(location: Location, itemID: String?) {
        val block = location.block
        val blockAbove = block.getRelative(BlockFace.UP)
        StringBlockMechanicFactory.setBlockModel(block, itemID)
        val mechanic = stringMechanic(block) ?: return
        if (mechanic.isTall) {
            if (!BlockHelpers.REPLACEABLE_BLOCKS.contains(blockAbove.type)) return
            else blockAbove.type = Material.TRIPWIRE
        }

        if (mechanic.isSapling()) mechanic.sapling()?.takeIf { it.canGrowNaturally }?.let {
            getPersistentDataContainer(block).set(
                SaplingMechanic.SAPLING_KEY,
                PersistentDataType.INTEGER,
                it.naturalGrowthTime
            )
        }
    }

    /**
     * Breaks an NexoBlock at the given location
     *
     * @param location  The location of the NexoBlock
     * @param player    The player that broke the block, can be null
     * @param forceDrop Whether to force the block to drop, even if player is null or in creative mode
     * @return True if the block was broken, false if the block was not an NexoBlock or could not be broken
     */
    @JvmStatic
    fun remove(location: Location, player: Player? = null, forceDrop: Boolean): Boolean {
        val block = location.block

        val noteMechanic = noteBlockMechanic(block)
        val overrideDrop = if (!forceDrop) null else (noteMechanic?.breakable ?: stringMechanic(block)?.breakable)?.drop
        return remove(location, player, overrideDrop)
    }

    /**
     * Breaks an NexoBlock at the given location
     *
     * @param location The location of the NexoBlock
     * @param player   The player that broke the block, can be null
     * @return True if the block was broken, false if the block was not an NexoBlock or could not be broken
     */
    @JvmOverloads
    @JvmStatic
    fun remove(location: Location, player: Player? = null, overrideDrop: Drop? = null): Boolean {
        val block = location.block

        return when {
            isNexoNoteBlock(block) -> removeNoteBlock(block, player, overrideDrop)
            isNexoStringBlock(block) -> removeStringBlock(block, player, overrideDrop)
            else -> false
        }
    }

    private fun removeNoteBlock(block: Block, player: Player?, overrideDrop: Drop?): Boolean {
        val (itemInHand, loc) = (player?.inventory?.itemInMainHand ?: ItemStack(Material.AIR)) to block.location
        val mechanic = noteBlockMechanic(block)?.let { it.directional?.parentMechanic ?: it } ?: return false

        var drop: Drop? = overrideDrop ?: mechanic.breakable.drop
        if (player != null) {
            val noteBlockBreakEvent = NexoNoteBlockBreakEvent(mechanic, block, player)
            if (!noteBlockBreakEvent.call()) return false

            drop = when {
                player.gameMode == GameMode.CREATIVE -> null
                overrideDrop != null || player.gameMode != GameMode.CREATIVE -> noteBlockBreakEvent.drop
                else -> drop
            }

            if (VersionUtil.isPaperServer) block.world.sendGameEvent(player, GameEvent.BLOCK_DESTROY, loc.toVector())
            loc.getNearbyPlayers(64.0).forEach { if (it != player) it.playEffect(loc, Effect.STEP_SOUND, block.blockData) }
        }

        if (drop != null) {
            val loots = drop.spawns(loc, itemInHand)
            if (loots.isNotEmpty() && player != null) {
                NexoNoteBlockDropLootEvent(mechanic, block, player, loots).call()
                damageItem(player, itemInHand)
            }
        }

        mechanic.storage()?.takeIf { it.storageType == StorageType.STORAGE }?.dropStorageContent(block)
        block.type = Material.AIR
        NoteMechanicHelpers.checkNoteBlockAbove(loc)
        return true
    }


    private fun removeStringBlock(block: Block, player: Player?, overrideDrop: Drop?): Boolean {
        val mechanic = stringMechanic(block) ?: return false
        val itemInHand = player?.inventory?.itemInMainHand ?: ItemStack(Material.AIR)

        val hasDropOverride = overrideDrop != null
        var drop: Drop? = if (hasDropOverride) overrideDrop else mechanic.breakable.drop
        if (player != null) {
            val wireBlockBreakEvent = NexoStringBlockBreakEvent(mechanic, block, player)
            if (!wireBlockBreakEvent.call()) return false

            if (player.gameMode == GameMode.CREATIVE) drop = null
            else if (hasDropOverride || player.gameMode != GameMode.CREATIVE) drop = wireBlockBreakEvent.drop

            if (VersionUtil.isPaperServer) block.world.sendGameEvent(player, GameEvent.BLOCK_DESTROY, block.location.toVector())
        }

        if (drop != null) {
            val loots = drop.spawns(block.location, itemInHand)
            if (loots.isNotEmpty() && player != null) {
                NexoStringBlockDropLootEvent(mechanic, block, player, loots).call()
            }
        }

        val blockAbove = block.getRelative(BlockFace.UP)
        if (mechanic.isTall) blockAbove.type = Material.AIR
        block.type = Material.AIR
        Bukkit.getScheduler().runTaskLater(NexoPlugin.instance(), Runnable {
            StringMechanicHelpers.fixClientsideUpdate(block.location)
            if (blockAbove.type == Material.TRIPWIRE) removeStringBlock(blockAbove, player, overrideDrop)
        }, 1L)
        return true
    }

    /**
     * Get the NexoBlock at a location
     *
     * @param location The location to check
     * @return The Mechanic of the NexoBlock at the location, or null if there is no NexoBlock at the location.
     * Keep in mind that this method returns the base Mechanic, not the type. Therefore, you will need to cast this to the type you need
     */
    @JvmStatic
    fun customBlockMechanic(location: Location): CustomBlockMechanic? {
        return when {
            !isCustomBlock(location.block) -> null
            else -> when (location.block.type) {
                Material.NOTE_BLOCK -> noteBlockMechanic(location.block)
                Material.TRIPWIRE -> stringMechanic(location.block)
                else -> null
            }
        }
    }

    @JvmStatic
    fun customBlockMechanic(blockData: BlockData): CustomBlockMechanic? {
        return when (blockData.material) {
            Material.NOTE_BLOCK -> noteBlockMechanic(blockData)
            Material.TRIPWIRE -> stringMechanic(blockData)
            else -> null
        }
    }

    @JvmStatic
    fun customBlockMechanic(itemID: String?) =
        NoteBlockMechanicFactory.instance()?.getMechanic(itemID) ?: StringBlockMechanicFactory.instance()?.getMechanic(itemID)

    @JvmStatic
    fun noteBlockMechanic(data: BlockData?): NoteBlockMechanic? {
        if (!NoteBlockMechanicFactory.isEnabled || data !is NoteBlock) return null
        return NoteBlockMechanicFactory.getMechanic(data)
    }

    @JvmStatic
    fun noteBlockMechanic(block: Block): NoteBlockMechanic? {
        if (!NoteBlockMechanicFactory.isEnabled) return null
        val noteBlock = block.blockData as? NoteBlock ?: return null
        return NoteBlockMechanicFactory.getMechanic(noteBlock)
    }

    @JvmStatic
    fun noteBlockMechanic(itemID: String?) = NoteBlockMechanicFactory.instance()?.getMechanic(itemID)

    @JvmStatic
    fun stringMechanic(blockData: BlockData?): StringBlockMechanic? {
        if (!StringBlockMechanicFactory.isEnabled || blockData !is Tripwire) return null
        return StringBlockMechanicFactory.getMechanic(blockData)
    }

    @JvmStatic
    fun stringMechanic(block: Block): StringBlockMechanic? {
        if (!StringBlockMechanicFactory.isEnabled) return null
        val tripwire = block.blockData as? Tripwire ?: return null
        return StringBlockMechanicFactory.getMechanic(tripwire)
    }

    @JvmStatic
    fun stringMechanic(itemID: String?) =
        StringBlockMechanicFactory.instance()?.getMechanic(itemID)
}