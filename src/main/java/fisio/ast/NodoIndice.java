package fisio.ast;

/** Acceso a un arreglo mediante `valor[indice]`. */
public record NodoIndice(int linea, int columna, NodoAST objeto, NodoAST indice) implements NodoAST {
}
