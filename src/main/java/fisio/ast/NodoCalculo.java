package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoCalculo(NodoAST): nombre: Token, expresion: NodoAST. */
public record NodoCalculo(int linea, int columna, TokenFisio nombre, NodoAST expresion) implements NodoAST {
}
