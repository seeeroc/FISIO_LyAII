package fisio.ast;

/** NodoWhile(NodoAST): condicion: NodoAST, cuerpo: NodoBloque. */
public record NodoWhile(int linea, int columna, NodoAST condicion, NodoBloque cuerpo) implements NodoAST {
}
