package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Contrato trait formado por firmas de métodos obligatorios. */
public record NodoTrait(int linea, int columna, boolean publico, TokenFisio nombre,
        List<NodoFirmaTrait> metodos) implements NodoAST {
    public NodoTrait {
        metodos = List.copyOf(metodos);
    }
}
