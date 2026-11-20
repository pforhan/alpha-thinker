package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
actual fun rememberInterchangeFilePicker(
  onPicked: (List<PickedImportFile>) -> Unit,
  onMessage: (String) -> Unit,
): (() -> Unit)? = remember(onPicked, onMessage) {
  {
    pickInterchangeFiles(
      onPicked = { files ->
        if (files.isEmpty()) return@pickInterchangeFiles
        onPicked(files)
      },
      onError = { onMessage(it) },
    )
  }
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalWasmJsInterop::class)
private fun pickInterchangeFiles(
  onPicked: (List<PickedImportFile>) -> Unit,
  onError: (String) -> Unit,
) {
  // JS reads selected files as base64 data URLs and passes a JSON array string:
  // [{"name":"...","data":"<base64>"}, ...]
  pickFilesAsJsonBase64(
    accept = ".thinker,.json,application/json,application/octet-stream,application/gzip".toJsString(),
    onResult = { json ->
      val files = decodePickedFilesJson(json.toString())
      onPicked(files)
    },
    onError = { msg -> onError(msg.toString()) },
  )
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodePickedFilesJson(json: String): List<PickedImportFile> {
  if (json.isBlank() || json == "[]") return emptyList()
  // Minimal parser for the controlled JSON shape produced by pickFilesAsJsonBase64.
  return runCatching {
    val result = mutableListOf<PickedImportFile>()
    var i = 0
    fun skipWs() { while (i < json.length && json[i].isWhitespace()) i++ }
    skipWs()
    if (i >= json.length || json[i] != '[') return emptyList()
    i++
    while (i < json.length) {
      skipWs()
      if (i < json.length && json[i] == ']') break
      if (i < json.length && json[i] == ',') { i++; skipWs() }
      if (i >= json.length || json[i] != '{') break
      i++
      var name: String? = null
      var dataB64: String? = null
      while (i < json.length && json[i] != '}') {
        skipWs()
        if (i < json.length && json[i] == ',') { i++; skipWs(); continue }
        val key = readJsonString(json, i).also { i = it.second }.first
        skipWs()
        if (i < json.length && json[i] == ':') i++
        skipWs()
        val value = readJsonString(json, i).also { i = it.second }.first
        when (key) {
          "name" -> name = value
          "data" -> dataB64 = value
        }
        skipWs()
      }
      if (i < json.length && json[i] == '}') i++
      val n = name
      val d = dataB64
      if (n != null && d != null) {
        val bytes = if (d.isEmpty()) ByteArray(0) else Base64.decode(d)
        result += PickedImportFile(name = n, bytes = bytes)
      }
    }
    result
  }.getOrDefault(emptyList())
}

private fun readJsonString(json: String, start: Int): Pair<String, Int> {
  var i = start
  while (i < json.length && json[i].isWhitespace()) i++
  if (i >= json.length || json[i] != '"') return "" to i
  i++ // opening quote
  val sb = StringBuilder()
  while (i < json.length) {
    val c = json[i++]
    when (c) {
      '"' -> return sb.toString() to i
      '\\' -> {
        if (i >= json.length) break
        when (val esc = json[i++]) {
          '"', '\\', '/' -> sb.append(esc)
          'b' -> sb.append('\b')
          'f' -> sb.append('\u000C')
          'n' -> sb.append('\n')
          'r' -> sb.append('\r')
          't' -> sb.append('\t')
          'u' -> {
            if (i + 4 <= json.length) {
              val hex = json.substring(i, i + 4)
              runCatching { sb.append(hex.toInt(16).toChar()) }
              i += 4
            }
          }
          else -> sb.append(esc)
        }
      }
      else -> sb.append(c)
    }
  }
  return sb.toString() to i
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun pickFilesAsJsonBase64(
  accept: JsString,
  onResult: (JsString) -> Unit,
  onError: (JsString) -> Unit,
): Unit = js(
  """(function() {
    try {
      var input = document.createElement('input');
      input.type = 'file';
      input.multiple = true;
      input.accept = accept;
      input.style.display = 'none';
      input.onchange = function() {
        try {
          var files = input.files;
          if (!files || files.length === 0) { onResult("[]"); return; }
          var results = new Array(files.length);
          var remaining = files.length;
          var failed = false;
          for (var idx = 0; idx < files.length; idx++) {
            (function(i) {
              var file = files[i];
              var reader = new FileReader();
              reader.onload = function() {
                if (failed) return;
                try {
                  var res = reader.result;
                  var b64 = '';
                  if (typeof res === 'string') {
                    var comma = res.indexOf(',');
                    b64 = comma >= 0 ? res.substring(comma + 1) : res;
                  }
                  results[i] = { name: file.name || 'import.thinker', data: b64 };
                  remaining--;
                  if (remaining === 0) onResult(JSON.stringify(results));
                } catch (e) {
                  failed = true;
                  onError('Failed to read file: ' + e);
                }
              };
              reader.onerror = function() {
                if (failed) return;
                failed = true;
                onError('Failed to read file');
              };
              reader.readAsDataURL(file);
            })(idx);
          }
        } catch (e) { onError('Failed to read selected files: ' + e); }
        finally { if (input.parentNode) input.parentNode.removeChild(input); }
      };
      document.body.appendChild(input);
      input.click();
    } catch (e) { onError('File picker unavailable: ' + e); }
  })()"""
)

