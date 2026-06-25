package org.nxblack.echoWave

import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.nxblack.echoWave.data.*
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class EchoWave : JavaPlugin(), Listener {
    companion object {
        const val SERVER_URL = "https://proxybedrock.onrender.com"
        const val PARTY_CHARS ="ABCDEFGHJKLMNOPQRSTUVWXYZ03456789"
    }


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val playerSettings = ConcurrentHashMap<UUID, PlayerSettings>()
    val groups = ConcurrentHashMap<String, Group>()
    val pendingInvites = ConcurrentHashMap<UUID, MutableList<GroupInvite>>()
    private val uploading = AtomicBoolean(false)
    private val roomMutex = Mutex()
    private val voiceBannedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pendingInvites.remove(event.player.uniqueId)
        playerSettings.remove(event.player.uniqueId)
    }



    override fun onEnable() {

        saveDefaultConfig()

        config.getStringList("voice-bans")
            .map(UUID::fromString)
            .forEach(voiceBannedPlayers::add)

        scope.launch {
            roomMutex.withLock {
                createRoom()
            }
        }

        getCommand("echo")!!
            .setExecutor(EchoCommand(this))

        server.pluginManager.registerEvents(this,this)

        server.scheduler.runTaskTimer(

            this, Runnable {

                if (VoiceRoomState.roomCode.isBlank() || VoiceRoomState.authToken.isBlank()) return@Runnable


                val payload = Bukkit.getOnlinePlayers()
                    .filterNot { isVoiceBanned(it) }
                    .map { player ->
                    val settings = getPlayerSettings(player)

                    PositionPayload(
                        gamertag = player.name,
                        x = player.location.blockX,
                        y = player.location.blockY,
                        z = player.location.blockZ,
                        dimension = player.world.name,
                        groupId = settings.groupId,
                        isMuted = settings.muted
                    )

                }

                if (!uploading.compareAndSet(false, true)) return@Runnable

                scope.launch {
                    try {
                        sendPositions(payload)
                    } finally {
                        uploading.set(false)
                    }
                }

            }, 20L, 20L
        )

    }

    override fun onDisable() {
        scope.cancel()
    }




    private suspend fun createRoom() {
        try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$SERVER_URL/api/create-room"))
            .headers("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build()

        val response = Http.client.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        ).await()
            if (response.statusCode() != 200) {
                logger.warning("HTTP ${response.statusCode()}")
                return
            }
            if (response.body().isBlank()) {
                logger.warning("Empty response")
                return
            }
        val json = Json.decodeFromString<CreateRoomResponse>(response.body())
        VoiceRoomState.roomCode = json.code
        VoiceRoomState.authToken = json.token}
        catch (e: CancellationException) {
            throw e
        }
        catch (e: Exception) {
            logger.warning("Failed to create room: ${e.message}")
        }

    }

    private suspend fun sendPositions(players: List<PositionPayload>) {

        try {
            val payload = PositionRequest(
                code = VoiceRoomState.roomCode,
                token = VoiceRoomState.authToken,
                players = players
            )
            val body = Json.encodeToString(payload)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$SERVER_URL/api/update-positions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build()

            val response = Http.client.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
            ).await()
            if (response.statusCode() != 200) {
                logger.warning("HTTP ${response.statusCode()}")
                return
            }
            if (response.body().isBlank()) {
                logger.warning("Empty response")
                return
            }

            val result = Json.decodeFromString<UpdateResponse>(response.body())
            if (result.error == "ROOM_NOT_FOUND") {
                VoiceRoomState.roomCode = ""
                VoiceRoomState.authToken = ""
                roomMutex.withLock {
                    if (VoiceRoomState.roomCode.isBlank()) {
                        createRoom()
                    }
                }
            }
        }
        catch (e: CancellationException) {
            throw e
        }
        catch (e: Exception) {
            logger.warning("Failed to update positions: ${e.message}")
        }

    }


    fun isVoiceBanned(player: Player): Boolean {
        return player.uniqueId in voiceBannedPlayers
    }

    fun setVoiceBan(player: Player, banned: Boolean) {
        if (banned) {
            voiceBannedPlayers.add(player.uniqueId)
        } else {
            voiceBannedPlayers.remove(player.uniqueId)
        }
    }

    fun generatePartyCode(): String {

        while (true) {
            val code = buildString {
                repeat(4) {append( PARTY_CHARS.random())}
            }
            val exists = groups.values.any {it.joinCode == code}
            if (!exists)  return code
        }
    }

    fun getPlayerSettings(player: Player): PlayerSettings {
        return playerSettings.computeIfAbsent(player.uniqueId) {
            PlayerSettings()
        }
    }

    fun joinGroup(player: Player,code: String){
        val invites = pendingInvites[player.uniqueId]?.toList()

        if(invites == null){
            player.sendMessage("§cYou don't have a pending invite.")
            return
        }

        val invite = invites.firstOrNull { it.code == code }

        if(invite == null){
            player.sendMessage("§cInvalid join code.")
            return
        }

        val group = groups[invite.partyId]

        if(group == null){
            player.sendMessage("§cParty no longer exists.")
            pendingInvites.remove(player.uniqueId)
            return
        }

        val settings = getPlayerSettings(player)

        if (!settings.groupId.isNullOrBlank()) {
            player.sendMessage("§cleave your current party first.")
            return
        }
        group.members.add(player.uniqueId)

        settings.groupId = invite.partyId

        pendingInvites[player.uniqueId]?.remove(invite)

        if (pendingInvites[player.uniqueId].isNullOrEmpty()) {
            pendingInvites.remove(player.uniqueId)
        }

        player.sendMessage("§aJoined the party.")

        group.members.forEach {
            Bukkit.getPlayer(it)
                ?.sendMessage("§b${player.name} joined the party.")

        }


    }

    fun leaveGroup(player: Player) {
        val settings = getPlayerSettings(player)

        val groupId = settings.groupId ?: run {
            player.sendMessage("§cYou are not in a party.")
            return
        }

        val group = groups[groupId] ?: return

        if (group.owner == player.uniqueId) {

            group.members.remove(player.uniqueId)
            settings.groupId = null

            if (group.members.isEmpty()) {
                groups.remove(groupId)
                player.sendMessage("§cParty disbanded.")
                return
            }

            val newOwner = group.members.first()
            group.owner = newOwner

            player.sendMessage("§eYou left the party.")

            group.members.forEach {
                val member = Bukkit.getPlayer(it)

                if (member != null) {
                    member.sendMessage("§e${player.name} left the party.")
                    member.sendMessage(
                        "§a${Bukkit.getOfflinePlayer(newOwner).name} is now the party owner."
                    )
                }
            }

            return
        }

        group.members.remove(player.uniqueId)
        settings.groupId = null

        player.sendMessage("§eYou left the party.")

        group.members.forEach {
            Bukkit.getPlayer(it)?.sendMessage(
                "§e${player.name} left the party."
            )
        }
    }

    fun showMembers(player: Player) {
        val settings = getPlayerSettings(player)
        val group = groups[settings.groupId] ?: run {
            player.sendMessage("§cYou are not in a party.")
            return
        }
        player.sendMessage("§b===== EchoWave Party =====")
        player.sendMessage(
            "§eOwner: ${
                Bukkit.getOfflinePlayer(group.owner).name
            }"
        )

        player.sendMessage("§aMembers:")
        group.members.forEach {
            player.sendMessage(
                " §7• ${
                    Bukkit.getOfflinePlayer(it).name
                }"
            )
        }

        player.sendMessage("§7Code: §f${group.joinCode}")
    }

    fun createGroup(player: Player){
        val settings = getPlayerSettings(player)

        if (!settings.groupId.isNullOrBlank()) {
            player.sendMessage("§c[Echowave] Leave your current party first.")
            return
        }

        val groupId = generatePartyCode()
        groups[groupId] = Group(
            owner = player.uniqueId,
            members = mutableSetOf(player.uniqueId),
            joinCode = groupId
        )
        settings.groupId = groupId
        player.sendMessage("§a[Echowave] Party created")


    }

    fun invitePlayer(player: Player,target: String){
        val settings = getPlayerSettings(player)
        if (settings.groupId.isNullOrBlank()) {
            createGroup(player)
        }
        if(settings.groupId == null) return

        val group = groups[settings.groupId] ?: return

        if(group.owner != player.uniqueId){
            player.sendMessage("§c[Echowave] Only the party owner can invite players.")
            return
        }

        val inPlayer = Bukkit.getOnlinePlayers()
            .find { it.name.equals(target, ignoreCase = true) }

        if(inPlayer == null){
            player.sendMessage("§c[Echowave] $target is not online.")
            return
        }

        if(inPlayer.name == player.name){
            player.sendMessage("§c[Echowave] You cannot invite yourself.")
            return
        }

        if(inPlayer.uniqueId in group.members){
            player.sendMessage("§e[Echowave] ${inPlayer.name} is already in your party.")
            return
        }

        val invites = pendingInvites.getOrPut(inPlayer.uniqueId) { mutableListOf() }

        val alreadyInvited = invites.any { invite ->
            invite.partyId == settings.groupId
        }
        if (alreadyInvited) {
            player.sendMessage("§e[Echowave] ${inPlayer.name} already has an invite.")
            return
        }
        invites.add(GroupInvite(partyId = settings.groupId!!, code = group.joinCode))

        inPlayer.sendMessage("§a[Echowave] ${player.name} invited you to a party. §b${group.joinCode}")
        player.sendMessage("§a[Echowave] Invite sent to ${inPlayer.name}.")


    }

    fun kickPlayer(player: Player, target: String) {
        val settings = getPlayerSettings(player)

        val groupId = settings.groupId ?: run {
            player.sendMessage("§c[Echowave] You are not in a party.")
            return
        }

        val group = groups[groupId] ?: return

        if (group.owner != player.uniqueId) {
            player.sendMessage("§c[Echowave] Only the party owner can kick players.")
            return
        }

        val targetPlayer = Bukkit.getOnlinePlayers()
            .find { it.name.equals(target, ignoreCase = true) }

        if (targetPlayer == null) {
            player.sendMessage("§c[Echowave] $target is not online.")
            return
        }

        if (targetPlayer.uniqueId == player.uniqueId) {
            player.sendMessage("§c[Echowave] Use /echo leave instead.")
            return
        }

        if (targetPlayer.uniqueId !in group.members) {
            player.sendMessage("§c[Echowave] ${targetPlayer.name} is not in your party.")
            return
        }

        group.members.remove(targetPlayer.uniqueId)
        getPlayerSettings(targetPlayer).groupId = null

        targetPlayer.sendMessage("§cYou were kicked from the party.")

        group.members.forEach {
            Bukkit.getPlayer(it)?.sendMessage(
                "§e${targetPlayer.name} was kicked from the party."
            )
        }

        player.sendMessage("§a${targetPlayer.name} has been kicked.")
    }

    fun transferOwnership(player: Player, target: String) {
        val settings = getPlayerSettings(player)

        val groupId = settings.groupId ?: run {
            player.sendMessage("§c[Echowave] You are not in a party.")
            return
        }

        val group = groups[groupId] ?: return

        if (group.owner != player.uniqueId) {
            player.sendMessage("§c[Echowave] Only the party owner can transfer ownership.")
            return
        }

        val targetPlayer = Bukkit.getOnlinePlayers()
            .find { it.name.equals(target, ignoreCase = true) }

        if (targetPlayer == null) {
            player.sendMessage("§c[Echowave] $target is not online.")
            return
        }

        if (targetPlayer.uniqueId !in group.members) {
            player.sendMessage("§c[Echowave] ${targetPlayer.name} is not in your party.")
            return
        }

        if (targetPlayer.uniqueId == player.uniqueId) {
            player.sendMessage("§c[Echowave] You are already the owner.")
            return
        }

        group.owner = targetPlayer.uniqueId

        group.members.forEach {
            Bukkit.getPlayer(it)?.sendMessage(
                "§a${targetPlayer.name} is now the party owner."
            )
        }
    }



}
