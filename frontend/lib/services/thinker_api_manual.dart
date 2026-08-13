import 'package:flutter/services.dart';
import '../models/project_models.dart';

class ThinkerApi {
  static const MethodChannel _channel = MethodChannel('dev.flutter.pigeon.alphainterplanetary.thinker.ThinkerApi');

  Future<ProjectDto> createProject(String synopsis, String? title) async {
    final Map<String, dynamic>? result = await _channel.invokeMethod('createProject', {
      'synopsis': synopsis,
      'title': title,
    });
    return ProjectDto.fromMap(result!);
  }

  Future<List<ProjectDto>> getAllProjects() async {
    final List<dynamic>? result = await _channel.invokeMethod('getAllProjects');
    return (result as List<dynamic>).map((e) => ProjectDto.fromMap(e as Map<String, dynamic>)).toList();
  }

  Future<ProjectDto> getProject(String id) async {
    final Map<String, dynamic>? result = await _channel.invokeMethod('getProject', {'id': id});
    return ProjectDto.fromMap(result!);
  }

  Future<List<QuestionDto>> getUnansweredQuestions(String projectId) async {
    final List<dynamic>? result = await _channel.invokeMethod('getUnansweredQuestions', {'projectId': projectId});
    return (result as List<dynamic>).map((e) => QuestionDto.fromMap(e as Map<String, dynamic>)).toList();
  }

  Future<void> deleteProject(String id) async {
    await _channel.invokeMethod('deleteProject', {'id': id});
  }

  Future<void> updateAnswer(String projectId, String questionId, String text, bool isDraft) async {
    await _channel.invokeMethod('updateAnswer', {
      'projectId': projectId,
      'questionId': questionId,
      'text': text,
      'isDraft': isDraft,
    });
  }

  Future<void> ignoreQuestion(String projectId, String questionId) async {
    await _channel.invokeMethod('ignoreQuestion', {'projectId': projectId, 'questionId': questionId});
  }

  Future<void> unignoreQuestion(String projectId, String questionId) async {
    await _channel.invokeMethod('unignoreQuestion', {'projectId': projectId, 'questionId': questionId});
  }

  Future<void> deleteAnswer(String projectId, String questionId, int answerId) async {
    await _channel.invokeMethod('deleteAnswer', {
      'projectId': projectId,
      'questionId': questionId,
      'answerId': answerId,
    });
  }

  Future<ProjectDto> updateProject(String id, String title, String synopsis, ProjectUpdateMode updateMode) async {
    final Map<String, dynamic>? result = await _channel.invokeMethod('updateProject', {
      'id': id,
      'title': title,
      'synopsis': synopsis,
      'updateMode': updateMode.name, // Using name for enum serialization as a simple approach
    });
    return ProjectDto.fromMap(result!);
  }
}
