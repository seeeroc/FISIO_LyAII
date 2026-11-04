package fisio.ast;

import fisio.token.TokenFisio;

/** Construcción de una variante simple mediante `Tipo::Variante`. */
public record NodoVarianteEnum(int linea, int columna, TokenFisio tipo, TokenFisio variante) implements NodoAST {
}
