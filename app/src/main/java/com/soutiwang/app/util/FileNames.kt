package com.soutiwang.app.util

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

fun ContentResolver.displayName(uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor: Cursor? ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "题库.xlsx"
}

fun normalizeKeyword(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")
