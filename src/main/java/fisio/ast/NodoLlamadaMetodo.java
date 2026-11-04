package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Llamada receptor.metodo(argumentos) con ajuste automático del receptor self. */
public record NodoLlamadaMetodo(int linea, int columna, NodoAST receptor, TokenFisio nombre,
        List<NodoAST> argumentos) implements NodoAST {
    public NodoLlamadaMetodo {
        argumentos = List.copyOf(argumentos);
    }
}
