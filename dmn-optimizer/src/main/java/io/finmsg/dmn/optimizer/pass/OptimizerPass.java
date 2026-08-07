package io.finmsg.dmn.optimizer.pass;

import io.finmsg.dmn.ir.RuntimeModel;

/** Interface for single optimization passes operating over RuntimeModel IR. */
public interface OptimizerPass {
  RuntimeModel transform(RuntimeModel model);
}
