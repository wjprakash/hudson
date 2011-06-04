/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
header {
  package hudson.model.labels;
  import hudson.model.LabelExt;
}

class LabelExpressionParser extends Parser;
options {
  defaultErrorHandler=false;
}

// order of precedence is as per http://en.wikipedia.org/wiki/Logical_connective#Order_of_precedence

expr
returns [LabelExt l]
  : l=term1 EOF
  ;

term1
returns [LabelExt l]
{ LabelExt r; }
  : l=term2( IFF r=term2 {l=l.iff(r);} )?
  ;

term2
returns [LabelExt l]
{ LabelExt r; }
  : l=term3( IMPLIES r=term3 {l=l.implies(r);} )?
  ;

term3
returns [LabelExt l]
{ LabelExt r; }
  : l=term4 ( OR r=term4 {l=l.or(r);} )?
  ;

term4
returns [LabelExt l]
{ LabelExt r; }
  : l=term5 ( AND r=term5 {l=l.and(r);} )?
  ;

term5
returns [LabelExt l]
{ LabelExt x; }
  : l=term6
  | NOT x=term6
    { l=x.not(); }
  ;

term6
returns [LabelExt l]
options { generateAmbigWarnings=false; }
  : LPAREN l=term1 RPAREN
    { l=l.paren(); }
  | a:ATOM
    { l=LabelAtomExt.get(a.getText()); }
  | s:STRINGLITERAL
    { l=LabelAtomExt.get(hudson.util.QuotedStringTokenizer.unquote(s.getText())); }
  ;

class LabelExpressionLexer extends Lexer;

AND:    "&&";
OR:     "||";
NOT:    "!";
IMPLIES:"->";
IFF:    "<->";
LPAREN: "(";
RPAREN: ")";

protected
IDENTIFIER_PART
    :   ~( '&' | '|' | '!' | '<' | '>' | '(' | ')' | ' ' | '\t' | '\"' | '\'' )
    ;

ATOM
/* the real check of valid identifier happens in LabelAtomExt.get() */
    :   (IDENTIFIER_PART)+
    ;

WS
  : (' '|'\t')+
    { $setType(Token.SKIP); }
  ;

STRINGLITERAL
    :   '"'
        ( '\\' ( 'b' | 't' | 'n' | 'f' | 'r' | '\"' | '\'' | '\\' )   /* escape */
        |  ~( '\\' | '"' | '\r' | '\n' )
        )*
        '"'
    ;
