package io.finmsg.dmn.frontend.xml.dmn.reader;

/**
 * Shared registry for the mutually recursive FEEL text readers.
 *
 * <p>The readers are stateless. A single immutable registry can therefore be
 * reused safely across parses and threads.</p>
 */
public final class ReaderRegistry {

  private static final ReaderRegistry SHARED = new ReaderRegistry();

  private final FeelReader feelReader;
  private final ExpressionNodeReader expressionNodeReader;
  private final BoxedExpressionReader boxedExpressionReader;
  private final ContextTextReader contextTextReader;
  private final ContextEntryTextReader contextEntryTextReader;
  private final RelationTextReader relationTextReader;
  private final RelationRowTextReader relationRowTextReader;
  private final ListExpressionTextReader listExpressionTextReader;
  private final FunctionDefinitionTextReader functionDefinitionTextReader;

  private ReaderRegistry() {
    feelReader = new FeelReader();
    expressionNodeReader = new ExpressionNodeReader(this);
    boxedExpressionReader = new BoxedExpressionReader(this);
    contextTextReader = new ContextTextReader(this);
    contextEntryTextReader = new ContextEntryTextReader(this);
    relationTextReader = new RelationTextReader(this);
    relationRowTextReader = new RelationRowTextReader(this);
    listExpressionTextReader = new ListExpressionTextReader(this);
    functionDefinitionTextReader = new FunctionDefinitionTextReader(this);
  }

  public static ReaderRegistry shared() {
    return SHARED;
  }

  public FeelReader feelReader() {
    return feelReader;
  }

  public ExpressionNodeReader expressionNodeReader() {
    return expressionNodeReader;
  }

  public BoxedExpressionReader boxedExpressionReader() {
    return boxedExpressionReader;
  }

  public ContextTextReader contextTextReader() {
    return contextTextReader;
  }

  public ContextEntryTextReader contextEntryTextReader() {
    return contextEntryTextReader;
  }

  public RelationTextReader relationTextReader() {
    return relationTextReader;
  }

  public RelationRowTextReader relationRowTextReader() {
    return relationRowTextReader;
  }

  public ListExpressionTextReader listExpressionTextReader() {
    return listExpressionTextReader;
  }

  public FunctionDefinitionTextReader functionDefinitionTextReader() {
    return functionDefinitionTextReader;
  }
}
