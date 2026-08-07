package io.finmsg.dmn.optimizer;

import io.finmsg.dmn.ir.RuntimeModel;
import io.finmsg.dmn.optimizer.pass.AlgebraicSimplificationPass;
import io.finmsg.dmn.optimizer.pass.ConstantFoldingPass;
import io.finmsg.dmn.optimizer.pass.DecisionTableOptimizationPass;
import java.util.Objects;

/** Main facade executing optimization pass pipelines on RuntimeModel IR. */
public class DmnOptimizer {

  private final DmnOptimizerOptions options;

  public DmnOptimizer() {
    this(DmnOptimizerOptions.defaultOptions());
  }

  public DmnOptimizer(DmnOptimizerOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  public RuntimeModel optimize(RuntimeModel model) {
    Objects.requireNonNull(model, "model");

    RuntimeModel current = model;

    if (options.enableConstantFolding()) {
      current = new ConstantFoldingPass().transform(current);
    }

    if (options.enableAlgebraicSimplification()) {
      current = new AlgebraicSimplificationPass().transform(current);
    }

    if (options.enableDecisionTableOptimization()) {
      current = new DecisionTableOptimizationPass().transform(current);
    }

    return current;
  }
}
