package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Llamada Tipo::funcion(argumentos) a una función asociada. */
public record NodoLlamadaAsociada(int linea, int columna, TokenFisio tipo, TokenFisio nombre,
        List<NodoAST> argumentos) implements NodoAST {
    public NodoLlamadaAsociada {
        argumentos = List.copyOf(argumentos);
    }
}
