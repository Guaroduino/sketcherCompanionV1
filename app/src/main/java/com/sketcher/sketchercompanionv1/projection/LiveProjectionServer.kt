package com.sketcher.sketchercompanionv1.projection

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holds state for each connected browser client.
 */
data class ProjectionClient(
    val ws: NanoWSD.WebSocket,
    val id: Int,
    @Volatile var clientWidth: Int = 1920,
    @Volatile var clientHeight: Int = 1080,
    @Volatile var mode: String = "sync",   // "sync" | "fixed"
    @Volatile var paused: Boolean = false
)

/**
 * Embedded HTTP + WebSocket server for live canvas projection.
 *
 * - GET /     → serves the HTML projection page
 * - GET /ws   → upgrades to WebSocket
 *
 * Call [broadcastSyncFrame] from the capture loop to push viewport frames.
 * Call [broadcastFixedSnapshot] to push full-canvas snapshots.
 */
class LiveProjectionServer(
    port: Int,
    val getCurrentMode: () -> String,
    private val onClientCountChanged: (Int) -> Unit,
    val onClientUpdated: () -> Unit
) : NanoWSD(port) {

    val clients = CopyOnWriteArrayList<ProjectionClient>()
    private val idCounter = AtomicInteger(0)

    override fun serveHttp(session: IHTTPSession): Response {
        return when (session.uri) {
            "/", "/index.html" -> {
                val res = newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    HtmlProjectionPage.HTML
                )
                res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                res.addHeader("Pragma", "no-cache")
                res.addHeader("Expires", "0")
                res
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return ProjectionWebSocket(handshake, this)
    }

    // ── BROADCAST METHODS ─────────────────────────────────────────────────

    /**
     * Send a viewport-cropped JPEG frame (tag 0x01) to all sync-mode clients.
     * [jpegByClient] maps client ID → their custom-cropped JPEG bytes.
     */
    fun broadcastSyncFrames(jpegByClient: Map<Int, ByteArray>) {
        val tagged = HashMap<Int, ByteArray>()
        for ((id, jpeg) in jpegByClient) {
            tagged[id] = byteArrayOf(0x01) + jpeg
        }
        for (client in clients) {
            if (!client.paused && client.mode == "sync") {
                val payload = tagged[client.id] ?: continue
                try { client.ws.send(payload) } catch (e: Exception) { /* client gone */ }
            }
        }
    }

    /**
     * Send the full-canvas snapshot JPEG (tag 0x02) to all fixed-mode clients.
     */
    fun broadcastFixedSnapshot(jpeg: ByteArray) {
        val payload = byteArrayOf(0x02) + jpeg
        for (client in clients) {
            if (client.mode == "fixed") {
                try { client.ws.send(payload) } catch (e: Exception) { /* client gone */ }
            }
        }
    }

    fun notifyClientCount() {
        val count = clients.size
        onClientCountChanged(count)
        val json = "{\"clients\":$count}"
        for (client in clients) {
            try { client.ws.send(json) } catch (_: Exception) {}
        }
    }

    // ── INNER WEBSOCKET HANDLER ────────────────────────────────────────────

    private inner class ProjectionWebSocket(
        handshake: IHTTPSession,
        private val server: LiveProjectionServer
    ) : WebSocket(handshake) {

        private lateinit var projectionClient: ProjectionClient

        override fun onOpen() {
            projectionClient = ProjectionClient(
                ws = this,
                id = idCounter.incrementAndGet(),
                mode = server.getCurrentMode()
            )
            server.clients.add(projectionClient)
            Log.d("Projection", "Client connected: ${projectionClient.id} (total: ${server.clients.size})")
            server.notifyClientCount()
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) {
            val text = message.textPayload ?: return
            try {
                // Simple JSON parsing without Gson to keep dependencies light
                when {
                    text.contains("\"type\":\"hello\"") -> {
                        projectionClient.clientWidth = extractInt(text, "width") ?: projectionClient.clientWidth
                        projectionClient.clientHeight = extractInt(text, "height") ?: projectionClient.clientHeight
                        Log.d("Projection", "Client ${projectionClient.id} dims: ${projectionClient.clientWidth}x${projectionClient.clientHeight}")
                        server.onClientUpdated()
                    }
                    text.contains("\"type\":\"log\"") -> {
                        val level = extractString(text, "level") ?: "log"
                        val msg = extractString(text, "message") ?: ""
                        Log.d("ProjectionClientLogs", "[Client ${projectionClient.id}] [$level] $msg")
                    }
                    text.contains("\"mode\":\"sync\"") -> {
                        projectionClient.mode = "sync"
                        server.onClientUpdated()
                    }
                    text.contains("\"mode\":\"fixed\"") -> {
                        projectionClient.mode = "fixed"
                        server.onClientUpdated()
                    }
                    text.contains("\"cmd\":\"pause\"") -> {
                        projectionClient.paused = true
                        server.onClientUpdated()
                    }
                    text.contains("\"cmd\":\"resume\"") -> {
                        projectionClient.paused = false
                        server.onClientUpdated()
                    }
                }
            } catch (e: Exception) {
                Log.w("Projection", "Error parsing client message: $text", e)
            }
        }

        override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            server.clients.remove(projectionClient)
            Log.d("Projection", "Client disconnected: ${projectionClient.id} (total: ${server.clients.size})")
            server.notifyClientCount()
        }

        override fun onPong(pong: NanoWSD.WebSocketFrame) {}
        override fun onException(exception: java.io.IOException) {
            Log.w("Projection", "WS exception for client ${projectionClient.id}", exception)
            server.clients.remove(projectionClient)
            server.notifyClientCount()
        }

        private fun extractInt(json: String, key: String): Int? {
            val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
            return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
        }

        private fun extractString(json: String, key: String): String? {
            val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}
