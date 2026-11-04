package fisio.ast;

import fisio.token.TokenFisio;

/** Lectura de un campo mediante `valor.campo`. */
public record NodoAccesoCampo(int linea, int columna, NodoAST objeto, TokenFisio campo) implements NodoAST {
}
