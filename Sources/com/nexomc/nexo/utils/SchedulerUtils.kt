package com.nexomc.nexo.utils

import com.nexomc.nexo.NexoPlugin
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

object SchedulerUtils {

    fun runTaskLater(delay: Long, task: () -> Unit) {
        Bukkit.getScheduler().runTaskLater(NexoPlugin.instance(), task, delay)
    }

    fun runTaskTimer(delay: Long, period: Long, task: () -> Unit): BukkitTask {
        return Bukkit.getScheduler().runTaskTimer(NexoPlugin.instance(), task, delay, period)
    }

    fun syncDelayedTask(delay: Long = 0L, task: () -> Unit) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(NexoPlugin.instance(), task, delay)
    }
}