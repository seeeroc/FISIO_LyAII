package fisio.ast;

import fisio.token.TokenFisio;

/** Equivalente de NodoOperacionRelacional(NodoAST): operador: Token, izquierda: NodoAST, derecha: NodoAST. */
public record NodoOperacionRelacional(
        int linea, int columna, TokenFisio operador, NodoAST izquierda, NodoAST derecha) implements NodoAST {
}
