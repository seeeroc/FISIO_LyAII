package fisio.ast;

import fisio.token.TokenFisio;

/** Asignación simple o compuesta sobre un lugar del AST. */
public record NodoAsignacion(int linea, int columna, NodoAST lugar, TokenFisio operador, NodoAST expresion)
        implements NodoAST {
}
