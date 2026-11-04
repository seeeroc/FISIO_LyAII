package fisio.ast;

import fisio.token.TokenFisio;

/** Operación binaria aritmética, lógica o de bits. */
public record NodoOperacion(
        int linea, int columna, TokenFisio operador, NodoAST izquierda, NodoAST derecha) implements NodoAST {
}
