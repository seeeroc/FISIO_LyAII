package fisio.ast;

import fisio.token.TokenFisio;

/**
 * NodoDeclaracion(NodoAST): identificador: Token, mutable: boolean,
 * tipo: Token opcional (null si no se anoto), expresion: NodoAST.
 * Fase 2 (let / let mut / let x: tipo = ...). No valida el tipo: "tipo" se
 * guarda tal cual (un ID cualquiera, p.ej. i32/f64), sin comprobacion
 * semantica -- eso esta fuera de alcance.
 */
public record NodoDeclaracion(
        int linea, int columna, TokenFisio identificador, boolean mutable, TokenFisio tipo, NodoAST expresion)
        implements NodoAST {
}
