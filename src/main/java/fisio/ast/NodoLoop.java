package fisio.ast;

/** NodoLoop(NodoAST): cuerpo: NodoBloque. Sin semantica (no hay 'break'/'continue'). */
public record NodoLoop(int linea, int columna, NodoBloque cuerpo) implements NodoAST {
}
