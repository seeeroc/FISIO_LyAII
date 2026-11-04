package fisio.analysis;

import java.io.StringReader;
import java.util.Objects;

import fisio.FisioParser;
import fisio.ParseException;
import fisio.TokenMgrError;
import fisio.ast.NodoPrograma;

public final class FisioParserRunner {

    private FisioParserRunner() {
    }

    public static ResultadoSintactico parse(String codigo) {
        Objects.requireNonNull(codigo, "codigo");
        FisioParser parser = new FisioParser(new StringReader(codigo));
        parser.configurarFinFuente(codigo);
        try {
            NodoPrograma ast = parser.Programa();
            return new ResultadoSintactico(ast, parser.errores());
        } catch (ParseException | TokenMgrError e) {
            throw new IllegalStateException(
                "Fallo de parsing no cubierto por la recuperación de FISIO.jj: "
                + e.getMessage(), e);
        }
    }
}
