package alphainterplanetary.thinker.eval

actual suspend fun postEvalJson(url: String, body: String): EvalUploadResult =
  EvalUploadResult(false, "Sharing evaluation rows is not supported on this platform.")
