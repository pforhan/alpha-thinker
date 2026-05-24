package com.pforhan.alphathinker.dao

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "answers",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indexes = [Index("question_id")]
)
data class AnswerEntity(
    @PrimaryKey
    @ColumnInfo(name = "question_id")
    val questionId: String,

    @ColumnInfo(name = "context_id")
    val contextId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "answered_at")
    val answeredAt: Instant,

    @ColumnInfo(name = "modified_at")
    val modifiedAt: Instant?
)
