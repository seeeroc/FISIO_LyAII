package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Firma obligatoria declarada dentro de un trait, sin cuerpo. */
public record NodoFirmaTrait(int linea, int columna, TokenFisio nombre,
        List<NodoParametroFuncion> parametros, TokenFisio tipoRetorno) implements NodoAST {
    public NodoFirmaTrait {
        parametros = List.copyOf(parametros);
    }
}
