package com.example.terminalabsensi.data.local.dao

import androidx.room.*
import com.example.terminalabsensi.data.local.entity.SampelWajah

@Dao
interface SampelWajahDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sampelWajah: SampelWajah)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sampelWajahList: List<SampelWajah>)

    @Delete
    suspend fun delete(sampelWajah: SampelWajah)

    @Query("DELETE FROM sampel_wajah WHERE idKaryawan = :idKaryawan")
    suspend fun deleteAllByKaryawan(idKaryawan: String)

    @Query("SELECT * FROM sampel_wajah WHERE idKaryawan = :idKaryawan")
    suspend fun getByKaryawan(idKaryawan: String): List<SampelWajah>

    @Query("SELECT * FROM sampel_wajah")
    suspend fun getAll(): List<SampelWajah>

    @Query("SELECT COUNT(*) FROM sampel_wajah WHERE idKaryawan = :idKaryawan")
    suspend fun countByKaryawan(idKaryawan: String): Int
}