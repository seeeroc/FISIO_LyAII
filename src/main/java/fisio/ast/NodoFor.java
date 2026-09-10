package fisio.ast;

import fisio.token.TokenFisio;

/**
 * NodoFor(NodoAST): variable: Token, iterable: NodoAST, cuerpo: NodoBloque.
 * Solo reconocimiento sintactico de "for variable in iterable { ... }" --
 * sin iteradores reales, rangos, ownership ni destructuring (fuera de alcance).
 */
public record NodoFor(int linea, int columna, TokenFisio variable, NodoAST iterable, NodoBloque cuerpo)
        implements NodoAST {
}
