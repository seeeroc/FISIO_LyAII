package fisio.ast;

import fisio.token.TokenFisio;

/** Operación unaria de negación lógica, numérica o de bits. */
public record NodoOperacionUnaria(int linea, int columna, TokenFisio operador, NodoAST operando)
        implements NodoAST {
}
