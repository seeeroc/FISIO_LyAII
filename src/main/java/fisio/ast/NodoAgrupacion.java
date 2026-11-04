package fisio.ast;

/** Expresión delimitada por paréntesis. */
public record NodoAgrupacion(int linea, int columna, NodoAST expresion) implements NodoAST {
}
