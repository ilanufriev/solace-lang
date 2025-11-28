grammar Solace;

// -------------------------
// 1. Топ-уровень
// -------------------------

program
    : (nodeDecl | networkDecl)* EOF
    ;

// node Name : hardware/software ( in: a; out: b,c; self: s; ) { init { ... } run { ... } }
nodeDecl
    : NODE ID ':' nodeKind
      LPAREN channelSignature? RPAREN
      LBRACE
        initBlock
        runBlock
      RBRACE
    ;

nodeKind
    : HARDWARE
    | SOFTWARE
    ;

// in/out/self — сигнатура FIFO-каналов узла
channelSignature
    : channelClause (SEMI channelClause)* SEMI?
    ;

channelClause
    : IN   COLON idList
    | OUT  COLON idList
    | SELF COLON idList
    ;

idList
    : ID (COMMA ID)*
    ;

// init / run как setup / loop
initBlock
    : INIT block
    ;

runBlock
    : RUN block
    ;

block
    : LBRACE statement* RBRACE
    ;

// -------------------------
// 2. Операторы
// -------------------------

statement
    : varDeclStmt
    | assignStmt
    | fifoWriteStmt
    | printStmt
    | ifStmt
    | exprStmt
    | SEMI                // пустой оператор
    ;

// int x = 0; / string s = "hi";
varDeclStmt
    : type ID (ASSIGN expr)? SEMI
    ;

type
    : INT_TYPE
    | STRING_TYPE
    ;

// x = expr;
assignStmt
    : ID ASSIGN expr SEMI
    ;

// fifoName <- expr;
fifoWriteStmt
    : ID WRITE_ARROW expr SEMI
    ;

// print(expr);
printStmt
    : PRINT LPAREN expr RPAREN SEMI
    ;

// if (cond) { ... } else { ... }
ifStmt
    : IF LPAREN expr RPAREN block (ELSE block)?
    ;

// "голое" выражение как оператор
exprStmt
    : expr SEMI
    ;

// -------------------------
// 3. Выражения
// -------------------------
//
// приоритеты (от низкого к высокому):
//  ||
//  &&
//  == !=
//  < <= > >=
//  << >>
//  + -
//  * /
//  унарные (!, -, $fifo[?])
//

expr
    // --- базовые и унарные ---
    : primary                      # PrimaryExpr
    | NOT expr                     # NotExpr
    | MINUS expr                   # NegExpr
    | DOLLAR ID QUESTION?          # FifoReadExpr

    // --- * / ---
    | expr STAR expr               # MulExpr
    | expr SLASH expr              # DivExpr

    // --- + - ---
    | expr PLUS expr               # AddExpr
    | expr MINUS expr              # SubExpr

    // --- << >> ---
    | expr SHIFT_LEFT expr         # ShiftLeftExpr
    | expr SHIFT_RIGHT expr        # ShiftRightExpr

    // --- < <= > >= ---
    | expr LT expr                 # LtExpr
    | expr LE expr                 # LeExpr
    | expr GT expr                 # GtExpr
    | expr GE expr                 # GeExpr

    // --- == != ---
    | expr EQ expr                 # EqExpr
    | expr NEQ expr                # NeqExpr

    // --- && ---
    | expr AND expr                # AndExpr

    // --- || ---
    | expr OR expr                 # OrExpr
    ;

// атомы
primary
    : INT_LITERAL
    | STRING_LITERAL
    | ID
    | LPAREN expr RPAREN
    ;

// -------------------------
// 4. Network (глобальная, без имени)
// -------------------------
//
// network {
//   A.out1 -> B.in0;
//   B.out0 -> C.in0;
// }

networkDecl
    : NETWORK LBRACE connection* RBRACE
    ;

connection
    : endpoint ARROW endpoint SEMI
    ;

// endpoint вида: NodeName.channelName
endpoint
    : ID DOT ID
    ;

// -------------------------
// 5. Лексер: ключевые слова
// -------------------------

NODE        : 'node';
HARDWARE    : 'hardware';
SOFTWARE    : 'software';
INIT        : 'init';
RUN         : 'run';
NETWORK     : 'network';
IN          : 'in';
OUT         : 'out';
SELF        : 'self';
INT_TYPE    : 'int';
STRING_TYPE : 'string';
PRINT       : 'print';
IF          : 'if';
ELSE        : 'else';

// -------------------------
// 6. Лексер: операторы и знаки
// -------------------------

PLUS        : '+';
MINUS       : '-';
STAR        : '*';
SLASH       : '/';
NOT         : '!';
AND         : '&&';
OR          : '||';

SHIFT_LEFT  : '<<';
SHIFT_RIGHT : '>>';

WRITE_ARROW : '<-';

LE          : '<=';
GE          : '>=';
EQ          : '==';
NEQ         : '!=';

LT          : '<';
GT          : '>';

ASSIGN      : '=';
ARROW       : '->';

LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
SEMI        : ';';
COMMA       : ',';
COLON       : ':';
DOT         : '.';

DOLLAR      : '$';
QUESTION    : '?';

// -------------------------
// 7. Лексер: литералы / идентификаторы
// -------------------------

INT_LITERAL
    : [0-9]+
    ;

STRING_LITERAL
    : '"' (~["\\] | '\\' .)* '"'
    ;

ID
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;

// -------------------------
// 8. Пробелы и комментарии
// -------------------------

WS
    : [ \t\r\n]+ -> skip
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;
