package fisio.ast;

import fisio.token.TokenFisio;

/** Elemento static con almacenamiento único; static mut requiere unsafe y se rechaza semánticamente. */
public record NodoEstatico(int linea, int columna, boolean publico, boolean mutable,
        TokenFisio nombre, TokenFisio tipo, NodoAST expresion) implements NodoAST {
}
