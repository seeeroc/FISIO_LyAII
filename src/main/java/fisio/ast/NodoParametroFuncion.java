package fisio.ast;

import fisio.token.TokenFisio;

/** Parámetro tipado de una función del subconjunto Rust-like. */
public record NodoParametroFuncion(int linea, int columna, TokenFisio nombre, TokenFisio tipo) implements NodoAST {
}
