package com.pforhan.alphathinker.dao

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indexes = [Index("project_id")]
)
data class QuestionEntity(
    @ColumnInfo(name = "question_id")
    val questionId: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "round")
    val round: Int,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Instant
) {
    @PrimaryKey
    val id: String = questionId
}
