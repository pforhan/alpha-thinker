package alphainterplanetary.thinker.tasks

/** Human title for a task kind, e.g. for the Task Manager rows and activity headlines. */
val TaskKind.title: String
  get() = when (this) {
    TaskKind.QuestionGeneration -> "Question generation"
    TaskKind.TitleRecommendation -> "Title recommendation"
    TaskKind.SynopsisRewrite -> "Synopsis rewrite"
    TaskKind.AutoArchive -> "Auto-archive"
    TaskKind.OllamaModelPull -> "Ollama model pull"
    TaskKind.ModelFileDownload -> "Model download"
    TaskKind.ModelVerification -> "Model verification"
  }

/** Short label describing what a running task is doing, e.g. a list chip. */
val TaskKind.progressLabel: String
  get() = when (this) {
    TaskKind.QuestionGeneration -> "Generating questions"
    TaskKind.TitleRecommendation -> "Generating title"
    TaskKind.SynopsisRewrite -> "Rewriting synopsis"
    TaskKind.AutoArchive -> "Reviewing answers"
    TaskKind.OllamaModelPull -> "Pulling model"
    TaskKind.ModelFileDownload -> "Downloading model"
    TaskKind.ModelVerification -> "Verifying model"
  }
