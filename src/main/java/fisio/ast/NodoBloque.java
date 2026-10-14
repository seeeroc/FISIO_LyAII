package fisio.ast;

import java.util.List;

/** Bloque de sentencias con una expresión final opcional. */
public record NodoBloque(int linea, int columna, List<NodoAST> sentencias, NodoAST expresionFinal)
        implements NodoAST {

    public NodoBloque {
        sentencias = List.copyOf(sentencias);
    }
}
