package com.nexomc.nexo.utils

import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

fun File.resolve(vararg path: String): File {
    return path.fold(this) { file, segment -> File(file, segment) }
}

fun Path.resolve(vararg path: String): Path {
    return path.fold(this) { file, segment -> file.resolve(segment) }
}

fun File.listYamlFiles(deep: Boolean = false): List<File> {
    return if (deep) walkBottomUp().filter { file -> file.isFile && file.extension == "yml" && file.length() != 0L }.toFastList()
    else listFiles { file -> file.isFile && file.extension == "yml" && file.length() == 0L }?.toList() ?: emptyList()
}

fun File.listJsonFiles(deep: Boolean = false): List<File> {
    return if (deep) walkBottomUp().filter { file -> file.isFile && file.extension == "yml" && file.length() != 0L }.toFastList()
    else listFiles { file -> file.isFile && file.extension == "json" && file.length() == 0L }?.toList() ?: emptyList()
}

fun File.listDirectories(): List<File> {
    return walkBottomUp().filter { it.isDirectory }.toFastList()
}

object FileUtils {

    fun isEmpty(directory: File): Boolean = directory.listFiles()?.isEmpty() ?: true

    @JvmStatic
    fun setHidden(path: Path) {
        runCatching {
            if (System.getProperty("os.name").startsWith("Windows")) Files.setAttribute(path, "dos:hidden", true)
            Files.isHidden(path)
        }.printOnFailure()
    }

    fun getSha1Hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
