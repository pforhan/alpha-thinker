import 'package:flutter/material.dart';
import '../injection.dart';
import '../thinker_api.dart';
import '../thinker_api_extensions.dart';
import '../services/project_service.dart';

class ProjectDetailScreen extends StatefulWidget {
  final ProjectDto project;
  const ProjectDetailScreen({super.key, required this.project});

  @override
  State<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends State<ProjectDetailScreen> {
  final ProjectService _service = getIt<ProjectService>();
  List<QuestionDto>? _questions;
  bool _loading = true;
  String _filter = 'Unanswered';

  @override
  void initState() {
    super.initState();
    _loadQuestions();
  }

  Future<void> _loadQuestions() async {
    setState(() => _loading = true);
    try {
      final project = await _service.getProject(widget.project.id);
      setState(() {
        _questions = project.questions;
        _loading = false;
      });
    } catch (e) {
      debugPrint('Error loading questions: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error loading questions: $e'),
            duration: const Duration(seconds: 10),
            action: SnackBarAction(label: 'Dismiss', onPressed: () {}),
          ),
        );
      }
      setState(() => _loading = false);
    }
  }

  Future<void> _ignoreQuestion(QuestionDto question) async {
    try {
      await _service.ignoreQuestion(widget.project.id, question.id);
      _loadQuestions();
    } catch (e) {
      debugPrint('Error ignoring question: $e');
    }
  }

  Future<void> _unignoreQuestion(QuestionDto question) async {
    try {
      await _service.unignoreQuestion(widget.project.id, question.id);
      _loadQuestions();
    } catch (e) {
      debugPrint('Error unignoring question: $e');
    }
  }

  Future<void> _answerQuestion(QuestionDto question) async {
    final answerController = TextEditingController();
    final current = question.currentAnswer;
    
    if (current != null && current.isAnswered) {
      answerController.text = current.text;
    }

    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(question.text),
        content: TextField(
          controller: answerController,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: 'Enter your answer...',
          ),
          maxLines: 5,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          if (current != null && current.isAnswered)
            TextButton(
              onPressed: () async {
                debugPrint('Deleting answer for question ${question.id} in project ${widget.project.id}');
                await _service.deleteAnswer(widget.project.id, question.id, current.id);
                debugPrint('Successfully deleted answer');
                Navigator.pop(context, false);
                _loadQuestions();
              },
              child: const Text('Delete Answer', style: TextStyle(color: Colors.red)),
            ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Submit'),
          ),
        ],
      ),
    );

    if (result == true && answerController.text.isNotEmpty) {
      try {
        await _service.updateAnswer(widget.project.id, question.id, answerController.text, false);
        _loadQuestions();
      } catch (e) {
        debugPrint('Error updating answer: $e');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Error updating answer: $e'),
              duration: const Duration(seconds: 10),
              action: SnackBarAction(label: 'Dismiss', onPressed: () {}),
            ),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.project.editableTitle),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Synopsis:', style: Theme.of(context).textTheme.titleSmall),
                Text(widget.project.synopsis),
              ],
            ),
          ),
          const Divider(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Questions:', style: Theme.of(context).textTheme.titleMedium),
                SizedBox(
                  height: 30,
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        ChoiceChip(
                          label: const Text('Unanswered'),
                          selected: _filter == 'Unanswered',
                          onSelected: (selected) {
                            setState(() => _filter = 'Unanswered');
                          },
                        ),
                        const SizedBox(width: 8),
                        ChoiceChip(
                          label: const Text('Answered'),
                          selected: _filter == 'Answered',
                          onSelected: (selected) {
                            setState(() => _filter = 'Answered');
                          },
                        ),
                        const SizedBox(width: 8),
                         ChoiceChip(
                           label: const Text('Ignored'),
                           selected: _filter == 'Ignored',
                           onSelected: (selected) {
                             setState(() => _filter = 'Ignored');
                           },
                         ),

                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : () {
                     final filteredQuestions = _questions?.where((q) {
                       final isIgnored = q.ignoredAt != null;
                       final current = q.currentAnswer;
                       final hasAnswer = current != null && current.isAnswered;
                       if (_filter == 'Unanswered') return !hasAnswer && !isIgnored;
                       if (_filter == 'Answered') return hasAnswer && !isIgnored;
                       if (_filter == 'Ignored') return isIgnored;
                       return true;
                     }).toList();


                    if (filteredQuestions == null || filteredQuestions.isEmpty) {
                      return Center(
                        child: Text(
                          _filter == 'Unanswered' 
                            ? 'No unanswered questions.' 
                            : 'No ${_filter.toLowerCase()} questions.',
                          textAlign: TextAlign.center,
                        ),
                      );
                    }

                    return ListView.builder(
                      itemCount: filteredQuestions.length,
                      itemBuilder: (context, index) {
                         final question = filteredQuestions[index];
                         final isIgnored = question.ignoredAt != null;
                         final current = question.currentAnswer;
                         final hasAnswer = current != null && current.isAnswered;


                        return Card(
                          margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          child: ListTile(
                            title: Text(question.text),
                            subtitle: hasAnswer 
                              ? Text(current.text, maxLines: 1, overflow: TextOverflow.ellipsis)
                              : (isIgnored ? const Text('Ignored') : null),
                            trailing: Tooltip(
                              message: isIgnored ? 'Show question' : 'Ignore question',
                              child: IconButton(
                                icon: Icon(isIgnored ? Icons.visibility : Icons.visibility_off),
                                onPressed: () => isIgnored 
                                  ? _unignoreQuestion(question) 
                                  : _ignoreQuestion(question),
                              ),
                            ),
                            onTap: () => _answerQuestion(question),
                          ),
                        );
                      },
                    );
                  }(),
          ),
        ],
      ),
    );
  }
}
