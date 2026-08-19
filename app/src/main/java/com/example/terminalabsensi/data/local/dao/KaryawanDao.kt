package com.example.terminalabsensi.data.local.dao

import androidx.room.*
import com.example.terminalabsensi.data.local.entity.Karyawan
import kotlinx.coroutines.flow.Flow

@Dao
interface KaryawanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(karyawan: Karyawan)

    @Update
    suspend fun update(karyawan: Karyawan)

    @Delete
    suspend fun delete(karyawan: Karyawan)

    @Query("SELECT * FROM karyawan WHERE idKaryawan = :id")
    suspend fun getById(id: String): Karyawan?

    @Query("SELECT * FROM karyawan ORDER BY nama ASC")
    fun getAll(): Flow<List<Karyawan>>

    @Query("SELECT * FROM karyawan WHERE statusAktif = 1 ORDER BY nama ASC")
    fun getAllAktif(): Flow<List<Karyawan>>

    @Query("SELECT EXISTS(SELECT 1 FROM karyawan WHERE idKaryawan = :id)")
    suspend fun isIdExists(id: String): Boolean
}