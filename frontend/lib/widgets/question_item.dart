import 'package:flutter/material.dart';
import '../thinker_api.dart';
import '../thinker_api_extensions.dart';

class SwipeableItem extends StatefulWidget {
  final String id;
  final Widget child;
  final Widget? background;
  final Widget? secondaryBackground;
  final Function(DismissDirection direction)? onDismissed;
  final Color? backgroundColor;
  final Color? secondaryBackgroundColor;

  const SwipeableItem({
    super.key,
    required this.id,
    required this.child,
    this.background,
    this.secondaryBackground,
    this.onDismissed,
    this.backgroundColor,
    this.secondaryBackgroundColor,
  });

  @override
  SwipeableItemState createState() => SwipeableItemState();
}

class SwipeableItemState extends State<SwipeableItem> {
  @override
  Widget build(BuildContext context) {
    return Dismissible(
      key: Key(widget.id),
      background: widget.background ?? (widget.backgroundColor != null 
        ? Container(color: widget.backgroundColor!, alignment: Alignment.centerLeft) 
        : const SizedBox.shrink()),
      secondaryBackground: widget.secondaryBackground ?? (widget.secondaryBackgroundColor != null 
        ? Container(color: widget.secondaryBackgroundColor!, alignment: Alignment.centerRight) 
        : const SizedBox.shrink()),
      onDismissed: widget.onDismissed,
      child: widget.child,
    );
  }
}

class QuestionItem extends StatelessWidget {
  final QuestionDto question;
  final String filter;
  final VoidCallback onAskLater;
  final VoidCallback onIgnore;
  final VoidCallback onUnignore;
  final VoidCallback onDeleteAnswer;
  final VoidCallback onTap;

  const QuestionItem({
    super.key,
    required this.question,
    required this.filter,
    required this.onAskLater,
    required this.onIgnore,
    required this.onUnignore,
    required this.onDeleteAnswer,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isIgnored = question.isIgnored;
    final hasAnswer = question.isAnswered;

    Widget? background;
    Widget? secondaryBackground;
    Function(DismissDirection direction)? onDismissed;

    if (filter == 'Unanswered') {
      background = Container(
        color: Colors.blue,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Row(
          children: [
            Icon(Icons.rotate_left, color: Colors.white),
            SizedBox(width: 8),
            Text('Ask Later', style: TextStyle(color: Colors.white)),
          ],
        ),
      );
      secondaryBackground = Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text('Ignore', style: TextStyle(color: Colors.white)),
            SizedBox(width: 8),
            Icon(Icons.visibility_off, color: Colors.white),
          ],
        ),
      );
      onDismissed = (direction) {
        if (direction == DismissDirection.startToEnd) {
          onAskLater();
        } else if (direction == DismissDirection.endToStart) {
          onIgnore();
        }
      };
    } else if (filter == 'Answered') {
      background = Container(
        color: Colors.grey,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Row(
          children: [
            Icon(Icons.visibility_off, color: Colors.white),
            SizedBox(width: 8),
            Text('Ignore', style: TextStyle(color: Colors.white)),
          ],
        ),
      );
      secondaryBackground = Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text('Delete Answer', style: TextStyle(color: Colors.white)),
            SizedBox(width: 8),
            Icon(Icons.delete, color: Colors.white),
          ],
        ),
      );
      onDismissed = (direction) {
        if (direction == DismissDirection.startToEnd) {
          onIgnore();
        } else if (direction == DismissDirection.endToStart) {
          onDeleteAnswer();
        }
      };
    } else if (filter == 'Ignored') {
      background = Container(
        color: Colors.green,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Row(
          children: [
            Icon(Icons.visibility, color: Colors.white),
            SizedBox(width: 8),
            Text('Unignore', style: TextStyle(color: Colors.white)),
          ],
        ),
      );
      secondaryBackground = Container(
        color: Colors.green,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text('Unignore', style: TextStyle(color: Colors.white)),
            SizedBox(width: 8),
            Icon(Icons.visibility, color: Colors.white),
          ],
        ),
      );
      onDismissed = (direction) => onUnignore();
    }

    return SwipeableItem(
      id: question.id,
      background: background,
      secondaryBackground: secondaryBackground,
      onDismissed: onDismissed,
      child: Card(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: ListTile(
          title: Text(question.text),
          subtitle: hasAnswer 
            ? Text(question.currentAnswer!.text, maxLines: 1, overflow: TextOverflow.ellipsis)
            : (isIgnored ? const Text('Ignored') : null),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (filter == 'Unanswered' && !hasAnswer && !isIgnored)
                Tooltip(
                  message: 'Ask later',
                  child: IconButton(
                    icon: const Icon(Icons.rotate_left),
                    onPressed: onAskLater,
                  ),
                ),
              Tooltip(
                message: isIgnored ? 'Show question' : 'Ignore question',
                child: IconButton(
                  icon: Icon(isIgnored ? Icons.visibility : Icons.visibility_off),
                  onPressed: isIgnored ? onUnignore : onIgnore,
                ),
              ),
            ],
          ),
          onTap: onTap,
        ),
      ),
    );
  }
}

