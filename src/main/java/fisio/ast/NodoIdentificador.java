package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoIdentificador(NodoAST): token: Token. */
public record NodoIdentificador(int linea, int columna, TokenFisio token) implements NodoAST {
}
