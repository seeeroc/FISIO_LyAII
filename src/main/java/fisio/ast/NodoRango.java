package fisio.ast;

/** Rango con ambos extremos y distinción entre .. y ..=. */
public record NodoRango(int linea, int columna, NodoAST inicio, NodoAST fin, boolean inclusivo) implements NodoAST {
    /** Conserva compatibilidad con los rangos exclusivos existentes. */
    public NodoRango(int linea, int columna, NodoAST inicio, NodoAST fin) {
        this(linea, columna, inicio, fin, false);
    }
}
