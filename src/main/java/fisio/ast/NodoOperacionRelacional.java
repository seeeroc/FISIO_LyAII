package fisio.ast;

import fisio.token.TokenFisio;

/** Comparación de igualdad u orden entre dos expresiones. */
public record NodoOperacionRelacional(
        int linea, int columna, TokenFisio operador, NodoAST izquierda, NodoAST derecha) implements NodoAST {
}
