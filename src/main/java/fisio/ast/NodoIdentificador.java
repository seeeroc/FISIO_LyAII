package fisio.ast;

import fisio.token.TokenFisio;

/** Uso de un identificador como expresión. */
public record NodoIdentificador(int linea, int columna, TokenFisio token) implements NodoAST {
}
