package com.swiftscan.pdf.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScanDocument::class],
    version = 1,
    exportSchema = false,
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
