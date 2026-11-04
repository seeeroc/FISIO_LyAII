package fisio.ast;

/** Condicional con bloque if y rama else opcional. */
public record NodoIf(int linea, int columna, NodoAST condicion, NodoBloque cuerpoIf, NodoAST cuerpoElse)
        implements NodoAST {
}
