grammar DecisionDsl;

/*
 * ANTLR4 grammar for the FinMsg .decision authoring DSL.
 *
 * Designed for dual use:
 *  1. Direct compilation into DMN Definitions protobuf via dmn-frontend-dsl
 *     (embedded FEEL expressions/unary-tests are parsed by dmn-feel-parser).
 *  2. IDE plugins (IntelliJ via antlr4-intellij-adaptor, VS Code via ANTLR4 TS / LSP),
 *     providing full syntax tokens for keywords, strings, numbers, comments, and brackets.
 */

decisionFile
    : NEWLINE* statement* EOF
    ;

statement
    : ( modelDecl
      | namespaceDecl
      | idDecl
      | importDecl
      | typeDecl
      | inputDecl
      | bkmDecl
      | decisionDecl
      | testDecl
      ) NEWLINE*
    ;

modelDecl
    : MODEL nameExpr
    ;

namespaceDecl
    : NAMESPACE STRING
    ;

idDecl
    : ID STRING
    ;

importDecl
    : IMPORT nameExpr FROM STRING NAMESPACE STRING
    ;

typeDecl
    : TYPE nameExpr (ID STRING)?
      ( recordTypeBody
      | collectionTypeBody
      | aliasTypeBody
      )
    ;

recordTypeBody
    : LBRACE NEWLINE* (recordField NEWLINE*)* RBRACE
    ;

recordField
    : nameExpr COLON typeRef (CONSTRAINT constraintExpr)?
    ;

collectionTypeBody
    : LIST LT typeRef GT
    ;

aliasTypeBody
    : EQ typeRef (CONSTRAINT constraintExpr)?
    ;

inputDecl
    : INPUT nameExpr (ID STRING)? COLON typeRef
    ;

bkmDecl
    : BKM nameExpr (ID STRING)? LPAREN paramList? RPAREN COLON typeRef NEWLINE*
      (usesClause NEWLINE*)?
      EQ NEWLINE* feelBody
    ;

paramList
    : param (COMMA param)*
    ;

param
    : nameExpr COLON typeRef
    ;

decisionDecl
    : DECISION nameExpr (ID STRING)? COLON typeRef NEWLINE*
      decisionClause*
      ( EQ NEWLINE* feelBody
      | tableBlock
      )
    ;

decisionClause
    : (requiresClause | usesClause) NEWLINE*
    ;

requiresClause
    : REQUIRES refList
    ;

usesClause
    : USES refList
    ;

refList
    : elementRef (COMMA elementRef)*
    ;

elementRef
    : (alias=IDENT DOT)? target=nameExpr (HASH explicitId=IDENT)?
    ;

tableBlock
    : TABLE hitPolicy LBRACE NEWLINE*
      tableHeader NEWLINE+
      (tableRule NEWLINE*)+
      RBRACE
    ;

hitPolicy
    : IDENT+
    ;

tableHeader
    : WHEN tableInputCol (PIPE tableInputCol)* ARROW tableOutputCol (PIPE tableOutputCol)*
    ;

tableInputCol
    : expr=tableInputExpr (COLON typeRef)?
    ;

tableOutputCol
    : (name=nameExpr COLON)? type=typeRef (DEFAULT defExpr=tableOutputDefaultExpr)?
    ;

tableRule
    : PIPE ruleCell (PIPE ruleCell)* PIPE? ARROW ruleCell (PIPE ruleCell)* PIPE?
    ;

testDecl
    : TEST STRING LBRACE NEWLINE*
      (testEntry NEWLINE*)*
      RBRACE
    ;

testEntry
    : kind=(GIVEN | EXPECT) nameExpr EQ testExpr
    ;

nameExpr
    : STRING
    | identOrKeyword
    ;

typeRef
    : (IDENT DOT)? IDENT (IDENT)*
    ;

// FEEL expression islands: tokenized cleanly for IDE highlighting while
// preserving exact source text via TokenStream.getText(start, stop).

