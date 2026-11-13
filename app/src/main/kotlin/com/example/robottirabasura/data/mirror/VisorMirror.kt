package com.example.robottirabasura.data.mirror

import android.util.Log
import com.example.robottirabasura.domain.repository.RobotRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sends one JSON document to the visor. Replaceable in tests. */
fun interface VisorSender {
    @Throws(IOException::class)
    fun send(json: String)
}

class HttpVisorSender(baseUrl: String) : VisorSender {
    private val url = URL(baseUrl.trimEnd('/') + "/estado")

    override fun send(json: String) {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 1_500
            connection.readTimeout = 1_500
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(json.toByteArray()) }
            connection.responseCode // Waits for the answer; the body isn't needed.
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Optional mirror of the simulated robot in the MuJoCo visor (mujoco/visor_g1.py). It only reads the repository's
 * telemetry, never sends commands, and a missing visor only costs a log line: the app works the same without it.
 */
class VisorMirror(
    private val robot: RobotRepository,
    /** Null when no visor is configured: [start] does nothing. */
    private val sender: VisorSender?,
    private val scope: CoroutineScope,
    private val minIntervalMillis: Long = 400,
) {
    private val json = Json { encodeDefaults = true }

    fun start() {
        val sender = sender ?: return
        scope.launch(Dispatchers.IO) {
            var failing = false
            var failures = 0
            robot.telemetry.filterNotNull().conflate().collect { telemetry ->
                try {
                    sender.send(json.encodeToString(VisorState.serializer(), telemetry.toVisorState(robot.site)))
                    if (failing || failures == 0) Log.i(TAG, "Visor MuJoCo conectado")
                    failing = false
                    failures = 1
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // First failure and then one every ~10 s, so a closed visor doesn't flood the log.
                    if (failures++ % 25 == 0) Log.w(TAG, "Visor MuJoCo no disponible: ${e.message}")
                    failing = true
                }
                delay(minIntervalMillis)
            }
        }
    }

    private companion object {
        const val TAG = "VisorMirror"
    }
}
