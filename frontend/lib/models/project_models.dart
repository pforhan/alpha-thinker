enum ProjectUpdateMode {
  keep,
  clear,
  revalidate,
}

class ProjectDto {
  String id;
  String synopsis;
  String editableTitle;
  int createdAt;
  int updatedAt;
  String status;
  List<QuestionDto> questions;

  ProjectDto({
    required this.id,
    required this.synopsis,
    required this.editableTitle,
    required this.createdAt,
    required this.updatedAt,
    required this.status,
    required this.questions,
  });

  factory ProjectDto.fromMap(Map<String, dynamic> map) {
    return ProjectDto(
      id: map['id'] as String,
      synopsis: map['synopsis'] as String,
      editableTitle: map['editableTitle'] as String,
      createdAt: map['createdAt'] as int,
      updatedAt: map['updatedAt'] as int,
      status: map['status'] as String,
      questions: (map['questions'] as List<dynamic>)
          .map((e) => QuestionDto.fromMap(e as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'synopsis': synopsis,
      'editableTitle': editableTitle,
      'createdAt': createdAt,
      'updatedAt': updatedAt,
      'status': status,
      'questions': questions.map((q) => q.toMap()).toList(),
    };
  }
}

class QuestionDto {
  String id;
  String text;
  int timestamp;
  String contextId;
  int? ignoredAt;
  List<AnswerDto> answers;

  QuestionDto({
    required this.id,
    required this.text,
    required this.timestamp,
    required this.contextId,
    this.ignoredAt,
    required this.answers,
  });

  factory QuestionDto.fromMap(Map<String, dynamic> map) {
    return QuestionDto(
      id: map['id'] as String,
      text: map['text'] as String,
      timestamp: map['timestamp'] as int,
      contextId: map['contextId'] as String,
      ignoredAt: map['ignoredAt'] as int?,
      answers: (map['answers'] as List<dynamic>)
          .map((e) => AnswerDto.fromMap(e as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'text': text,
      'timestamp': timestamp,
      'contextId': contextId,
      'ignoredAt': ignoredAt,
      'answers': answers.map((a) => a.toMap()).toList(),
    };
  }
}

class AnswerDto {
  int id;
  String questionId;
  String text;
  int? answeredAt;
  int? modifiedAt;
  int? deletedAt;

  AnswerDto({
    required this.id,
    required this.questionId,
    required this.text,
    this.answeredAt,
    this.modifiedAt,
    this.deletedAt,
  });

  factory AnswerDto.fromMap(Map<String, dynamic> map) {
    return AnswerDto(
      id: map['id'] as int,
      questionId: map['questionId'] as String,
      text: map['text'] as String,
      answeredAt: map['answeredAt'] as int?,
      modifiedAt: map['modifiedAt'] as int?,
      deletedAt: map['deletedAt'] as int?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'questionId': questionId,
      'text': text,
      'answeredAt': answeredAt,
      'modifiedAt': modifiedAt,
      'deletedAt': deletedAt,
    };
  }
}
