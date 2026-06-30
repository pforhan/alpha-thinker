import 'package:flutter/material.dart';
import '../injection.dart';
import '../thinker_api.dart';
import '../thinker_api_extensions.dart';
import '../services/project_service.dart';
import '../services/preference_service.dart';
import '../widgets/question_item.dart';
import '../widgets/answer_dialog.dart';
import '../widgets/edit_project_dialog.dart';

class ProjectDetailScreen extends StatefulWidget {
  final ProjectDto project;

  const ProjectDetailScreen({super.key, required this.project});

  @override
  State<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends State<ProjectDetailScreen> {
  final ProjectService _service = getIt<ProjectService>();
  final PreferenceService _prefs = getIt<PreferenceService>();
  late ProjectDto _currentProject;
  List<QuestionDto>? _questions;
  List<String>? _unansweredOrder;
  bool _loading = true;
  String _filter = 'Unanswered';

  @override
  void initState() {
    super.initState();
    _currentProject = widget.project;
    _loadQuestions();
  }

  List<QuestionDto> _getFilteredQuestions() {
    if (_questions == null) return [];

    var filtered = _questions!.where((q) {
      if (_filter == 'Unanswered') return q.isUnanswered;
      if (_filter == 'Answered') return q.isAnswered && !q.isIgnored;
      if (_filter == 'Ignored') return q.isIgnored;
      return true;
    }).toList();

    if (_filter == 'Unanswered' && _unansweredOrder != null) {
      filtered.sort((a, b) {
        final indexA = _unansweredOrder!.indexOf(a.id);
        final indexB = _unansweredOrder!.indexOf(b.id);
        if (indexA == -1) return 1;
        if (indexB == -1) return -1;
        return indexA.compareTo(indexB);
      });

      if (filtered.length > 3) {
        filtered = filtered.take(3).toList();
      }
    } else if (_filter == 'Answered') {
      filtered.sort((a, b) =>
          (b.currentAnswer?.modifiedAt ?? b.currentAnswer?.answeredAt ?? 0)
              .compareTo(
              a.currentAnswer?.modifiedAt ?? a.currentAnswer?.answeredAt ?? 0));
    } else if (_filter == 'Ignored') {
      filtered.sort((a, b) => (b.ignoredAt ?? 0).compareTo(a.ignoredAt ?? 0));
    }
    return filtered;
  }

  Future<void> _loadQuestions() async {
    setState(() => _loading = true);
    try {
      final project = await _service.getProject(widget.project.id);
      setState(() => _currentProject = project);
      final questions = project.questions;

      if (_filter == 'Unanswered') {
        final unanswered = questions.where((q) => q.isUnanswered).toList();

        if (unanswered.isNotEmpty) {
          final savedOrder = await _prefs.getQuestionOrder(widget.project.id);
          List<String> order = savedOrder;

          if (order.isEmpty) {
            order = unanswered.map((q) => q.id).toList()
              ..shuffle();
            await _prefs.saveQuestionOrder(widget.project.id, order);
          }

          _unansweredOrder = order;
        }
      }

      setState(() {
        _questions = questions;
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

  Future<void> _rotateUnanswered(List<String> currentVisibleIds) async {
    if (_unansweredOrder == null || _unansweredOrder!.isEmpty) return;

    final order = List<String>.from(_unansweredOrder!);
    final remaining = order
        .where((id) => !currentVisibleIds.contains(id))
        .toList();

    if (remaining.isEmpty) return;

    // Move current visible ones to the end
    final newOrder = [...remaining, ...currentVisibleIds];

    setState(() {
      _unansweredOrder = newOrder;
    });
    await _prefs.saveQuestionOrder(widget.project.id, newOrder);
  }

  Future<void> _askLater(QuestionDto question) async {
    if (_unansweredOrder == null) return;

    final order = List<String>.from(_unansweredOrder!);
    final id = question.id;
    if (!order.contains(id)) return;

    order.remove(id);
    order.add(id);

    setState(() {
      _unansweredOrder = order;
    });
    await _prefs.saveQuestionOrder(widget.project.id, order);
  }

  Future<void> _answerQuestion(QuestionDto question) async {
    final result = await showDialog<String>(
      context: context,
      builder: (context) =>
          AnswerDialog(
            project: widget.project,
            question: question,
            onSubmit: (answer) async {
              if (answer.isNotEmpty) {
                try {
                  await _service.updateAnswer(
                      widget.project.id, question.id, answer, false, false);
                } catch (e) {
                  debugPrint('Error updating answer: $e');
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('Error updating answer: $e'),
                        duration: const Duration(seconds: 10),
                        action: SnackBarAction(
                            label: 'Dismiss', onPressed: () {}),
                      ),
                    );
                  }
                }
              }
            },
          ),
    );

    if (result == 'submit' || result == 'deleted') {
      _loadQuestions();
    } else if (result == 'ask_later') {
      _askLater(question);
      _loadQuestions();
    }
  }

  Future<void> _updateSynopsis(String newSynopsis,
      ProjectUpdateMode mode) async {
    try {
      final updatedProject = await _service.updateProject(
        widget.project.id,
        newSynopsis,
        mode,
      );
      setState(() {
        _currentProject = updatedProject;
      });
      _loadQuestions();
    } catch (e) {
      debugPrint('Error updating synopsis: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error updating synopsis: $e')),
      );
    }
  }

