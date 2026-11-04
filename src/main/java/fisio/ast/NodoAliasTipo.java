package fisio.ast;

import fisio.token.TokenFisio;

/** Alias transparente declarado mediante `type Nombre = Tipo;`. */
public record NodoAliasTipo(
        int linea, int columna, boolean publico, TokenFisio nombre, TokenFisio tipo) implements NodoAST {
}
