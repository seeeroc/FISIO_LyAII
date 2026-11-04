package fisio.ast;

import java.util.List;

/** Raíz del AST con los elementos de nivel superior. */
public record NodoPrograma(int linea, int columna, List<NodoAST> sentencias) implements NodoAST {

    public NodoPrograma {
        sentencias = List.copyOf(sentencias);
    }
}
