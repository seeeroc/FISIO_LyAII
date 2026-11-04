package fisio.ast;

import java.util.List;

/** Literal de tupla; la tupla vacía representa el valor unidad `()`. */
public record NodoTupla(int linea, int columna, List<NodoAST> elementos) implements NodoAST {
    public NodoTupla {
        elementos = List.copyOf(elementos);
    }
}
