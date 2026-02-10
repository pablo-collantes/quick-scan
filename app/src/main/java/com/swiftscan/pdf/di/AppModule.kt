package com.swiftscan.pdf.di

import android.content.Context
import androidx.room.Room
import com.swiftscan.pdf.storage.db.ScanDao
import com.swiftscan.pdf.storage.db.ScanDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideScanDatabase(@ApplicationContext context: Context): ScanDatabase {
        return Room.databaseBuilder(
            context,
            ScanDatabase::class.java,
            "swiftscan_db",
        ).build()
    }

    @Provides
    @Singleton
    fun provideScanDao(database: ScanDatabase): ScanDao {
        return database.scanDao()
    }
}
