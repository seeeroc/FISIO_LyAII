package fisio.ast;

import java.util.List;

/** Literal de arreglo homogéneo: `[a, b, c]`. */
public record NodoArreglo(int linea, int columna, List<NodoAST> elementos) implements NodoAST {
    public NodoArreglo {
        elementos = List.copyOf(elementos);
    }
}
