package fisio.ast;

/** Retorno explícito con una expresión opcional; el analizador semántico comprueba su tipo. */
public record NodoReturn(int linea, int columna, NodoAST expresion) implements NodoAST {
}
