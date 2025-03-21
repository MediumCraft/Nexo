package com.nexomc.nexo.commands

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.NexoFurniture
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.configs.Message
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.configs.SoundManager
import com.nexomc.nexo.fonts.FontManager
import com.nexomc.nexo.items.ItemUpdater
import com.nexomc.nexo.mechanics.MechanicsManager
import com.nexomc.nexo.mechanics.custom_block.CustomBlockSoundListener
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.mechanics.furniture.listeners.FurnitureSoundListener
import com.nexomc.nexo.nms.NMSHandlers.resetHandler
import com.nexomc.nexo.pack.PackGenerator
import com.nexomc.nexo.pack.server.NexoPackServer.Companion.initializeServer
import com.nexomc.nexo.recipes.RecipesManager
import com.nexomc.nexo.utils.AdventureUtils.tagResolver
import com.nexomc.nexo.utils.SchedulerUtils
import com.nexomc.nexo.utils.logs.Logs
import dev.jorel.commandapi.CommandTree
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.multiLiteralArgument
import dev.jorel.commandapi.kotlindsl.textArgument
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.command.CommandSender
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import kotlin.jvm.optionals.getOrDefault

internal fun CommandTree.reloadCommand() = multiLiteralArgument(nodeName = "reload", "reload", "rl") {
    withPermission("nexo.command.reload")
    textArgument("type", optional = true) {
        replaceSuggestions(ArgumentSuggestions.strings("items", "pack", "recipes", "configs", "all"))
        anyExecutor { sender, args ->
            when ((args.getOptional("type").getOrDefault("all") as String).lowercase()) {
                "items" -> ReloadCommand.reloadItems(sender)
                "pack" -> ReloadCommand.reloadPack(sender)
                "recipes" -> ReloadCommand.reloadRecipes(sender)
                "configs" -> {
                    MechanicsManager.unregisterListeners()
                    MechanicsManager.unregisterTasks()
                    NexoPlugin.instance().reloadConfigs()
                    MechanicsManager.registerNativeMechanics()
                }
                else -> ReloadCommand.reloadAll(sender)
            }
            Bukkit.getOnlinePlayers().forEach { player: Player ->
                NexoPlugin.instance().fontManager().sendGlyphTabCompletion(player)
            }
        }
    }
}

object ReloadCommand {

    @JvmOverloads
    @JvmStatic
    fun reloadAll(sender: CommandSender? = Bukkit.getConsoleSender()) {
        FurnitureFactory.instance()?.packetManager()?.removeAllFurniturePackets()
        MechanicsManager.unregisterListeners()
        MechanicsManager.unregisterTasks()
        resetHandler()
        NexoPlugin.instance().reloadConfigs()
        NexoPlugin.instance().packServer(initializeServer())
        MechanicsManager.registerNativeMechanics()
        reloadItems(sender)
        reloadRecipes(sender)
        reloadPack(sender)
    }

    @JvmOverloads
    @JvmStatic
    fun reloadItems(sender: CommandSender? = Bukkit.getConsoleSender()) {
        Message.RELOAD.send(sender, tagResolver("reloaded", "items"))
        NexoItems.itemConfigCache.clear()
        CustomBlockSoundListener.breakerPlaySound.onEach { it.value.cancel() }.clear()
        FurnitureSoundListener.breakerPlaySound.onEach { it.value.cancel() }.clear()
        FurnitureFactory.instance()?.packetManager()?.removeAllFurniturePackets()
        NexoItems.loadItems()
        NexoPlugin.instance().invManager().regen()

        if (Settings.UPDATE_ITEMS.toBool() && Settings.UPDATE_ITEMS_ON_RELOAD.toBool()) {
            Logs.logInfo("Updating all items in player-inventories...")
            Bukkit.getServer().onlinePlayers.asSequence().associateWith(Player::getInventory).forEach { (player, inventory) ->
                SchedulerUtils.foliaScheduler.runAtEntity(player) {
                    for (i in 0..inventory.size) {
                        val oldItem = inventory.getItem(i) ?: continue
                        val newItem = ItemUpdater.updateItem(oldItem).takeUnless { it == oldItem } ?: continue
                        inventory.setItem(i, newItem)
                    }
                }
            }
            /*SchedulerUtils.runTaskAsync {
                Bukkit.getServer().onlinePlayers.forEach { player ->
                    val updates = ObjectArrayList<Pair<Int, ItemStack>>()

                    player.inventory.contents.forEachIndexed { index, item ->
                        if (item == null) return@forEachIndexed
                        val newItem = ItemUpdater.updateItem(item).takeUnless { it == item } ?: return@forEachIndexed
                        updates.add(index to newItem)
                    }

                    SchedulerUtils.runTask {
                        updates.forEach { (index, newItem) ->
                            player.inventory.setItem(index, newItem)
                        }
                    }
                }
            }*/
        }

        Logs.logInfo("Updating all placed furniture...")
        SchedulerUtils.runAtWorldEntities { entity ->
            (entity as? ItemDisplay)?.let(NexoFurniture::updateFurniture)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun reloadPack(sender: CommandSender? = Bukkit.getConsoleSender()) {
        Message.PACK_REGENERATED.send(sender)
        NexoPlugin.instance().fontManager(FontManager(NexoPlugin.instance().configsManager()))
        NexoPlugin.instance().soundManager(SoundManager(NexoPlugin.instance().configsManager().sounds))
        NexoPlugin.instance().packGenerator(PackGenerator())
        NexoPlugin.instance().packGenerator().generatePack()
    }

    @JvmOverloads
    @JvmStatic
    fun reloadRecipes(sender: CommandSender? = Bukkit.getConsoleSender()) {
        Message.RELOAD.send(sender, tagResolver("reloaded", "recipes"))
        if (Bukkit.recipeIterator().asSequence().filter { (it as? Keyed)?.key?.namespace == "nexo" }.count() < 100) RecipesManager.reload()
        else {
            Logs.logWarn("Nexo did not reload recipes due to the number of recipes!")
            Logs.logWarn("In modern Paper-versions this would cause the server to freeze for very long times")
            Logs.logWarn("Restart your server fully for recipe-changes to take effect")
        }
    }
}
