package fisio.ast;

import fisio.token.TokenFisio;

/** Acceso posicional a una tupla mediante `.0`, `.1`, etc. */
public record NodoAccesoTupla(int linea, int columna, NodoAST objeto, TokenFisio indice) implements NodoAST {
}
