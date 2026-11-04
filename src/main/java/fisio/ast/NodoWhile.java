package fisio.ast;

/** Bucle while con condición booleana. */
public record NodoWhile(int linea, int columna, NodoAST condicion, NodoBloque cuerpo) implements NodoAST {
}
