package com.nexomc.nexo

import com.jeff_media.customblockdata.CustomBlockData
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.commands.CommandsManager
import com.nexomc.nexo.compatibilities.CompatibilitiesManager
import com.nexomc.nexo.compatibilities.worldguard.NexoWorldguardFlags
import com.nexomc.nexo.configs.*
import com.nexomc.nexo.converter.Converter
import com.nexomc.nexo.converter.ItemsAdderConverter
import com.nexomc.nexo.converter.OraxenConverter
import com.nexomc.nexo.fonts.FontManager
import com.nexomc.nexo.fonts.NexoTranslator
import com.nexomc.nexo.items.ItemUpdater
import com.nexomc.nexo.mechanics.MechanicsManager
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.pack.PackGenerator
import com.nexomc.nexo.pack.server.EmptyServer
import com.nexomc.nexo.pack.server.NexoPackServer
import com.nexomc.nexo.recipes.RecipesManager
import com.nexomc.nexo.utils.*
import com.nexomc.nexo.utils.actions.ClickActionManager
import com.nexomc.nexo.utils.breaker.BreakerManager
import com.nexomc.nexo.utils.breaker.LegacyBreakerManager
import com.nexomc.nexo.utils.breaker.ModernBreakerManager
import com.nexomc.nexo.utils.customarmor.CustomArmorListener
import com.nexomc.nexo.utils.inventories.InventoryManager
import com.nexomc.nexo.utils.libs.CommandAPIManager
import com.nexomc.protectionlib.ProtectionLib
import com.tcoded.folialib.FoliaLib
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

class NexoPlugin : JavaPlugin() {
    private lateinit var configsManager: ConfigsManager
    private lateinit var resourceManager: ResourceManager
    private lateinit var fontManager: FontManager
    private lateinit var soundManager: SoundManager
    private lateinit var invManager: InventoryManager
    private lateinit var packGenerator: PackGenerator
    private var packServer: NexoPackServer = EmptyServer()
    private lateinit var clickActionManager: ClickActionManager
    private lateinit var breakerManager: BreakerManager
    private lateinit var converter: Converter
    lateinit var foliaLib: FoliaLib
        private set

    override fun onLoad() {
//        if (!PaperPluginLoader.usedPaperPluginLoader) {
//            NexoLibsLoader.loadNexoLibs(this)
//            if (!NexoLibsLoader.usedNexoLoader) LibbyManager.loadLibs(this)
//        }
        nexo = this
        foliaLib = FoliaLib(this)
        runCatching { CommandAPIManager(this).load() }

        NexoWorldguardFlags.registerFlags()
        NexoDatapack.clearOldDatapacks()
    }

    override fun onEnable() {
        runCatching { CommandAPIManager(this).enable() }
        ProtectionLib.init(this)
        reloadConfigs()
        clickActionManager = ClickActionManager(this)
        fontManager = FontManager(configsManager)
        soundManager = SoundManager(configsManager.sounds)
        breakerManager = when {
            VersionUtil.atleast("1.20.5") -> ModernBreakerManager(ConcurrentHashMap(), ConcurrentHashMap())
            else -> LegacyBreakerManager(ConcurrentHashMap())
        }
        ProtectionLib.debug = Settings.DEBUG.toBool()

        if (Settings.KEEP_UP_TO_DATE.toBool()) SettingsUpdater().handleSettingsUpdate()
        Bukkit.getPluginManager().registerEvents(CustomArmorListener(), this)
        packGenerator = PackGenerator()

        fontManager.registerEvents()
        Bukkit.getPluginManager().registerEvents(ItemUpdater(), this)

        invManager = InventoryManager()
        CustomBlockData.registerListener(this)

        runCatching { CommandsManager.loadCommands() }

        NexoMetrics.initializeMetrics()
        MechanicsManager.registerNativeMechanics(false)
        NexoPackServer.registerDefaultPackServers()

        foliaLib.scheduler.runNextTick {
            NexoPackServer.initializeServer()
            NexoItems.loadItems()
            RecipesManager.load()
            packGenerator.generatePack()
            NexoTranslator.registerTranslations()
        }

        CompatibilitiesManager.enableCompatibilies()
        if (VersionUtil.isCompiled) NoticeUtils.compileNotice()
        if (VersionUtil.isCI) NoticeUtils.ciNotice()
        if (VersionUtil.isLeaked) NoticeUtils.leakNotice()
        if (LibbyManager.failedLibs) NoticeUtils.failedLibs()

        SchedulerUtils.runTaskLater(10L) {
            JarReader.postStartupCheck()
        }
    }

    override fun onDisable() {
        runCatching {
            packServer.stop()
            HandlerList.unregisterAll(this)
            FurnitureFactory.unregisterEvolution()

            CompatibilitiesManager.disableCompatibilities()
            CommandAPIManager(this).disable()
            Message.PLUGIN_UNLOADED.log()
        }
    }

    fun reloadConfigs() {
        resourceManager = ResourceManager(this)
        configsManager = ConfigsManager(this)
        Settings.reload()
        configsManager.validatesConfig()
        converter = Converter(resourceManager.converter.config)
        OraxenConverter.convert()
        ItemsAdderConverter.convert()
    }

    fun configsManager() = configsManager

    fun resourceManager() = resourceManager

    fun resourceManager(resourceManager: ResourceManager) {
        this.resourceManager = resourceManager
    }

    fun converter() = converter

    fun fontManager() = fontManager

    fun fontManager(fontManager: FontManager) {
        this.fontManager.unregisterEvents()
        this.fontManager = fontManager
        fontManager.registerEvents()

        NexoTranslator.registerTranslations()
    }

    fun soundManager() = soundManager

    fun soundManager(soundManager: SoundManager) {
        this.soundManager = soundManager
    }

    fun breakerManager() = breakerManager

    fun invManager() = invManager

    fun packGenerator(): PackGenerator = packGenerator

    fun packGenerator(packGenerator: PackGenerator) {
        PackGenerator.stopPackGeneration()
        this.packGenerator = packGenerator
    }

    fun packServer() = packServer

    fun packServer(server: NexoPackServer) {
        packServer.stop()
        packServer = server
        packServer.start()
    }

    fun clickActionManager() = clickActionManager

    companion object {
        private lateinit var nexo: NexoPlugin

        @JvmStatic
        fun instance() = nexo

        @JvmStatic
        val jarFile: JarFile? by lazy { runCatching { JarFile(nexo.file) }.getOrNull() }
    }
}
