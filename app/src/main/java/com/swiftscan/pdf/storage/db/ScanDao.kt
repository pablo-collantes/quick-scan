package com.swiftscan.pdf.storage.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun getAllScans(): Flow<List<ScanDocument>>

    @Query("SELECT * FROM scans WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchScans(query: String): Flow<List<ScanDocument>>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanDocument?

    @Insert
    suspend fun insert(document: ScanDocument): Long

    @Update
    suspend fun update(document: ScanDocument)

    @Delete
    suspend fun delete(document: ScanDocument)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun deleteById(id: Long)
}
