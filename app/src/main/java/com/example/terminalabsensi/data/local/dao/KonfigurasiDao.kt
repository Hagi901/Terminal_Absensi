package com.example.terminalabsensi.data.local.dao

import androidx.room.*
import com.example.terminalabsensi.data.local.entity.Konfigurasi
import kotlinx.coroutines.flow.Flow

@Dao
interface KonfigurasiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(konfigurasi: Konfigurasi)

    @Query("SELECT * FROM konfigurasi WHERE id = 1")
    suspend fun get(): Konfigurasi?

    @Query("SELECT * FROM konfigurasi WHERE id = 1")
    fun getFlow(): Flow<Konfigurasi?>
}