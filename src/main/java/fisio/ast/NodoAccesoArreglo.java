package fisio.ast;

import fisio.token.TokenFisio;

/**
 * Equivalente de NodoAccesoArreglo(NodoAST): identificador: Token, indice: Token.
 * "indice" es Token (no NodoAST) en parser.py: _consumir_numero() retorna un
 * Token crudo, sin envolverlo en NodoNumero.
 */
public record NodoAccesoArreglo(
        int linea, int columna, TokenFisio identificador, TokenFisio indice) implements NodoAST {
}
