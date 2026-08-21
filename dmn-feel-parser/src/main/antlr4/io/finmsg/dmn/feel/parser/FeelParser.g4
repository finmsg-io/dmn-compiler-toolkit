parser grammar FeelParser;

options {
    tokenVocab = FeelLexer;
}

expressionRoot
    : expression EOF
    ;

unaryTestsRoot
    : unaryTests EOF
    ;

textualExpressionsRoot
    : textualExpressions EOF
    ;

typeRoot
    : type EOF
    ;

expression
    : textualExpression
    ;

textualExpressions
    : textualExpression (COMMA textualExpression)*
    ;

textualExpression
    : negatedUnaryTests
    | forExpression
    | ifExpression
    | quantifiedExpression
    | disjunction
    ;

forExpression
    : FOR iterationContext (COMMA iterationContext)* RETURN expression
    ;

iterationContext
    : name IN expression (DOT_DOT expression)?
    ;

ifExpression
    : IF expression THEN expression ELSE expression
    ;

quantifiedExpression
    : (SOME | EVERY) iterationBinding (COMMA iterationBinding)* SATISFIES expression
    ;

iterationBinding
    : name IN expression
    ;

disjunction
    : conjunction (OR conjunction)*
    ;

conjunction
    : comparison (AND comparison)*
    ;

comparison
    : additiveExpression comparisonSuffix?
    ;

comparisonSuffix
    : comparisonOperator additiveExpression
    | BETWEEN expression AND expression
    | IN positiveUnaryTest
    | IN LPAREN positiveUnaryTests RPAREN
    ;

comparisonOperator
    : EQ
    | NE
    | LT
    | LE
    | GT
    | GE
    ;

additiveExpression
    : multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*
    ;

multiplicativeExpression
    : exponentiationExpression ((STAR | SLASH) exponentiationExpression)*
    ;

// FEEL: arithmetic negation binds more tightly than exponentiation.
// Therefore -4 ** 2 is parsed as (-4) ** 2.
exponentiationExpression
    : arithmeticNegation (POWER arithmeticNegation)*
    ;

arithmeticNegation
    : MINUS* instanceOfExpression
    ;

instanceOfExpression
    : postfixExpression (INSTANCE OF type)?
    ;

postfixExpression
    : primaryExpression postfixPart*
    ;

postfixPart
    : parameters
    | LBRACKET expression RBRACKET
    | DOT name
    | ELLIPSIS name
    ;

primaryExpression
    : literal
    | rangeLiteral
    | interval
    | list
    | context
    | functionDefinition
    | negatedUnaryTests
    | LPAREN expression RPAREN
    | name
    ;

parameters
    : LPAREN RPAREN
    | LPAREN namedParameters RPAREN
    | LPAREN positionalParameters RPAREN
    ;

namedParameters
    : namedParameter (COMMA namedParameter)*
    ;

namedParameter
    : parameterName COLON expression
    ;

positionalParameters
    : expression (COMMA expression)*
    ;

functionDefinition
    : FUNCTION LPAREN formalParameters? RPAREN EXTERNAL? expression
    ;

formalParameters
    : formalParameter (COMMA formalParameter)*
    ;

formalParameter
    : parameterName (COLON type)?
    ;

parameterName
    : qualifiedName
    ;

list
    : LBRACKET (expression (COMMA expression)*)? RBRACKET
    ;

context
    : LBRACE (contextEntry (COMMA contextEntry)*)? RBRACE
    ;

contextEntry
    : key COLON expression
    ;

key
    : keySegment+
    | STRING_LITERAL
    ;

keySegment
    : nameSegment
    | PLUS
    | MINUS
    | STAR
    | SLASH
    | DOT
    ;

unaryTests
    : negatedUnaryTests
    | MINUS
    | positiveUnaryTests
    ;

negatedUnaryTests
    : NOT LPAREN positiveUnaryTests RPAREN
    ;

positiveUnaryTests
    : positiveUnaryTest (COMMA positiveUnaryTest)*
    ;

positiveUnaryTest
    : simplePositiveUnaryTest
    | expression
    ;

simplePositiveUnaryTest
    : comparisonOperator endpoint
    | interval
    | rangeLiteral
    ;

// Rule 8: both endpoints are present.
interval
    : intervalStart endpoint DOT_DOT endpoint intervalEnd
    ;

intervalStart
    : openIntervalStart
    | closedIntervalStart
    ;

intervalEnd
    : openIntervalEnd
    | closedIntervalEnd
    ;

openIntervalStart
    : LPAREN
    | RBRACKET
    ;

closedIntervalStart
    : LBRACKET
    ;

openIntervalEnd
    : RPAREN
    | LBRACKET
    ;

closedIntervalEnd
    : RBRACKET
    ;

// Rule 66. The second and third alternatives represent an omitted
// lower or upper endpoint respectively.
rangeLiteral
    : intervalStart endpoint DOT_DOT endpoint intervalEnd
    | intervalStart DOT_DOT endpoint intervalEnd
    | intervalStart endpoint DOT_DOT openIntervalEnd
    | LPAREN comparisonOperator endpoint RPAREN
    ;

endpoint
    : expression
    ;

literal
    : NUMBER_LITERAL
    | STRING_LITERAL
    | TRUE
    | FALSE
    | NULL
    | atLiteral
    ;

atLiteral
    : AT STRING_LITERAL
    ;

name
    : nameSegment+
    ;

nameSegment
    : IDENTIFIER
    | NUMBER_LITERAL
    | LIST
    | RANGE
    | CONTEXT
    | OF
    | DATE_AND_TIME
    | YEARS_AND_MONTHS_DURATION
    | DAY_AND_TIME_DURATION
    | DAYS_AND_TIME_DURATION
    ;


qualifiedName
    : name (DOT name)*
    ;

type
    : qualifiedName
    | RANGE LT type GT
    | LIST LT type GT
    | CONTEXT LT contextTypeEntry (COMMA contextTypeEntry)* GT
    | FUNCTION LT typeList? GT ARROW type
    ;

contextTypeEntry
    : name COLON type
    ;

typeList
    : type (COMMA type)*
    ;
