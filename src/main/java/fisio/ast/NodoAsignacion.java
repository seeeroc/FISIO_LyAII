package fisio.ast;

/** Equivalente de NodoAsignacion(NodoAST): lugar: NodoAST, expresion: NodoAST. */
public record NodoAsignacion(int linea, int columna, NodoAST lugar, NodoAST expresion) implements NodoAST {
}
