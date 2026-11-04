package fisio.ast;

/** Rango con ambos extremos y distinción entre .. y ..=. */
public record NodoRango(int linea, int columna, NodoAST inicio, NodoAST fin, boolean inclusivo) implements NodoAST {
}
