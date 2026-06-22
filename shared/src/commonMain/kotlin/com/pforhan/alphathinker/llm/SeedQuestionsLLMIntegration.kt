package com.pforhan.alphathinker.llm

import com.pforhan.alphathinker.model.Question
import com.pforhan.alphathinker.util.randomUUID
import kotlinx.datetime.Clock

class SeedQuestionsLLMIntegration : LLMIntegration {
  override suspend fun generateInitialQuestions(synopsis: String): List<Question> {
    val now = Clock.System.now()
    return seedQuestions.map { text ->
      Question(
        id = randomUUID(),
        text = text,
        timestamp = now,
        contextId = "seed"
      )
    }
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
  ): List<Question> {
    // Lite edition has no automated follow-up questions
    return emptyList()
  }

  companion object {
    val seedQuestions = listOf(
      "What is the primary problem this project solves?",
      "Who is the ideal user or beneficiary?",
      "What is the single most important goal?",
      "What are three key milestones for the first month?",
      "What resources (time, money, tools) are currently available?",
      "What resources are still needed?",
      "What is the target completion date?",
      "What are the top three risks to success?",
      "How will you know if the project is successful?",
      "What is the \"Minimum Viable Product\" (MVP) version?",
      "What are the key technical constraints or requirements?",
      "Who are the primary stakeholders and decision-makers?",
      "What similar projects or competitors have you looked at?",
      "What is the long-term vision for this project?",
      "What are the non-negotiable features or qualities?",
      "How will this project be maintained or supported later?",
      "What is the estimated total budget?",
      "Are there any legal, ethical, or compliance factors?",
      "How will you promote or distribute the final result?",
      "What is the very first step you need to take?"
    )
  }
}
