package fisio.ast;

import fisio.token.TokenFisio;

/**
 * Equivalente de NodoAsignacion(NodoAST): lugar: NodoAST, operador:
 * TokenFisio, expresion: NodoAST.
 *
 * Revision general (Rust-like): se agrego "operador" (antes no existia --
 * el operador "=" estaba implicito) para admitir asignaciones compuestas
 * ("+=", "-=", "*=", "/=", "%=") ademas de la asignacion simple ("="), sin
 * quitar ningun campo existente. Unico punto de construccion en
 * FISIO.jj (Asignacion()); fisio.gui.FormateadorAst es el unico lector.
 */
public record NodoAsignacion(int linea, int columna, NodoAST lugar, TokenFisio operador, NodoAST expresion)
        implements NodoAST {
}
