package fisio.ast;

import fisio.token.TokenFisio;

/** Bucle for sobre un rango o arreglo. */
public record NodoFor(int linea, int columna, TokenFisio variable, NodoAST iterable, NodoBloque cuerpo)
        implements NodoAST {
}
