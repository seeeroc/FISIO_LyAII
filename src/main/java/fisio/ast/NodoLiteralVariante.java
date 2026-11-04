package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Construcción de una variante enum con campos: `Evento::Punto { x: 1, y: 2 }`. */
public record NodoLiteralVariante(int linea, int columna, TokenFisio tipo, TokenFisio variante,
        List<NodoLiteralStruct.CampoValor> campos) implements NodoAST {
    public NodoLiteralVariante {
        campos = List.copyOf(campos);
    }
}
