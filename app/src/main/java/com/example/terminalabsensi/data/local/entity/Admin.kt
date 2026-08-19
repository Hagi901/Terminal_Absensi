package com.example.terminalabsensi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin")
data class Admin(
    @PrimaryKey
    val username: String,
    val pinPasswordHash: String,
    val updatedAt: Long = System.currentTimeMillis()
)