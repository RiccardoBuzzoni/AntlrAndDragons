grammar BagOfGrammar;

// ============================================================
// ANTLR & DRAGONS - Grammar
// A typed imperative DSL for RPG scenario simulation
// ============================================================

// TOP-LEVEL PROGRAM STRUCTURE
//      program: optional function declarations section, followed by the executable main block.

program : creatureSection? spellbookSection? questBlock EOF ;

// Class declarations live in a dedicated, non-executable section.

creatureSection : CREATURES ':' creatureDecl+ ;

// Function declarations live in a dedicated, non-executable section.

spellbookSection : SPELLBOOK ':' spellDecl+ ;

questBlock : QUEST ':' block ;

// ----------------------------------------
//  CLASSES (advanced feature: Classi e Oggetti)
// ----------------------------------------

creatureDecl : CREATURE ID '{' creatureMember* '}' ;

creatureMember : visibility type ID ';'                        # ClassField
               | visibility type ID '(' paramList? ')' block   # ClassMethodReturn
               | visibility VOID ID '(' paramList? ')' block   # ClassMethodVoid
               ;

visibility : KNOWN
           | UNSEEN
           ;

// ----------------------------------------
//  FUNCTIONS (advanced feature: Funzioni)
// ----------------------------------------

spellDecl : SPELL type ID '(' paramList? ')' block # FuncDeclReturn
          | SPELL VOID ID '(' paramList? ')' block # FuncDeclVoid
          ;

paramList : param (',' param)* ;

param : type ID ;

// ----------------------------------------
//  BLOCK AND STATEMENTS
// ----------------------------------------

block : '{' stat* '}' ;

stat : varDecl ';'              # StatVarDecl
     | assign ';'               # StatAssign
     | incDecStat ';'           # StatIncDec
     | ifStat                   # StatIf
     | untilStat                # StatWhile
     | forStat                  # StatFor
     | switchStat               # StatSwitch    // Advanced feature: Flusso condizionato
     | NARRATE expr ';'         # StatPrint
     | RETURN expr ';'          # StatReturn
     | RETURN ';'               # StatReturnVoid
     | BREAK ';'                # StatBreak
     | FLEE ';'                 # StatExit
     | spellCall ';'            # StatFuncCall
     | expr '.' spellCall ';'   # StatMethodCall
     | block                    # StatoBlock
     ;

// ----------------------------------------
//  VARIABLE DECLERATION
// ----------------------------------------

varDecl : type ID '=' expr                      # VarDeclInit
        | type ID '=' SUMMON type '[' expr ']'  # VarDeclArray
        | type ID                               # VarDeclDefault
        ;

// ----------------------------------------
//  ASSIGNMENTS (includes Zucchero sintattico)
// ----------------------------------------

assign : ID '=' expr                # AssignSimple
       | ID '[' expr ']' '=' expr   # AssignArray
       | ID '.' ID '=' expr         # AssignField
       | ID '+=' expr               # AssignAdd
       | ID '-=' expr               # AssignSub
       | ID '*=' expr               # AssignMul
       | ID '/=' expr               # AssignDiv
       | ID '%=' expr               # AssignMod
       ;

// Pre/post increment/decrement as standalone statements (advanced feature: Zucchero sintattico)

incDecStat : '++' ID    # PreInc
           | '--' ID    # PreDec
           | ID '++'    # PostInc
           | ID '--'    # PostDec
           ;

// ----------------------------------------
//  CONTROL FLOW
// ----------------------------------------

ifStat : IF '(' expr ')' block (ELSE block)? ;

untilStat : UNTIL '(' expr ')' block ;

// For with optional else (advanced feature: Flusso di controllo condizionato).
forStat : FOR ID FROM expr TO expr block (ELSE block)? ;

// Switch statement (advanced feature: Flusso di controllo condizionato).
switchStat : SWITCH '(' expr ')' '{' caseClause+ defaultClause? '}' ;

caseClause : CASE expr ':' stat* ;

defaultClause : DEFAULT ':' stat* ;

// ----------------------------------------
//  FUNCTION CALLS
// ----------------------------------------

spellCall : CAST ID '(' argList? ')' ;

argList : expr (',' expr)* ;

// ----------------------------------------
//  EXPRESSIONS
//  1. Ternary (advanced feature: Zucchero sintattico)
//  2. Logical
//  3. Equality
//  4. Relational
//  5. Additive
//  6. Multiplicative
//  7. Unary
//  8. Cast (advanced feature: Conversione di tipo)
//  9. Postfix
// ----------------------------------------

