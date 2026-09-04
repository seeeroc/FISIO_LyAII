package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/** Equivalente de NodoComando(NodoAST): nombre: Token, argumentos: list[NodoAST]. */
public record NodoComando(int linea, int columna, TokenFisio nombre, List<NodoAST> argumentos) implements NodoAST {

    public NodoComando {
        argumentos = List.copyOf(argumentos);
    }
}