  Future<void> _editSynopsis() async {
    await showDialog(
      context: context,
      builder: (context) =>
          EditProjectDialog(
            project: _currentProject,
            onSave: (synopsis, mode) => _updateSynopsis(synopsis, mode),
          ),
    );
  }

  Widget _buildFilterChips() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('Questions:', style: Theme
              .of(context)
              .textTheme
              .titleMedium),
          SizedBox(
            height: 30,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _buildFilterChip('Unanswered'),
                  const SizedBox(width: 8),
                  _buildFilterChip('Answered'),
                  const SizedBox(width: 8),
                  _buildFilterChip('Ignored'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String filter) {
    return ChoiceChip(
      label: Text(filter),
      selected: _filter == filter,
      onSelected: (selected) {
        setState(() => _filter = filter);
      },
    );
  }

  Widget _buildShuffleButton() {
    if (_filter != 'Unanswered') return const SizedBox.shrink();

    final unanswered = _questions?.where((q) => q.isUnanswered).toList() ?? [];
    if (unanswered.length <= 3) return const SizedBox.shrink();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          TextButton(
            onPressed: () {
              final currentOrder = List<String>.from(_unansweredOrder ?? []);
              final currentVisibleIds = currentOrder
                  .where((id) => unanswered.any((q) => q.id == id))
                  .take(3)
                  .toList();
              _rotateUnanswered(currentVisibleIds);
            },
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: const [
                Icon(Icons.shuffle, size: 16),
                SizedBox(width: 4),
                Text('Shuffle'),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuestionItem(QuestionDto question) {
    return QuestionItem(
      question: question,
      filter: _filter,
      onAskLater: () => _askLater(question),
      onIgnore: () => _ignoreQuestion(question),
      onUnignore: () => _unignoreQuestion(question),
      onDeleteAnswer: () async {
        final current = question.currentAnswer;
        if (current != null) {
          await _service.deleteAnswer(
              widget.project.id, question.id, current.id);
          _loadQuestions();
        }
      },
      onTap: () => _answerQuestion(question),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_currentProject.editableTitle),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit),
            onPressed: _editSynopsis,
          ),
        ],
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Synopsis:', style: Theme
                        .of(context)
                        .textTheme
                        .titleSmall),
                    IconButton(
                      icon: const Icon(Icons.edit, size: 20),
                      onPressed: _editSynopsis,
                    ),
                  ],
                ),
                Text(_currentProject.synopsis),
              ],
            ),
          ),
          const Divider(),
          _buildFilterChips(),
          _buildShuffleButton(),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : Builder(
              builder: (context) {
                final filteredQuestions = _getFilteredQuestions();

                if (filteredQuestions.isEmpty) {
                  return Center(
                    child: Text(
                      _filter == 'Unanswered'
                          ? 'No unanswered questions.'
                          : 'No ${_filter.toLowerCase()} questions.',
                      textAlign: TextAlign.center,
                    ),
                  );
                }

                return AnimatedSwitcher(
                  duration: const Duration(milliseconds: 300),
                  child: ListView.builder(
                    key: ValueKey(
                        _filter + (_unansweredOrder?.join(',') ?? '')),
                    itemCount: filteredQuestions.length,
                    itemBuilder: (context, index) =>
                        _buildQuestionItem(filteredQuestions[index]),
                  ),
                );
              },
            ),
          ),
        ],
      ),

    );
  }
}
