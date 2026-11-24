package alphainterplanetary.thinker.ui.format

import kotlin.time.Instant

@OptIn(ExperimentalWasmJsInterop::class)
actual fun formatAbsoluteDate(instant: Instant, style: AbsoluteDateStyle): String {
  val styleName = when (style) {
    AbsoluteDateStyle.MonthDay -> "monthDay"
    AbsoluteDateStyle.MonthYear -> "monthYear"
    AbsoluteDateStyle.YearOnly -> "year"
  }
  return formatAbsoluteDateJs(instant.toEpochMilliseconds().toDouble(), styleName.toJsString()).toString()
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun formatAbsoluteDateJs(epochMs: Double, style: JsString): JsString = js(
  """(function() {
    var opts = {};
    if (style === 'monthDay') { opts.month = 'short'; opts.day = 'numeric'; }
    else if (style === 'monthYear') { opts.month = 'short'; opts.year = 'numeric'; }
    else { opts.year = 'numeric'; }
    try {
      return new Intl.DateTimeFormat(undefined, opts).format(new Date(epochMs));
    } catch (e) {
      var d = new Date(epochMs);
      return d.toISOString();
    }
  })()"""
)
