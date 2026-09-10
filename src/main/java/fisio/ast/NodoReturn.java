package fisio.ast;

/** NodoReturn(NodoAST): expresion: NodoAST opcional (null en "return;"). Sin chequeo de tipo de retorno. */
public record NodoReturn(int linea, int columna, NodoAST expresion) implements NodoAST {
}
