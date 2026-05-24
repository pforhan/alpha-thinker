package com.pforhan.alphathinker

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.pforhan.alphathinker.database.AppDatabase
import com.pforhan.alphathinker.llm.MockLLMIntegration
import com.pforhan.alphathinker.repository.AndroidProjectStorage

class AlphaThinkerApp : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "alphathinker_db"
        ).build()
    }

    val repository by lazy {
        val storage = AndroidProjectStorage(database)
        val llm = MockLLMIntegration()
        com.pforhan.alphathinker.repository.ProjectRepository(storage, llm)
    }
}
