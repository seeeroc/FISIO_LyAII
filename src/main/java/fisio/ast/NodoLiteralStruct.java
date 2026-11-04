package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Construcción de un struct, opcionalmente actualizando desde `..base`. */
public record NodoLiteralStruct(int linea, int columna, TokenFisio nombre,
        List<CampoValor> campos, NodoAST base) implements NodoAST {
    public NodoLiteralStruct {
        campos = List.copyOf(campos);
    }

    public record CampoValor(TokenFisio nombre, NodoAST valor) {
    }
}
