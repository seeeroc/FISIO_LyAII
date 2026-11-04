package fisio.ast;

/** Finaliza el bucle envolvente más cercano. */
public record NodoBreak(int linea, int columna) implements NodoAST {
}
