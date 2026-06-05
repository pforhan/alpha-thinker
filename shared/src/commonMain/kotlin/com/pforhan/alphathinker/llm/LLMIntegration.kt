package com.pforhan.alphathinker.llm

import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.model.Project

interface LLMIntegration {
    @Throws(AnalysisFailure::class)
    suspend fun generateInitialQuestions(synopsis: String): List<Question>

    @Throws(AnalysisFailure::class)
    suspend fun generateFollowUpQuestions(synopsis: String, previousRound: Int): List<Question>

    class AnalysisFailure(override val message: String) : Exception(message)
}
