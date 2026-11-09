package alphainterplanetary.thinker.engine

/** One [PlanningEngine] interaction that the artificial testing delay can target. */
enum class EngineInteraction(val label: String) {
  TitleRecommendation("Title recommendation"),
  QuestionGeneration("Question generation"),
}
