package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/** Llamada de función representada como expresión. */
public record NodoLlamadaFuncion(int linea, int columna, TokenFisio nombre, List<NodoAST> argumentos)
        implements NodoAST {

    public NodoLlamadaFuncion {
        argumentos = List.copyOf(argumentos);
    }
}
