package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoParametro(NodoAST): variable: Token, expresion: NodoAST. */
public record NodoParametro(int linea, int columna, TokenFisio variable, NodoAST expresion) implements NodoAST {
}
