package fisio.ast;

import fisio.token.TokenFisio;

/** Declaración let con mutabilidad y anotación de tipo opcionales. */
public record NodoDeclaracion(
        int linea, int columna, TokenFisio identificador, boolean mutable, TokenFisio tipo, NodoAST expresion)
        implements NodoAST {
}
