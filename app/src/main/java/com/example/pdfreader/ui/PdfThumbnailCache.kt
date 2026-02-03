package com.example.pdfreader.ui

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

object PdfThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

private val thumbnailSemaphore = Semaphore(permits = 2)

suspend fun loadPdfThumbnail(
    context: Context,
    uriString: String,
    targetWidthPx: Int
): Bitmap? {
    val widthBucket = targetWidthPx.coerceIn(120, 720).let { ((it + 49) / 50) * 50 }
    val key = "$uriString@$widthBucket"
    PdfThumbnailCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        val file = thumbnailFile(context, uriString, widthBucket)
        if (file.exists()) {
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            if (decoded != null) {
                PdfThumbnailCache.put(key, decoded)
                return@withContext decoded
            } else {
                file.delete()
            }
        }

        thumbnailSemaphore.withPermit {
            PdfThumbnailCache.get(key)?.let { return@withContext it }
            if (file.exists()) {
                val decoded = BitmapFactory.decodeFile(file.absolutePath)
                if (decoded != null) {
                    PdfThumbnailCache.put(key, decoded)
                    return@withContext decoded
                } else {
                    file.delete()
                }
            }

            val generated = generatePdfThumbnail(context, uriString, widthBucket)
            if (generated != null) {
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    generated.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                PdfThumbnailCache.put(key, generated)
            }
            generated
        }
    }
}

private fun thumbnailFile(context: Context, uriString: String, widthBucket: Int): File {
    val dir = File(context.cacheDir, "pdf_thumbs")
    val name = "${uriString.hashCode().toString(16)}_${widthBucket}.png"
    return File(dir, name)
}

private fun generatePdfThumbnail(context: Context, uriString: String, targetWidthPx: Int): Bitmap? {
    val resolver: ContentResolver = context.contentResolver
    val uri = Uri.parse(uriString)
    val pfd = resolver.openFileDescriptor(uri, "r") ?: return null
    val renderer = PdfRenderer(pfd)
    try {
        if (renderer.pageCount <= 0) return null
        val page = renderer.openPage(0)
        try {
            val scale = targetWidthPx.toFloat() / page.width.toFloat()
            val targetHeightPx = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(
                targetWidthPx.coerceAtLeast(1),
                targetHeightPx,
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    } finally {
        renderer.close()
        pfd.close()
    }
}
