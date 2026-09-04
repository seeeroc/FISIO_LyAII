package fisio.ast;

/** Equivalente de NodoAgrupacion(NodoAST): expresion: NodoAST. */
public record NodoAgrupacion(int linea, int columna, NodoAST expresion) implements NodoAST {
}
