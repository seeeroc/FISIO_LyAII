package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoNumero(NodoAST): token: Token. */
public record NodoNumero(int linea, int columna, TokenFisio token) implements NodoAST {
}
