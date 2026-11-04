package fisio.ast;

/** Creación de una referencia compartida (&T) o mutable (&mut T). */
public record NodoReferencia(int linea, int columna, boolean mutable, NodoAST expresion)
        implements NodoAST {
}
