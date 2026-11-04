package fisio.ast;

import fisio.token.TokenFisio;

/** Constante tipada e inmutable, válida como item global o dentro de un bloque. */
public record NodoConstante(
        int linea, int columna, boolean publico, TokenFisio nombre, TokenFisio tipo, NodoAST expresion) implements NodoAST {
}
