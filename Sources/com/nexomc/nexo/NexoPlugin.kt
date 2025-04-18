package com.nexomc.nexo

import com.jeff_media.customblockdata.CustomBlockData
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.commands.CommandsManager
import com.nexomc.nexo.compatibilities.CompatibilitiesManager
import com.nexomc.nexo.compatibilities.worldguard.NexoWorldguardFlags
import com.nexomc.nexo.configs.ConfigsManager
import com.nexomc.nexo.configs.Message
import com.nexomc.nexo.configs.ResourceManager
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.configs.SettingsUpdater
import com.nexomc.nexo.configs.SoundManager
import com.nexomc.nexo.converter.Converter
import com.nexomc.nexo.converter.ItemsAdderConverter
import com.nexomc.nexo.converter.OraxenConverter
import com.nexomc.nexo.fonts.FontManager
import com.nexomc.nexo.items.ItemUpdater
import com.nexomc.nexo.mechanics.MechanicsManager
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory
import com.nexomc.nexo.pack.PackGenerator
import com.nexomc.nexo.pack.server.EmptyServer
import com.nexomc.nexo.pack.server.NexoPackServer
import com.nexomc.nexo.recipes.RecipesManager
import com.nexomc.nexo.utils.NexoMetrics
import com.nexomc.nexo.utils.NoticeUtils
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.actions.ClickActionManager
import com.nexomc.nexo.utils.breaker.BreakerManager
import com.nexomc.nexo.utils.breaker.LegacyBreakerManager
import com.nexomc.nexo.utils.breaker.ModernBreakerManager
import com.nexomc.nexo.utils.customarmor.CustomArmorListener
import com.nexomc.nexo.utils.inventories.InventoryManager
import com.nexomc.nexo.utils.libs.CommandAPIManager
import com.nexomc.protectionlib.ProtectionLib
import com.tcoded.folialib.FoliaLib
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin

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
        if (!NexoLibsLoader.loadNexoLibs(this)) LibbyManager.loadLibs(this)
        dataFolder.resolve("lib").deleteRecursively()
        nexo = this
        foliaLib = FoliaLib(this)
        CommandAPIManager(this).load()

        NexoWorldguardFlags.registerFlags()
    }

    override fun onEnable() {
        CommandAPIManager(this).enable()
        ProtectionLib.init(this)
        reloadConfigs()
        clickActionManager = ClickActionManager(this)
        fontManager = FontManager(configsManager)
        soundManager = SoundManager(configsManager.sounds)
        breakerManager = when {
            VersionUtil.atleast("1.20.5") -> ModernBreakerManager(ConcurrentHashMap())
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

        CommandsManager.loadCommands()

        packServer = NexoPackServer.initializeServer()
        packServer.start()

        NexoMetrics.initializeMetrics()
        MechanicsManager.registerNativeMechanics(false)

        foliaLib.scheduler.runNextTick {
            NexoItems.loadItems()
            RecipesManager.load()
            packGenerator.generatePack()
        }

        CompatibilitiesManager.enableCompatibilies()
        if (VersionUtil.isCompiled) NoticeUtils.compileNotice()
        if (VersionUtil.isCI) NoticeUtils.ciNotice()
        if (VersionUtil.isLeaked) NoticeUtils.leakNotice()
        if (LibbyManager.failedLibs) NoticeUtils.failedLibs()
    }

    override fun onDisable() {
        packServer.stop()
        HandlerList.unregisterAll(this)
        FurnitureFactory.unregisterEvolution()

        CompatibilitiesManager.disableCompatibilities()
        CommandAPIManager(this).disable()
        Message.PLUGIN_UNLOADED.log()
    }

    fun reloadConfigs() {
        resourceManager = ResourceManager(this)
        configsManager = ConfigsManager(this)
        configsManager.validatesConfig()
        converter = Converter(resourceManager.converter.config)
        if (!converter.oraxenConverter.hasBeenConverted) OraxenConverter.convert()
        if (!converter.itemsadderConverter.hasBeenConverted) ItemsAdderConverter.convert()
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
        val jarFile: JarFile?
            get() = runCatching { JarFile(nexo.file) }.getOrNull()
    }
}
