package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoOperacion(NodoAST): operador: Token, izquierda: NodoAST, derecha: NodoAST. */
public record NodoOperacion(
        int linea, int columna, TokenFisio operador, NodoAST izquierda, NodoAST derecha) implements NodoAST {
}
