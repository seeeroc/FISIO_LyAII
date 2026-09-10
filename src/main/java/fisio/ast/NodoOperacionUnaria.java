package fisio.ast;

import fisio.token.TokenFisio;

/** NodoOperacionUnaria(NodoAST): operador: Token ('-' o '!'), operando: NodoAST. */
public record NodoOperacionUnaria(int linea, int columna, TokenFisio operador, NodoAST operando)
        implements NodoAST {
}
