import 'thinker_api.dart';

extension AnswerDtoExtension on AnswerDto {
  bool get isAnswered =>
      answeredAt != null && text
          .trim()
          .isNotEmpty;

  bool get isDraft => answeredAt == null;
}

extension QuestionDtoExtension on QuestionDto {
  AnswerDto? get currentAnswer {
    if (answers.isEmpty) return null;
    final last = answers.last;
    return last.deletedAt == null ? last : null;
  }

  bool get isAnswered => currentAnswer?.isAnswered ?? false;

  bool get isIgnored => ignoredAt != null;

  bool get isUnanswered => !isAnswered && !isIgnored;
}

extension ProjectDtoExtension on ProjectDto {
  List<QuestionDto> get unansweredQuestions =>
      questions.where((q) => q.isUnanswered).toList();
}
