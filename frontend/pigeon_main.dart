import 'package:pigeon_options.dart';

void main() {
  runPigeon(options: PigeonOptions(
    pigeonOptionsSource: 'pigeons/messages.dart',
    pigeonOutOutDir: 'lib/src/pigeon_generated',
    pigeonDartOutDir: 'lib/src/pigeon_generated',
    pigeonKotlinOutDir: '../shared/src/commonMain/kotlin/com/pforhan/alphathinker/pigeon',
  ),
  ),
}
