import 'package:flutter/foundation.dart';
import 'package:get_it/get_it.dart';
import 'services/project_service.dart';
import 'services/in_memory_project_service.dart';
import 'services/manual_project_service.dart';
import 'services/preference_service.dart';

final GetIt getIt = GetIt.instance;

void setupDependencyInjection() {
  getIt.registerLazySingleton<PreferenceService>(() => PreferenceService());
  if (kIsWeb) {
    getIt.registerLazySingleton<ProjectService>(() => InMemoryProjectService());
  } else {
    getIt.registerLazySingleton<ProjectService>(() => ManualProjectService());
  }
}
