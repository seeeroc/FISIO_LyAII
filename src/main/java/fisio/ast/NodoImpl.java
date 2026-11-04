package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Bloque impl inherente o implementación de un trait para un struct o enum. */
public record NodoImpl(int linea, int columna, TokenFisio rasgo, TokenFisio tipo,
        List<NodoFuncion> metodos)
        implements NodoAST {
    public NodoImpl {
        metodos = List.copyOf(metodos);
    }
}
