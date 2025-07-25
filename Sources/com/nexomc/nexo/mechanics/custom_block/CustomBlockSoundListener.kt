package com.nexomc.nexo.mechanics.custom_block

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.events.custom_block.chorusblock.NexoChorusBlockBreakEvent
import com.nexomc.nexo.api.events.custom_block.chorusblock.NexoChorusBlockPlaceEvent
import com.nexomc.nexo.api.events.custom_block.noteblock.NexoNoteBlockBreakEvent
import com.nexomc.nexo.api.events.custom_block.noteblock.NexoNoteBlockPlaceEvent
import com.nexomc.nexo.api.events.custom_block.stringblock.NexoStringBlockBreakEvent
import com.nexomc.nexo.api.events.custom_block.stringblock.NexoStringBlockPlaceEvent
import com.nexomc.nexo.utils.BlockHelpers
import com.nexomc.nexo.utils.BlockHelpers.isLoaded
import com.nexomc.nexo.utils.SchedulerUtils
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.blocksounds.BlockSounds
import com.nexomc.nexo.utils.to
import com.nexomc.nexo.utils.wrappers.AttributeWrapper
import com.nexomc.protectionlib.ProtectionLib
import com.tcoded.folialib.wrapper.task.WrappedTask
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.world.GenericGameEvent
import org.bukkit.event.world.WorldUnloadEvent

class CustomBlockSoundListener(val customSounds: CustomBlockFactory.CustomBlockSounds) : Listener {
    companion object {
        val breakerPlaySound = Object2ObjectOpenHashMap<Location, WrappedTask>()
    }

