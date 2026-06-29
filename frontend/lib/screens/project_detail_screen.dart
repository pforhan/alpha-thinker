import 'package:flutter/material.dart';
import '../injection.dart';
import '../thinker_api.dart';
import '../thinker_api_extensions.dart';
import '../services/project_service.dart';
import '../services/preference_service.dart';
import 'dart:math';

class ProjectDetailScreen extends StatefulWidget {
  final ProjectDto project;
  const ProjectDetailScreen({super.key, required this.project});

  @override
  State<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends State<ProjectDetailScreen> {
  final ProjectService _service = getIt<ProjectService>();
  final PreferenceService _prefs = getIt<PreferenceService>();
  List<QuestionDto>? _questions;
  List<String>? _unansweredOrder;
  bool _loading = true;
  String _filter = 'Unanswered';

  @override
  void initState() {
    super.initState();
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
       filtered.sort((a, b) => (b.currentAnswer?.modifiedAt ?? b.currentAnswer?.answeredAt ?? 0).compareTo(a.currentAnswer?.modifiedAt ?? a.currentAnswer?.answeredAt ?? 0));
     } else if (_filter == 'Ignored') {
       filtered.sort((a, b) => (b.ignoredAt ?? 0).compareTo(a.ignoredAt ?? 0));
     }
     return filtered;
   }

  Future<void> _loadQuestions() async {
    setState(() => _loading = true);
    try {
      final project = await _service.getProject(widget.project.id);
      final questions = project.questions;
      
      if (_filter == 'Unanswered') {
        final unanswered = questions.where((q) => q.isUnanswered).toList();

        if (unanswered.isNotEmpty) {
          final savedOrder = await _prefs.getQuestionOrder(widget.project.id);
          List<String> order = savedOrder;
          
          if (order.isEmpty) {
            order = unanswered.map((q) => q.id).toList()..shuffle();
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
    final remaining = order.where((id) => !currentVisibleIds.contains(id)).toList();
    
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
           if (current == null || !current.isAnswered)
             TextButton(
               onPressed: () async {
                 if (answerController.text.isNotEmpty) {
                   try {
                     await _service.updateAnswer(widget.project.id, question.id, answerController.text, false, true);
                     _loadQuestions();
                   } catch (e) {
                     debugPrint('Error saving draft: $e');
                   }
                 }
                 _askLater(question);
                 Navigator.pop(context, false);
               },
               child: const Text('Ask Later'),
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
        await _service.updateAnswer(widget.project.id, question.id, answerController.text, false, false);
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

  Widget _buildFilterChips() {
    return Padding(
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
    final isIgnored = question.isIgnored;
    final hasAnswer = question.isAnswered;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ListTile(
        title: Text(question.text),
        subtitle: hasAnswer 
          ? Text(question.currentAnswer!.text, maxLines: 1, overflow: TextOverflow.ellipsis)
          : (isIgnored ? const Text('Ignored') : null),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (!hasAnswer && !isIgnored && _questions?.where((q) => q.isUnanswered).length != null && _questions!.where((q) => q.isUnanswered).length > 3)
              Tooltip(
                message: 'Ask later',
                child: IconButton(
                  icon: const Icon(Icons.rotate_left),
                  onPressed: () => _askLater(question),
                ),
              ),
            Tooltip(
              message: isIgnored ? 'Show question' : 'Ignore question',
              child: IconButton(
                icon: Icon(isIgnored ? Icons.visibility : Icons.visibility_off),
                onPressed: () => isIgnored 
                  ? _unignoreQuestion(question) 
                  : _ignoreQuestion(question),
              ),
            ),
          ],
        ),
        onTap: () => _answerQuestion(question),
      ),
    );
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

                       return ListView.builder(
                         itemCount: filteredQuestions.length,
                         itemBuilder: (context, index) => _buildQuestionItem(filteredQuestions[index]),
                       );
                     },
                   ),
            ),
        ],
      ),

    );
  }
}
