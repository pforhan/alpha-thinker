import 'package:flutter/material.dart';

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