expr : expr '?' expr ':' expr                       # ExprTernary
     | expr op=('or' | 'and') expr                  # ExprLogical
     | expr op=('eq' | 'neq') expr                  # ExprEquality
     | expr op=('lt' | 'gt' | 'lte' | 'gte') expr   # ExprRelational
     | expr op=('+' | '-') expr                     # ExprAddSub
     | expr op=('*' | '/' | '%') expr               # ExprMulDivMod
     | 'not' expr                                   # ExprNot
     | '-' expr                                     # ExprNeg
     | '(' type ')' expr                            # ExprCast
     | expr '[' expr ']'                            # ExprArrayAccess
     | '++' ID                                      # ExprPreInc
     | '--' ID                                      # ExprPreDec
     | ID '++'                                      # ExprPostInc
     | ID '--'                                      # ExprPostDec
     | SUMMON ID '(' ')'                            # ExprNew
     | expr '.' spellCall                           # ExprMethodCall
     | expr '.' ID                                  # ExprFieldAccess
     | spellCall                                    # ExprFuncCall
     | ID                                           # ExprId
     | INT_LIT                                      # ExprInt
     | FLOAT_LIT                                    # ExprFloat
     | BOOL_LIT                                     # ExprBool
     | STRING_LIT                                   # ExprString
     | INTERP_STRING                                # ExprInterpString
     | '(' expr ')'                                 # ExprParen
     ;

// ----------------------------------------
//  TYPES
//  Basic: Int Float Bool String
//  Domain: HP Damage Level (subtypes of Int)
//          QuestName (subtype of String)
//  Object: ID (class name used as type)
//  Array: T[]
//  Any: root of the type hierarchy
//  Void: for functions with no return value
// ----------------------------------------

type : INT          # TypeInt
     | FLOAT        # TypeFloat
     | BOOL         # TypeBool
     | STRING       # TypeString
     | HP           # TypeHP
     | DAMAGE       # TypeDamage
     | LEVEL        # TypeLevel
     | QUESTNAME    # TypeQuestName
     | ANY          # TypeAny
     | ID           # TypeObject // class name as type
     | type '[' ']' # TypeArray
     ;

// ----------------------------------------
//  KEYWORDS (language structure)
// ----------------------------------------

CREATURES : 'creatures' ; // class declaration section header
CREATURE : 'creature' ;
SPELLBOOK : 'spellbook' ; // function declaration section header
SPELL : 'spell' ;
CAST : 'cast' ; // function call keyword
QUEST : 'quest' ; // main block header
KNOWN : 'known' ;
UNSEEN : 'unseen' ;
SUMMON : 'summon' ;
NARRATE : 'narrate' ;
RETURN : 'return' ;
FLEE : 'flee' ;
IF : 'if' ;
ELSE : 'else';
UNTIL : 'until' ;
FOR : 'for' ;
FROM : 'from' ;
TO : 'to' ;
SWITCH : 'switch' ;
CASE : 'case' ;
DEFAULT : 'default' ;
BREAK : 'break' ;
VOID : 'void' ;

// ----------------------------------------
//  TYPE KEYWORDS
// ----------------------------------------

INT         : 'Int'         ;
FLOAT       : 'Float'       ;
BOOL        : 'Bool'        ;
STRING      : 'String'      ;
HP          : 'HP'          ;
DAMAGE      : 'Damage'      ;
LEVEL       : 'Level'       ;
QUESTNAME   : 'QuestName'   ;
ANY         : 'Any'         ;

// ----------------------------------------
//  LITERALS
// ----------------------------------------

BOOL_LIT    : 'true' | 'false' ;
INT_LIT     : [0-9]+  ;
FLOAT_LIT   : [0-9]+ '.' [0-9]+ ;
STRING_LIT  : '"' (~["\\\n] | '\\' .)* '"' ; // No interpolation
INTERP_STRING : 'i"' (INTERP_CHAR | INTERP_EXPR)* '"' ;

// Framgents -> Pieces to use inside other tokens (improves readability)

fragment INTERP_CHAR : ~["\\\n$] | '\\' . | '$' ~[{] ;
fragment INTERP_EXPR : '${' (~[}])* '}' ;

// ----------------------------------------
//  IDENTIFIERS
// ----------------------------------------

ID : [a-zA-Z_] [a-zA-Z_0-9]* ;

// ----------------------------------------
//  WHITESPACES AND COMMENTS
// ----------------------------------------

WS : [ \t\r\n]+ -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;