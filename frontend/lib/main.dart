import 'package:flutter/material.dart';
import 'injection.dart';
import 'screens/project_list_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  setupDependencyInjection();
  runApp(const AlphaThinkerApp());
}

class AlphaThinkerApp extends StatelessWidget {
  const AlphaThinkerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Alpha Thinker',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const ProjectListScreen(),
    );
  }
}
