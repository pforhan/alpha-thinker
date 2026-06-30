import 'package:flutter/material.dart';
import '../thinker_api.dart';
import '../thinker_api_extensions.dart';
import '../services/project_service.dart';
import '../injection.dart';

class AnswerDialog extends StatefulWidget {
  final ProjectDto project;
  final QuestionDto question;
  final Future<void> Function(String answer) onSubmit;

  const AnswerDialog({
    super.key,
    required this.project,
    required this.question,
    required this.onSubmit,
  });

  @override
  State<AnswerDialog> createState() => _AnswerDialogState();
}

class _AnswerDialogState extends State<AnswerDialog> {
  late TextEditingController _answerController;
  final ProjectService _service = getIt<ProjectService>();

  @override
  void initState() {
    super.initState();
    _answerController = TextEditingController();
    final current = widget.question.currentAnswer;
    if (current != null && current.isAnswered) {
      _answerController.text = current.text;
    }
  }

  @override
  void dispose() {
    _answerController.dispose();
    super.dispose();
  }

  Future<void> _handleAskLater() async {
    if (_answerController.text.isNotEmpty) {
      try {
        await _service.updateAnswer(
            widget.project.id, widget.question.id, _answerController.text,
            false, true);
      } catch (e) {
        debugPrint('Error saving draft: $e');
      }
    }
    Navigator.pop(context, 'ask_later');
  }

  Future<void> _handleDeleteAnswer() async {
    final current = widget.question.currentAnswer;
    if (current != null) {
      try {
        await _service.deleteAnswer(
            widget.project.id, widget.question.id, current.id);
      } catch (e) {
        debugPrint('Error deleting answer: $e');
      }
    }
    Navigator.pop(context, 'deleted');
  }

  @override
  Widget build(BuildContext context) {
    final current = widget.question.currentAnswer;
    return AlertDialog(
      title: Text(widget.question.text),
      content: TextField(
        controller: _answerController,
        autofocus: true,
        decoration: const InputDecoration(
          hintText: 'Enter your answer...',
        ),
        maxLines: 5,
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, 'cancel'),
          child: const Text('Cancel'),
        ),
        if (current == null || !current.isAnswered)
          TextButton(
            onPressed: _handleAskLater,
            child: const Text('Ask Later'),
          ),
        if (current != null && current.isAnswered)
          TextButton(
            onPressed: _handleDeleteAnswer,
            child: const Text(
                'Delete Answer', style: TextStyle(color: Colors.red)),
          ),
        ElevatedButton(
          onPressed: () async {
            await widget.onSubmit(_answerController.text);
            Navigator.pop(context, 'submit');
          },
          child: const Text('Submit'),
        ),
      ],
    );
  }
}
