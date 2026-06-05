package com.pforhan.alphathinker.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.ConstructedBy
import androidx.room.RoomDatabaseConstructor
import com.pforhan.alphathinker.database.ProjectDao
import com.pforhan.alphathinker.database.QuestionDao
import com.pforhan.alphathinker.database.AnswerDao
import com.pforhan.alphathinker.database.ProjectEntity
import com.pforhan.alphathinker.database.QuestionEntity
import com.pforhan.alphathinker.database.AnswerEntity

@Database(
    entities = [ProjectEntity::class, QuestionEntity::class, AnswerEntity::class],
    version = 1,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun questionDao(): QuestionDao
    abstract fun answerDao(): AnswerDao
}

expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
