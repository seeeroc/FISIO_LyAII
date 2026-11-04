package fisio.ast;

import fisio.token.TokenFisio;

/** Conversión primitiva explícita mediante la sintaxis `expresion as Tipo`. */
public record NodoConversion(int linea, int columna, NodoAST expresion, TokenFisio tipo) implements NodoAST {
}
