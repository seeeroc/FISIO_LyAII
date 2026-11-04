package fisio.ast;

import fisio.token.TokenFisio;

/** Literal numérico, booleano, de cadena o de carácter. */
public record NodoNumero(int linea, int columna, TokenFisio token) implements NodoAST {
}
