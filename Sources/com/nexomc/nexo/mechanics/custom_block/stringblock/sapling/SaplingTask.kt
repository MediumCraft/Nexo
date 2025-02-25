package com.nexomc.nexo.mechanics.custom_block.stringblock.sapling

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.compatibilities.worldedit.WrappedWorldEdit
import com.jeff_media.customblockdata.CustomBlockData
import com.nexomc.nexo.utils.BlockHelpers.persistentDataContainer
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable

class SaplingTask(private val delay: Int) : BukkitRunnable() {
    override fun run() {
        if (!WrappedWorldEdit.loaded) return
        for (world: World in Bukkit.getWorlds()) {
            for (chunk: Chunk? in world.loadedChunks) {
                for (block: Block in CustomBlockData.getBlocksWithCustomData(NexoPlugin.instance(), chunk)) {
                    val pdc = block.persistentDataContainer
                    when {
                        pdc.has(SaplingMechanic.SAPLING_KEY, PersistentDataType.INTEGER) && block.type == Material.TRIPWIRE -> {
                            val string = NexoBlocks.stringMechanic(block)
                            if (string == null || !string.isSapling()) return

                            val sapling = string.sapling()
                            if (sapling == null || !sapling.hasSchematic()) continue
                            if (!sapling.canGrowNaturally) continue
                            if (sapling.requiresWaterSource && !sapling.isUnderWater(block)) continue
                            if (sapling.requiresLight() && block.lightLevel < sapling.minLightLevel) continue

                            val selectedSchematic = sapling.selectSchematic()
                            if (selectedSchematic == null || (!sapling.replaceBlocks && WrappedWorldEdit.blocksInSchematic(block.location, selectedSchematic).isNotEmpty())) continue

                            val growthTimeRemains = pdc.getOrDefault(SaplingMechanic.SAPLING_KEY, PersistentDataType.INTEGER, 0) - delay
                            if (growthTimeRemains <= 0) {
                                block.setType(Material.AIR, false)
                                if (sapling.hasGrowSound())
                                    block.world.playSound(block.location, sapling.growSound!!, 1.0f, 0.8f)
                                WrappedWorldEdit.pasteSchematic(block.location, selectedSchematic, sapling.replaceBlocks, sapling.copyBiomes, sapling.copyEntities)
                            } else pdc.set(SaplingMechanic.SAPLING_KEY, PersistentDataType.INTEGER, growthTimeRemains)
                        }
                        pdc.has(SaplingMechanic.SAPLING_KEY, PersistentDataType.INTEGER) && block.type != Material.TRIPWIRE -> pdc.remove(SaplingMechanic.SAPLING_KEY)
                    }
                }
            }
        }
    }
}
