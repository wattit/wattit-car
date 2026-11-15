package app.wattit.car.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface HttpResult {
    data class Ok(val body: String) : HttpResult
    data class Status(val code: Int, val body: String) : HttpResult
    data class Failed(val cause: IOException) : HttpResult
}

/** Minimal HTTP client on HttpURLConnection: two endpoints do not justify an HTTP library. */
class WattitApi(private val baseUrl: String) {

    suspend fun plan(request: PlanRequest): HttpResult =
        send("POST", "/api/car/plan", ApiJson.encodePlanRequest(request))

    suspend fun timeline(day: String, mode: String): HttpResult =
        send("GET", "/api/timeline?day=$day&mode=$mode", null)

    private suspend fun send(method: String, path: String, body: String?): HttpResult =
        withContext(Dispatchers.IO) {
            val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) HttpResult.Ok(text) else HttpResult.Status(code, text)
            } catch (e: IOException) {
                HttpResult.Failed(e)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
