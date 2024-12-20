package com.nexomc.nexo.pack.server

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.utils.appendIfMissing
import com.nexomc.nexo.utils.logs.Logs
import com.nexomc.nexo.utils.prependIfMissing
import com.google.gson.JsonParser
import com.nexomc.nexo.utils.AdventureUtils
import com.nexomc.nexo.utils.VersionUtil
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.mime.ByteArrayBody
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.bukkit.entity.Player
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture

class PolymathServer : NexoPackServer {
    private val serverAddress: String =
        Settings.POLYMATH_SERVER.toString("atlas.mineinabyss.com").prependIfMissing("https://").appendIfMissing("/")
    private var packUrl: String? = null
    private var hash: String? = null
    private var packUUID: UUID? = null
    private var uploadFuture: CompletableFuture<Void>? = null

    override fun packUrl() = packUrl ?: ""

    override val isPackUploaded: Boolean
        get() = NexoPlugin.instance().packGenerator().packGenFuture?.isDone != false && uploadFuture?.isDone != false

    override fun uploadPack(): CompletableFuture<Void> {
        if (hash != NexoPlugin.instance().packGenerator().builtPack()!!.hash()) {
            uploadFuture?.cancel(true)
            uploadFuture = null
        }

        if (uploadFuture == null) uploadFuture = CompletableFuture.runAsync {
            runCatching {
                HttpClients.createDefault().use { httpClient ->
                    val request = HttpPost(serverAddress + "upload")
                    val httpEntity =
                        MultipartEntityBuilder.create().addTextBody("id", Settings.POLYMATH_SECRET.toString()).addPart(
                            "pack", ByteArrayBody(NexoPlugin.instance().packGenerator().builtPack()!!.data().toByteArray(), "pack")
                        ).build()

                    request.entity = httpEntity

                    val response = httpClient.execute(request)
                    val responseString = EntityUtils.toString(response.entity)
                    val jsonOutput = runCatching {
                        JsonParser.parseString(responseString).asJsonObject
                    }.onFailure {
                        Logs.logError("The resource pack could not be uploaded due to a malformed response.")
                        Logs.logWarn("This is usually due to the resourcepack server being down.")
                        if (Settings.DEBUG.toBool()) {
                            Logs.logWarn(responseString)
                            it.printStackTrace()
                        }
                        else Logs.logWarn(it.message!!)
                    }.getOrNull() ?: return@runAsync

                    if (jsonOutput.has("url") && jsonOutput.has("sha1")) {
                        packUrl = jsonOutput["url"].asString
                        hash = jsonOutput["sha1"].asString
                        packUUID = UUID.nameUUIDFromBytes(hash!!.toByteArray())

                        Logs.logSuccess("ResourcePack has been uploaded to $packUrl")
                        return@runAsync
                    }

                    if (jsonOutput.has("error")) Logs.logError("Error: " + jsonOutput["error"].asString)
                    Logs.logError("Response: $jsonOutput")
                    Logs.logError("The resource pack has not been uploaded to the server. Usually this is due to an excessive size.")
                }
            }.onFailure {
                Logs.logError("The resource pack has not been uploaded to the server. Usually this is due to an excessive size.")
                if (Settings.DEBUG.toBool()) it.printStackTrace()
                else Logs.logWarn(it.message!!)
            }
        }

        return uploadFuture!!
    }

    override fun sendPack(player: Player) {
        if (NexoPlugin.instance().packGenerator().packGenFuture?.isDone == false) return
        if (uploadFuture == null || uploadFuture?.isDone == false) return

        val request = ResourcePackRequest.resourcePackRequest()
            .required(NexoPackServer.mandatory).replace(true)
            .prompt(NexoPackServer.prompt).packs(ResourcePackInfo.resourcePackInfo(packUUID!!, URI.create(packUrl!!), hash!!))
            .build()
        if (VersionUtil.isPaperServer) player.sendResourcePacks(request)
        else player.setResourcePack(packUUID!!, packUrl!!, NexoPackServer.hashArray(hash!!), AdventureUtils.LEGACY_SERIALIZER.serialize(NexoPackServer.prompt), NexoPackServer.mandatory)
    }
}