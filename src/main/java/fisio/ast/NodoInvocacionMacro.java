package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/** Invocación de macro con sus argumentos, sin expansión ni ejecución. */
public record NodoInvocacionMacro(int linea, int columna, TokenFisio nombre, List<NodoAST> argumentos)
        implements NodoAST {

    public NodoInvocacionMacro {
        argumentos = List.copyOf(argumentos);
    }
}
