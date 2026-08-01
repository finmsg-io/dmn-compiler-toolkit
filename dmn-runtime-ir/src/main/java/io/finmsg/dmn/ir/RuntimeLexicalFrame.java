package io.finmsg.dmn.ir;

import java.util.HashMap;
import java.util.Map;

/** Lexical-scope operations shared by boxed and FEEL expression lowering. */
final class RuntimeLexicalFrame {
  private RuntimeLexicalFrame() {
  }

  static Map<String, LocalSlotAddress> childScope(Map<String, LocalSlotAddress> addresses) {
    return new HashMap<>(addresses);
  }

  static Map<String, LocalSlotAddress> capturedScope(Map<String, LocalSlotAddress> addresses) {
    Map<String, LocalSlotAddress> captured = new HashMap<>();
    addresses.forEach((path, address) -> captured.put(path,
        new LocalSlotAddress(address.lexicalDepth() + 1, address.localSlot())));
    return captured;
  }

  static int bind(
      Map<String, LocalSlotAddress> addresses, int[] nextSlot, String declarationPath) {
    int slot = nextSlot[0]++;
    addresses.put(declarationPath, new LocalSlotAddress(0, slot));
    return slot;
  }
}
