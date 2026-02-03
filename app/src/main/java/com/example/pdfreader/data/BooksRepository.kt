package com.example.pdfreader.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BooksRepository(private val context: Context, private val dao: BookDao) {
    fun books(): Flow<List<Book>> = dao.observeAll()
    fun book(id: Long): Flow<Book?> = dao.observeById(id)

    suspend fun addFromUri(uri: Uri): Long {
        val resolver = context.contentResolver
        val name = queryDisplayName(resolver, uri) ?: "未命名"
        val now = System.currentTimeMillis()
        val totalPages = queryPageCount(resolver, uri)
        val book = Book(
            title = name,
            uri = uri.toString(),
            addedAt = now,
            lastOpenedPage = 0,
            totalPages = totalPages
        )
        return dao.insert(book)
    }

    suspend fun updateLastPage(id: Long, page: Int, totalPages: Int) {
        val current = dao.getById(id)
        if (current != null) {
            dao.update(current.copy(lastOpenedPage = page, totalPages = totalPages))
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && index >= 0) return c.getString(index)
        }
        return null
    }

    private suspend fun queryPageCount(resolver: ContentResolver, uri: Uri): Int {
        return withContext(Dispatchers.IO) {
            val pfd = resolver.openFileDescriptor(uri, "r") ?: return@withContext 0
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            try {
                renderer.pageCount
            } finally {
                renderer.close()
                pfd.close()
            }
        }
    }

}
