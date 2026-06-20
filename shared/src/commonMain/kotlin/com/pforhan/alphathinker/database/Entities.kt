package com.pforhan.alphathinker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val synopsis: String,
    val editableTitle: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val createdAt: Long,
    val ignoredAt: Long? = null
)

@Entity(tableName = "answers")
data class AnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val text: String,
    val answeredAt: Long,
    val modifiedAt: Long? = null
)
