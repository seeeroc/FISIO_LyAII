package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Definición de un struct con campos nombrados o posicionales. */
public record NodoStruct(int linea, int columna, boolean publico, TokenFisio nombre,
        List<Campo> campos, boolean tupla) implements NodoAST {
    public NodoStruct {
        campos = List.copyOf(campos);
    }

    public NodoStruct(int linea, int columna, boolean publico, TokenFisio nombre, List<Campo> campos) {
        this(linea, columna, publico, nombre, campos, false);
    }

    public record Campo(TokenFisio nombre, TokenFisio tipo, boolean publico) {
        public Campo(TokenFisio nombre, TokenFisio tipo) { this(nombre, tipo, false); }
    }
}
