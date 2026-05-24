package com.pforhan.alphathinker.dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val synopsis: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
