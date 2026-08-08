package io.finmsg.dmn.ir;

/**
 * Lowering-time lexical address shared by boxed and FEEL expression lowering.
 */
record LocalSlotAddress(int lexicalDepth, int localSlot) {
}
