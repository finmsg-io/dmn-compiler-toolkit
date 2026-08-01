package io.finmsg.dmn.semantic.analysis;

import io.finmsg.dmn.model.Definitions;

/** A stateless semantic-analysis stage that derives a result from an immutable DMN model. */
@FunctionalInterface
public interface DmnSemanticPass<R> {

  R analyze(Definitions model);
}
