import '../models/project_models.dart';
import '../thinker_api_extensions.dart';

enum QuestionFilter {
  unanswered,
  answered,
  ignored;

  String get displayLabel {
    switch (this) {
      case QuestionFilter.unanswered:
        return 'Unanswered';
      case QuestionFilter.answered:
        return 'Answered';
      case QuestionFilter.ignored:
        return 'Ignored';
    }
  }

  List<QuestionDto> apply(List<QuestionDto> questions) {
    switch (this) {
      case QuestionFilter.unanswered:
        return questions.where((q) => q.isUnanswered).toList();
      case QuestionFilter.answered:
        return questions.where((q) => q.isAnswered && !q.isIgnored).toList();
      case QuestionFilter.ignored:
        return questions.where((q) => q.isIgnored).toList();
    }
  }
}
