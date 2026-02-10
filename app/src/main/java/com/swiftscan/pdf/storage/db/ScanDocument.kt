package com.swiftscan.pdf.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val thumbnailPath: String?,
    val pageCount: Int,
    val fileSize: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val mimeType: String = "application/pdf",
)
