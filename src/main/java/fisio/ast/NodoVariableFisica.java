package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoVariableFisica(NodoAST): token: Token. */
public record NodoVariableFisica(int linea, int columna, TokenFisio token) implements NodoAST {
}
