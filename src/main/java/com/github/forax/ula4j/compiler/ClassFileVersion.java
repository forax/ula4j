package com.github.forax.ula4j.compiler;

import java.io.IOException;

final class ClassFileVersion {
  static final int CLASS_FILE_VERSION;
  static {
    var runtimeVersion = Runtime.version().feature();
    var valhallaEnabled = false;
    if (runtimeVersion >= 28) {
      try (var input = Long.class.getResourceAsStream("/java/lang/Long.class")) {
        if (input == null) {
          throw new AssertionError("Long.class not found");
        }
        var header = input.readNBytes(6);
        valhallaEnabled = header[4] == (byte) 0xFF && header[5] == (byte) 0xFF;
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    // Use the runtime version, add preview (Valhalla) if enabled
    CLASS_FILE_VERSION = (valhallaEnabled ? 0xFFFF : 0 ) << 16 | (runtimeVersion - 25 + 69);
  }
}
