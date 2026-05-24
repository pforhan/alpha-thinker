package com.pforhan.alphathinker.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pforhan.alphathinker.dao.AnswerEntity
import com.pforhan.alphathinker.dao.ProjectDao
import com.pforhan.alphathinker.dao.ProjectEntity
import com.pforhan.alphathinker.dao.QuestionEntity

@Database(
    entities = [ProjectEntity::class, QuestionEntity::class, AnswerEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
