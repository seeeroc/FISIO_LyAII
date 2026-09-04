package fisio.ast;

import java.util.List;

/** Equivalente de NodoPrograma(NodoAST): sentencias: list[NodoAST]. */
public record NodoPrograma(int linea, int columna, List<NodoAST> sentencias) implements NodoAST {

    public NodoPrograma {
        sentencias = List.copyOf(sentencias);
    }
}