feelBody
    : feelLine (NEWLINE+ feelLine)*
    ;

feelLine
    : feelLineStart feelToken*
    ;

feelLineStart
    : STRING
    | NUMBER
    | IDENT
    | LPAREN | RPAREN | LBRACK | RBRACK | LBRACE | RBRACE
    | DOT | RANGE_DOTS | COMMA | COLON | HASH | AT
    | ARROW | EQ | NEQ | LTE | GTE | LT | GT
    | PLUS | MINUS | STAR | SLASH | POWER | PIPE
    | FROM | LIST | CONSTRAINT | REQUIRES | USES | TABLE | WHEN | DEFAULT | GIVEN | EXPECT
    ;

feelToken
    : feelLineStart
    | MODEL | NAMESPACE | ID | IMPORT | TYPE | INPUT | BKM | DECISION | TEST
    ;

constraintExpr
    : lineExprToken+
    ;

testExpr
    : lineExprToken+
    ;

lineExprToken
    : feelToken
    ;

tableInputExpr
    : tableHeaderToken+
    ;

tableOutputDefaultExpr
    : tableDefaultToken+
    ;

tableHeaderToken
    : STRING | NUMBER | IDENT
    | LPAREN | RPAREN | LBRACK | RBRACK | LBRACE | RBRACE
    | DOT | RANGE_DOTS | COMMA | HASH | AT
    | EQ | NEQ | LTE | GTE | LT | GT
    | PLUS | MINUS | STAR | SLASH | POWER
    | MODEL | NAMESPACE | ID | IMPORT | FROM | TYPE | LIST | CONSTRAINT
    | INPUT | BKM | DECISION | REQUIRES | USES | TABLE | WHEN | DEFAULT | TEST | GIVEN | EXPECT
    ;

tableDefaultToken
    : tableHeaderToken
    | COLON
    ;

ruleCell
    : ruleCellToken+
    ;

ruleCellToken
    : tableHeaderToken
    | COLON
    ;

identOrKeyword
    : IDENT
    | MODEL | NAMESPACE | ID | IMPORT | FROM | TYPE | LIST | CONSTRAINT
    | INPUT | BKM | DECISION | REQUIRES | USES | TABLE | WHEN | DEFAULT | TEST | GIVEN | EXPECT
    ;

// ---------------------------------------------------------------------------
// Lexer rules
// ---------------------------------------------------------------------------

MODEL       : 'model' ;
NAMESPACE   : 'namespace' ;
ID          : 'id' ;
IMPORT      : 'import' ;
FROM        : 'from' ;
TYPE        : 'type' ;
LIST        : 'list' ;
CONSTRAINT  : 'constraint' ;
INPUT       : 'input' ;
BKM         : 'bkm' ;
DECISION    : 'decision' ;
REQUIRES    : 'requires' ;
USES        : 'uses' ;
TABLE       : 'table' ;
WHEN        : 'when' ;
DEFAULT     : 'default' ;
TEST        : 'test' ;
GIVEN       : 'given' ;
EXPECT      : 'expect' ;

ARROW       : '=>' ;
NEQ         : '!=' ;
LTE         : '<=' ;
GTE         : '>=' ;
POWER       : '**' ;
RANGE_DOTS  : '..' ;

EQ          : '=' ;
LT          : '<' ;
GT          : '>' ;
PIPE        : '|' ;
LBRACE      : '{' ;
RBRACE      : '}' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACK      : '[' ;
RBRACK      : ']' ;
COLON       : ':' ;
COMMA       : ',' ;
DOT         : '.' ;
HASH        : '#' ;
AT          : '@' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;

STRING
    : '"' ( '\\' . | ~["\\\r\n] )* '"'
    ;

NUMBER
    : [0-9]+ ( '.' [0-9]+ )?
    ;

IDENT
    : [A-Za-z_] [A-Za-z0-9_]*
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

NEWLINE
    : ( '\r'? '\n' )+
    ;

WS
    : [ \t]+ -> channel(HIDDEN)
    ;
