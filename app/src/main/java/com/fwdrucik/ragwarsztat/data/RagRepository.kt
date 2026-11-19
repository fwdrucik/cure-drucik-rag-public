package com.fwdrucik.ragwarsztat.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.fwdrucik.ragwarsztat.model.MessageSender
import com.fwdrucik.ragwarsztat.model.RagChatMessage
import com.fwdrucik.ragwarsztat.model.RagEngineType
import com.fwdrucik.ragwarsztat.model.RagSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class RagRepository {

    suspend fun pingEngine(engine: RagEngineType, host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = when (engine) {
                RagEngineType.LM_STUDIO_RAG -> "http://$host:$port/api/status"
                RagEngineType.ANYTHING_LLM -> "http://$host:$port/api/v1/ping"
            }
            val url = URL(endpoint)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 4000

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success("✅ Połączono (${engine.displayName}): HTTP $code")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Błąd połączenia z $host:$port - ${e.message}"))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun cleanVramAndRam(
        host: String,
        port: Int,
        targetEngine: RagEngineType,
        targetModel: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/system/free_memory")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("engine", targetEngine.name)
                put("target_model", targetModel ?: "bielik-11b-v3.0-instruct-heretic-mpoa")
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(respStr)
                Result.success(json.optString("message", "VRAM i RAM wyczyszczone!"))
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
                Result.failure(Exception("HTTP $code: $err"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Błąd czyszczenia pamięci: ${e.message}"))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun queryRag(
        query: String,
        engine: RagEngineType,
        host: String,
        port: Int,
        selectedModel: String,
        workspaceSlug: String = "warsztat-pdf"
    ): Result<RagChatMessage> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (engine == RagEngineType.LM_STUDIO_RAG) {
                val url = URL("http://$host:$port/api/chat")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 10000
                conn.readTimeout = 70000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("message", query)
                    put("model", selectedModel)
                    put("temperature", 0.0)
                    put("top_k", 4)
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code == 200) {
                    val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respStr)
                    val text = json.optString("response", "Brak odpowiedzi z serwera RAG.")
                    val model = json.optString("model_used", selectedModel)
                    
                    val sources = mutableListOf<RagSource>()
                    val arr = json.optJSONArray("sources")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val fname = item.optString("filename", "Dokument.pdf")
                            val pageNum = item.optInt("page", 1)
                            val encodedFname = URLEncoder.encode(fname, "UTF-8")
                            val pageImgUrl = "http://$host:$port/api/pdf/page_image?filename=$encodedFname&page=$pageNum&dpi=140"
                            val downloadUrl = "http://$host:$port/api/pdf/download?filename=$encodedFname"

                            sources.add(
                                RagSource(
                                    id = item.optInt("id", i + 1),
                                    filename = fname,
                                    brand = item.optString("brand", "Dokumentacja"),
                                    page = pageNum,
                                    totalPages = item.optInt("total_pages", 1),
                                    pageImageUrl = pageImgUrl,
                                    downloadUrl = downloadUrl,
                                    snippet = item.optString("snippet", "")
                                )
                            )
                        }
                    }

                    Result.success(
                        RagChatMessage(
                            id = "ai-${UUID.randomUUID()}",
                            sender = MessageSender.MECHANIK_AI,
                            text = text,
                            sources = sources,
                            engineUsed = engine.displayName,
                            modelUsed = model
                        )
                    )
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
                    Result.failure(Exception("HTTP $code: $err"))
                }
            } else {
                // AnythingLLM Integration
                val url = URL("http://$host:$port/api/v1/workspace/$workspaceSlug/chat")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 10000
                conn.readTimeout = 70000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    put("message", query)
                    put("mode", "query")
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respStr)
                    val textResponse = json.optString("textResponse", "Brak odpowiedzi AnythingLLM")
                    
                    val sources = mutableListOf<RagSource>()
                    val sourcesArr = json.optJSONArray("sources")
                    if (sourcesArr != null) {
                        for (i in 0 until sourcesArr.length()) {
                            val s = sourcesArr.getJSONObject(i)
                            val fname = s.optString("title", "Dokument.pdf")
                            val pageNum = s.optInt("page", 1)
                            val encodedFname = URLEncoder.encode(fname, "UTF-8")
                            val pageImgUrl = "http://$host:$port/api/pdf/page_image?filename=$encodedFname&page=$pageNum&dpi=140"
                            val downloadUrl = "http://$host:$port/api/pdf/download?filename=$encodedFname"

                            sources.add(
                                RagSource(
                                    id = i + 1,
                                    filename = fname,
                                    brand = "AnythingLLM RAG",
                                    page = pageNum,
                                    totalPages = 1,
                                    pageImageUrl = pageImgUrl,
                                    downloadUrl = downloadUrl,
                                    snippet = s.optString("text", "")
                                )
                            )
                        }
                    }

                    Result.success(
                        RagChatMessage(
                            id = "ai-${UUID.randomUUID()}",
                            sender = MessageSender.MECHANIK_AI,
                            text = textResponse,
                            sources = sources,
                            engineUsed = engine.displayName,
                            modelUsed = "AnythingLLM Native RAG"
                        )
                    )
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
                    Result.failure(Exception("AnythingLLM HTTP $code: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Błąd zapytania do silnika $engine: ${e.message}"))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun loadPdfPageImage(
        filename: String,
        page: Int,
        host: String,
        port: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val localFolders = listOf(
            File("/sdcard/03_PDF", filename),
            File("/storage/emulated/0/03_PDF", filename),
            File("/storage/emulated/0/Download/03_PDF", filename)
        )
        for (f in localFolders) {
            if (f.exists()) {
                val localBitmap = renderLocalPdfPage(f, page)
                if (localBitmap != null) return@withContext localBitmap
            }
        }

        var conn: HttpURLConnection? = null
        try {
            val encodedFname = URLEncoder.encode(filename, "UTF-8")
            val urlStr = "http://$host:$port/api/pdf/page_image?filename=$encodedFname&page=$page&dpi=150"
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000

            if (conn.responseCode == 200) {
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun renderLocalPdfPage(file: File, page1Indexed: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageIdx = (page1Indexed - 1).coerceIn(0, renderer.pageCount - 1)
            page = renderer.openPage(pageIdx)
            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }
}