    @EventHandler
    fun WorldUnloadEvent.onWorldUnload() {
        breakerPlaySound.entries.forEach { (loc, task) ->
            if (loc.isLoaded || task.isCancelled) return@forEach
            task.cancel()
            breakerPlaySound.remove(loc)
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun BlockPlaceEvent.onPlacingWood() {
        if (blockPlaced.blockData.soundGroup.placeSound != Sound.BLOCK_WOOD_PLACE) return
        if (NexoBlocks.isNexoNoteBlock(blockPlaced) || NexoBlocks.isNexoChorusBlock(blockPlaced)) return

        // Play sound for wood
        BlockHelpers.playCustomBlockSound(blockPlaced.location, BlockSounds.VANILLA_WOOD_PLACE, BlockSounds.VANILLA_PLACE_VOLUME, BlockSounds.VANILLA_PLACE_PITCH)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun BlockBreakEvent.onBreakingWood() {
        val location = block.location

        breakerPlaySound[location]?.cancel()
        if (block.blockData.soundGroup.breakSound != Sound.BLOCK_WOOD_BREAK) return
        if (NexoBlocks.isNexoNoteBlock(block) || NexoBlocks.isNexoChorusBlock(block)) return
        if (isCancelled || !ProtectionLib.canBreak(player, location)) return

        BlockHelpers.playCustomBlockSound(location, BlockSounds.VANILLA_WOOD_BREAK, BlockSounds.VANILLA_BREAK_VOLUME, BlockSounds.VANILLA_BREAK_PITCH)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun BlockDamageEvent.onHitWood() {
        if (VersionUtil.below("1.20.5") || block.blockData.soundGroup.hitSound != Sound.BLOCK_WOOD_HIT) return
        val location = block.location.takeUnless { it in breakerPlaySound } ?: return
        val blockSounds = NexoBlocks.customBlockMechanic(block)?.blockSounds

        val sound = blockSounds?.hitSound ?: BlockSounds.VANILLA_WOOD_HIT
        val volume = blockSounds?.hitVolume ?: BlockSounds.VANILLA_HIT_VOLUME
        val pitch = blockSounds?.hitPitch ?: BlockSounds.VANILLA_HIT_PITCH

        breakerPlaySound[location] = SchedulerUtils.foliaScheduler.runAtLocationTimer(
            location, Runnable {
                BlockHelpers.playCustomBlockSound(location, sound, volume, pitch)
        }, 2L, 4L)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun BlockDamageAbortEvent.onStopHittingWood() {
        breakerPlaySound.remove(block.location)?.cancel()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun GenericGameEvent.onStepFall() {
        val player = (entity as? Player)?.takeUnless { VersionUtil.isFoliaServer } ?: return
        val fallDistance = AttributeWrapper.SAFE_FALL_DISTANCE?.let(player::getAttribute)?.value ?: return

        if (!player.location.isLoaded || isAsynchronous) return
        if (event == GameEvent.HIT_GROUND && player.fallDistance < fallDistance) return
        if (event == GameEvent.STEP && (player.isInWater || player.isSwimming || player.isSneaking || player.isInLava)) return

        val blockStandingOn = BlockHelpers.entityStandingOn(player)?.takeUnless { it.type.isAir } ?: return
        if (blockStandingOn.blockData.soundGroup.stepSound != Sound.BLOCK_WOOD_STEP) return
        val mechanic = NexoBlocks.customBlockMechanic(blockStandingOn)

        val (sound, volume, pitch) = when {
            event === GameEvent.STEP ->
                (mechanic?.blockSounds?.let { it.stepSound to it.stepVolume to it.stepPitch })
                    ?: (BlockSounds.VANILLA_WOOD_STEP to BlockSounds.VANILLA_STEP_VOLUME to BlockSounds.VANILLA_STEP_PITCH)

            event == GameEvent.HIT_GROUND ->
                (mechanic?.blockSounds?.let { it.fallSound to it.fallVolume to it.fallPitch })
                    ?: (BlockSounds.VANILLA_WOOD_FALL to BlockSounds.VANILLA_FALL_VOLUME to BlockSounds.VANILLA_FALL_PITCH)

            else -> return
        }

        BlockHelpers.playCustomBlockSound(player.location, sound, SoundCategory.PLAYERS, volume, pitch)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun NexoNoteBlockPlaceEvent.onPlacing() {
        val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasPlaceSound) ?: return
        BlockHelpers.playCustomBlockSound(block.location, blockSounds.placeSound, blockSounds.placeVolume, blockSounds.placePitch)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun NexoNoteBlockBreakEvent.onBreaking() {
        val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasBreakSound) ?: return
        BlockHelpers.playCustomBlockSound(block.location, blockSounds.breakSound, blockSounds.breakVolume, blockSounds.breakPitch)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun NexoChorusBlockPlaceEvent.onPlacing() {
        val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasPlaceSound) ?: return
        BlockHelpers.playCustomBlockSound(block.location, blockSounds.placeSound, blockSounds.placeVolume, blockSounds.placePitch)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun NexoChorusBlockBreakEvent.onBreaking() {
        val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasBreakSound) ?: return
        BlockHelpers.playCustomBlockSound(block.location, blockSounds.breakSound, blockSounds.breakVolume, blockSounds.breakPitch)
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun NexoStringBlockPlaceEvent.onPlaceString() {
        val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasPlaceSound) ?: return

        BlockHelpers.playCustomBlockSound(block.location, blockSounds.placeSound, blockSounds.placeVolume, blockSounds.placePitch)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun NexoStringBlockBreakEvent.onBreakString() {
        val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasBreakSound) ?: return

        BlockHelpers.playCustomBlockSound(block.location, blockSounds.breakSound, blockSounds.breakVolume, blockSounds.breakPitch)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun BlockPistonExtendEvent.onPistonPush() {
        blocks.filter { it.type == Material.TRIPWIRE }.forEach { block: Block ->
            block.setType(Material.AIR, false)
            val mechanic = NexoBlocks.stringMechanic(block) ?: return@forEach
            val blockSounds = mechanic.blockSounds?.takeIf(BlockSounds::hasBreakSound) ?: return@forEach

            BlockHelpers.playCustomBlockSound(block.location, blockSounds.breakSound, blockSounds.breakVolume, blockSounds.breakPitch)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun GenericGameEvent.onStepFallString() {
        val player = (entity as? Player)?.takeIf { it.location.isLoaded && !isAsynchronous } ?: return

        if (player.lastDamageCause?.cause != EntityDamageEvent.DamageCause.FALL || event == GameEvent.HIT_GROUND) return
        val blockSounds = NexoBlocks.stringMechanic(player.location.block)?.blockSounds ?: return

        val (sound, volume, pitch) = when (event) {
            event if blockSounds.hasStepSound() -> blockSounds.stepSound to blockSounds.stepVolume to blockSounds.stepPitch
            event if blockSounds.hasFallSound() -> blockSounds.fallSound to blockSounds.fallVolume to blockSounds.fallPitch
            else -> return
        }
        BlockHelpers.playCustomBlockSound(player.location, sound, SoundCategory.PLAYERS, volume, pitch)
    }
}