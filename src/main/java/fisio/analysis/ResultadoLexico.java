package fisio.analysis;

import java.util.List;
import fisio.token.ErrorLexico;
import fisio.token.TokenFisio;

/** Instantánea: tokens válidos y errores en listas separadas, sin incluir EOF. */
public record ResultadoLexico(List<TokenFisio> tokens, List<ErrorLexico> errores) {
    public ResultadoLexico {
        tokens = List.copyOf(tokens);
        errores = List.copyOf(errores);
    }
}
