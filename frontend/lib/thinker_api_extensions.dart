import 'thinker_api.dart';

extension AnswerDtoExtension on AnswerDto {
  bool get isAnswered => text.trim().isNotEmpty;
}

extension QuestionDtoExtension on QuestionDto {
  AnswerDto? get currentAnswer {
    if (answers.isEmpty) return null;
    final last = answers.last;
    return last.deletedAt == null ? last : null;
  }
}
