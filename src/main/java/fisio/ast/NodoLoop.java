package fisio.ast;

/** Bucle incondicional; su terminación depende de la presencia de un `break` alcanzable. */
public record NodoLoop(int linea, int columna, NodoBloque cuerpo) implements NodoAST {
}
