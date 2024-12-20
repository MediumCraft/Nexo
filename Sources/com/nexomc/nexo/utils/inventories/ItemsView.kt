package com.nexomc.nexo.utils.inventories

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.items.ItemBuilder
import com.nexomc.nexo.items.ItemUpdater
import com.nexomc.nexo.utils.AdventureUtils
import com.nexomc.nexo.utils.AdventureUtils.setDefaultStyle
import com.nexomc.nexo.utils.ItemUtils.isEmpty
import com.nexomc.nexo.utils.Utils.removeExtension
import dev.triumphteam.gui.guis.Gui
import dev.triumphteam.gui.guis.GuiItem
import dev.triumphteam.gui.guis.PaginatedGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.*
import kotlin.math.max

class ItemsView {
    private val settings = NexoPlugin.instance().resourceManager().settings()

    private lateinit var mainGui: PaginatedGui

    fun create(): PaginatedGui {
        val files = mutableMapOf<File, PaginatedGui>()
        NexoItems.itemMap().keys.forEach { file ->
            val unexcludedItems = NexoItems.unexcludedItems(file).takeIf { it.isNotEmpty() } ?: return@forEach
            files[file] = createSubGUI(file.name, unexcludedItems)
        }

        //Get max between the highest slot and the number of files
        val highestUsedSlot = files.keys
            .map { it.guiItemSlot().slot }
            .maxWithOrNull(Comparator.naturalOrder())
            ?.let { slot ->
                max(slot.toDouble(), (files.keys.size - 1).toDouble()).toInt()
            } ?: (files.keys.size - 1)
        val emptyGuiItem = GuiItem(Material.AIR)
        val guiItems = Collections.nCopies(highestUsedSlot + 1, emptyGuiItem).toMutableList()

        for ((file, gui) in files) {
            val slot = file.guiItemSlot().slot.takeUnless { it == -1 } ?: continue
            guiItems[slot] = GuiItem(file.guiItemSlot().itemStack!!) { e -> gui.open(e.whoClicked) }
        }

        for ((file, gui) in files) {
            val guiItemSlot = file.guiItemSlot().takeIf { it.slot == -1 } ?: continue
            guiItems[guiItems.indexOf(emptyGuiItem)] = GuiItem(guiItemSlot.itemStack!!) { e -> gui.open(e.whoClicked) }
        }

        mainGui = Gui.paginated().rows(Settings.NEXO_INV_ROWS.toInt()).pageSize(Settings.NEXO_INV_SIZE.toInt(45))
            .title(Settings.NEXO_INV_TITLE.toComponent()).disableItemSwap().disableItemPlace().create()
        mainGui.addItem(*guiItems.toTypedArray())

        (NexoItems.itemFromId(Settings.NEXO_INV_PREVIOUS_ICON.toString()) ?: ItemBuilder(Material.BARRIER))
            .displayName(Component.text("Previous Page")).build().let {
            mainGui.setItem(6, 3, GuiItem(it) { event ->
                mainGui.previous()
                event.isCancelled = true
            })

        }

        (NexoItems.itemFromId(Settings.NEXO_INV_NEXT_ICON.toString()) ?: ItemBuilder(Material.BARRIER))
            .displayName(Component.text("Next Page")).build().let {
                mainGui.setItem(6, 7, GuiItem(it) { event ->
                    mainGui.next()
                    event.isCancelled = true
                })
            }

        (NexoItems.itemFromId(Settings.NEXO_INV_EXIT.toString()) ?: ItemBuilder(Material.BARRIER))
            .displayName(Component.text("Exit")).build().let {
            mainGui.setItem(6, 5, GuiItem(it) { event -> event.whoClicked.closeInventory() })
        }

        return mainGui
    }

    private fun createSubGUI(fileName: String, items: List<ItemBuilder>): PaginatedGui {
        val gui = Gui.paginated().rows(6).pageSize(45).title(
            AdventureUtils.MINI_MESSAGE.deserialize(
                settings.getString(
                    "NexoInventory.menu_layout.${removeExtension(fileName)}.title",
                    Settings.NEXO_INV_TITLE.toString()
                )!!.replace("<main_menu_title>", Settings.NEXO_INV_TITLE.toString())
            )
        ).create()
        gui.disableAllInteractions()

        items.asSequence().map { it.build() }.filterNot { isEmpty(it) }.forEach {
            gui.addItem(GuiItem(it) { e: InventoryClickEvent ->
                    e.whoClicked.inventory.addItem(ItemUpdater.updateItem(e.currentItem!!.clone()))
                }
            )
        }

        val nextPage = (NexoItems.itemFromId(Settings.NEXO_INV_NEXT_ICON.toString()) ?: ItemBuilder(Material.BARRIER))
            .displayName(Component.text("Next Page")).build()
        val previousPage = (NexoItems.itemFromId(Settings.NEXO_INV_PREVIOUS_ICON.toString()) ?: ItemBuilder(Material.BARRIER))
            .displayName(Component.text("Previous Page")).build()
        val exitIcon = (NexoItems.itemFromId(Settings.NEXO_INV_EXIT.toString()) ?: ItemBuilder(Material.BARRIER))
            .displayName(Component.text("Exit")).build()

        if (gui.pagesNum > 1) {
            gui.setItem(6, 3, GuiItem(previousPage) { gui.previous() })
            gui.setItem(6, 7, GuiItem(nextPage) { gui.next() })
        }

        gui.setItem(6, 5, GuiItem(exitIcon) { event -> mainGui.open(event.whoClicked) })

        return gui
    }

    @JvmRecord
    private data class GuiItemSlot(val itemStack: ItemStack?, val slot: Int)

    private fun File.guiItemSlot(): GuiItemSlot {
        val fileName = removeExtension(name)
        val icon = settings.getString("NexoInventory.menu_layout.$fileName.icon")
        val displayName = AdventureUtils.MINI_MESSAGE.deserialize(
            settings.getString("NexoInventory.menu_layout.$fileName.displayname", this.name)!!
        ).setDefaultStyle(NamedTextColor.GREEN)

        val itemStack = (NexoItems.itemFromId(icon) ?: NexoItems.itemMap()[this]?.values?.firstOrNull() ?: ItemBuilder(Material.PAPER)).clone()
            .addItemFlags(ItemFlag.HIDE_ATTRIBUTES).itemName(displayName).displayName(displayName).lore(listOf())
            .build()

        val slot = settings.getInt("NexoInventory.menu_layout.${removeExtension(this.name)}.slot", 0) - 1
        return GuiItemSlot(itemStack, slot)
    }
}