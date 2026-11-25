package com.dohguard.shared.remote

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor HTTP/1.1 leve baseado em ServerSocket (sem dependências externas)
 * para comunicação local em rede Wi-Fi e pareamento direto entre ParentApp e KidsApp.
 */
class LocalHttpServer(
    private val handler: (HttpRequest) -> HttpResponse
) {
    data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    ) {
        fun header(name: String): String =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
    }

    data class HttpResponse(
        val statusCode: Int,
        val body: String,
        val contentType: String = "application/json; charset=utf-8"
    )

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workers: ExecutorService? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    val isRunning: Boolean
        get() = running.get()

    fun start(bindPort: Int = 0, loopbackOnly: Boolean = false): Int {
        if (running.get()) return port
        val bindAddr = if (loopbackOnly) InetAddress.getByName("127.0.0.1") else null
        val ss = ServerSocket(bindPort, 16, bindAddr)
        serverSocket = ss
        workers = Executors.newCachedThreadPool()
        running.set(true)

        acceptThread = Thread {
            while (running.get()) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                workers?.submit { handleClient(client) }
            }
        }.apply {
            isDaemon = true
            name = "MonitorDad-LocalHttpServer"
            start()
        }
        return ss.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        workers?.shutdownNow()
        workers = null
        acceptThread = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 5_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()
            val req = readHttpRequest(input) ?: return
            val response = runCatching { handler(req) }.getOrElse { err ->
                HttpResponse(
                    statusCode = 500,
                    body = """{"error":"${escapeJson(err.message ?: "Erro interno")}"}"""
                )
            }
            writeHttpResponse(output, response)
        }
    }

    private fun readHttpRequest(input: InputStream): HttpRequest? {
        val headerBytes = ByteArrayOutputStream()
        var state = 0
        while (true) {
            val b = input.read()
            if (b == -1) break
            headerBytes.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4 || headerBytes.size() > 32_768) break
        }
        val rawHeader = headerBytes.toString(Charsets.UTF_8.name())
        val lines = rawHeader.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val requestLineParts = lines[0].split(" ")
        if (requestLineParts.size < 2) return null
        val method = requestLineParts[0].uppercase()
        val fullPath = requestLineParts[1].substringBefore("?")

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) {
                headers[lines[i].substring(0, idx).trim()] = lines[i].substring(idx + 1).trim()
            }
        }

        val contentLength = headers.entries
            .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value?.toIntOrNull()?.coerceIn(0, 1_048_576) ?: 0

        val bodyBytes = ByteArray(contentLength)
        var readTotal = 0
        while (readTotal < contentLength) {
            val n = input.read(bodyBytes, readTotal, contentLength - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        val body = String(bodyBytes, 0, readTotal, Charsets.UTF_8)
        return HttpRequest(method = method, path = fullPath, headers = headers, body = body)
    }

    private fun writeHttpResponse(output: OutputStream, response: HttpResponse) {
        val statusText = when (response.statusCode) {
            200 -> "OK"
            201 -> "Created"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            else -> "Internal Server Error"
        }
        val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ${response.statusCode} $statusText\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)
        output.write(headers)
        output.write(bodyBytes)
        output.flush()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
