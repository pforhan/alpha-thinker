package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The planning-context budget: how many tokens of the interview so far may ride
 * along with a question prompt.
 *
 * Generous budgets let a model read every answer the project has accumulated;
 * tight ones keep a small edge model's context window clear by dropping the
 * earliest phases' answers first (the current phase is never dropped). The
 * choice lives beside the testing controls because both describe how much room
 * a generation is given rather than what it produces.
 */
@Composable
internal fun ContextBudgetItem(
  budgetTokens: Int,
  onBudgetChange: (Int) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Text(
        text = "Planning context budget",
        style = MaterialTheme.typography.titleSmall,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = "The token budget for the questions-and-answers sent with each " +
          "generation. When a project runs over it, the earliest phases' answers " +
          "are dropped first; the current phase's are always kept.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
      BudgetChoiceRow(
        budgetTokens = budgetTokens,
        onBudgetChange = onBudgetChange,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetChoiceRow(
  budgetTokens: Int,
  onBudgetChange: (Int) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    PlanningContext.BudgetOptionsTokens.forEach { tokens ->
      FilterChip(
        selected = budgetTokens == tokens,
        onClick = { onBudgetChange(tokens) },
        label = { Text("${tokens}t") },
        elevation = null,
      )
    }
  }
}
