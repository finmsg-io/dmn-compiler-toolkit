package io.finmsg.dmn.runtime;

public final class DmnEvaluationException extends RuntimeException {
  public DmnEvaluationException(String message) { super(message); }
  public DmnEvaluationException(String message, Throwable cause) { super(message, cause); }
}
