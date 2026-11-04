package fisio.ast;

/** Acceso al valor apuntado por una referencia mediante el operador '*'. */
public record NodoDesreferencia(int linea, int columna, NodoAST referencia) implements NodoAST {
}
