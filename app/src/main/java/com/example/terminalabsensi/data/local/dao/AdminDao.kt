package com.example.terminalabsensi.data.local.dao

import androidx.room.*
import com.example.terminalabsensi.data.local.entity.Admin

@Dao
interface AdminDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(admin: Admin)

    @Update
    suspend fun update(admin: Admin)

    @Query("SELECT * FROM admin WHERE username = :username")
    suspend fun getByUsername(username: String): Admin?

    @Query("SELECT EXISTS(SELECT 1 FROM admin)")
    suspend fun hasAdmin(): Boolean
}