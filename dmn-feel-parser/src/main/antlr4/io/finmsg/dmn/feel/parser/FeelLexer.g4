lexer grammar FeelLexer;

BLOCK_COMMENT : '/*' .*? '*/' -> skip ;
LINE_COMMENT  : '//' ~[\r\n]* -> skip ;
WS            : WhiteSpace+ -> channel(HIDDEN) ;

// Longest operators first.
ELLIPSIS : '...' ;
POWER    : '**' ;
LE       : '<=' ;
GE       : '>=' ;
NE       : '!=' ;
DOT_DOT  : '..' ;
ARROW    : '->' ;

EQ       : '=' ;
LT       : '<' ;
GT       : '>' ;
PLUS     : '+' ;
MINUS    : '-' ;
STAR     : '*' ;
SLASH    : '/' ;
DOT      : '.' ;
COMMA    : ',' ;
COLON    : ':' ;
LPAREN   : '(' ;
RPAREN   : ')' ;
LBRACKET : '[' ;
RBRACKET : ']' ;
LBRACE   : '{' ;
RBRACE   : '}' ;
AT       : '@' ;

DATE_AND_TIME : 'date' WhiteSpace+ 'and' WhiteSpace+ 'time' ;
YEARS_AND_MONTHS_DURATION : 'years' WhiteSpace+ 'and' WhiteSpace+ 'months' WhiteSpace+ 'duration' ;
DAY_AND_TIME_DURATION : 'day' WhiteSpace+ 'and' WhiteSpace+ 'time' WhiteSpace+ 'duration' ;
DAYS_AND_TIME_DURATION : 'days' WhiteSpace+ 'and' WhiteSpace+ 'time' WhiteSpace+ 'duration' ;

FOR       : 'for' ;
IN        : 'in' ;
RETURN    : 'return' ;
IF        : 'if' ;
THEN      : 'then' ;
ELSE      : 'else' ;
SOME      : 'some' ;
EVERY     : 'every' ;
SATISFIES : 'satisfies' ;
AND       : 'and' ;
OR        : 'or' ;
BETWEEN   : 'between' ;
INSTANCE  : 'instance' ;
OF        : 'of' ;
NOT       : 'not' ;
FUNCTION  : 'function' ;
EXTERNAL  : 'external' ;
TRUE      : 'true' ;
FALSE     : 'false' ;
NULL      : 'null' ;
RANGE     : 'range' ;
LIST      : 'list' ;
CONTEXT   : 'context' ;

STRING_LITERAL
    : '"' (StringEscapeSequence | ~["\r\n\\])* '"'
    | '\'' (SingleQuoteEscapeSequence | ~['\r\n\\])* '\''
    ;

NUMBER_LITERAL
    : (Digits ('.' Digits)? | '.' Digits) ExponentPart?
    ;

IDENTIFIER
    : NameStartChar NamePartChar*
    ;

fragment ExponentPart
    : [eE] [+-]? Digits
    ;

fragment Digits
    : [0-9]+
    ;

fragment StringEscapeSequence
    : '\\u' HexDigit HexDigit HexDigit HexDigit
    | '\\U' HexDigit HexDigit HexDigit HexDigit HexDigit HexDigit
    | '\\' .
    ;

fragment SingleQuoteEscapeSequence
    : '\\u' HexDigit HexDigit HexDigit HexDigit
    | '\\U' HexDigit HexDigit HexDigit HexDigit HexDigit HexDigit
    | '\\' .
    ;

fragment HexDigit
    : [0-9a-fA-F]
    ;

fragment NameStartChar
    : '?'
    | [A-Z_a-z]
    | [\u00C0-\u00D6]
    | [\u00D8-\u00F6]
    | [\u00F8-\u02FF]
    | [\u0370-\u037D]
    | [\u037F-\u1FFF]
    | [\u200C-\u200D]
    | [\u2070-\u218F]
    | [\u2C00-\u2FEF]
    | [\u3001-\uD7FF]
    | [\uF900-\uFDCF]
    | [\uFDF0-\uFFFD]
    | [\u{10000}-\u{EFFFF}]
    ;

fragment NamePartChar
    : NameStartChar
    | [0-9]
    | '\''
    | '\u00B7'
    | [\u0300-\u036F]
    | [\u203F-\u2040]
    ;

fragment WhiteSpace
    : [\u0009\u000A-\u000D\u0020\u0085\u00A0\u1680\u180E\u2000-\u200B\u2028\u2029\u202F\u205F\u3000\uFEFF]
    ;
