package fisio.analysis;

import java.io.StringReader;

// Importamos el nombre correcto generado por JavaCC
import fisio.FisioParser; 
import fisio.ParseException;
import fisio.TokenMgrError;
import fisio.ast.NodoPrograma;

public final class FisioParserRunner {

    private FisioParserRunner() {
    }

    public static ResultadoSintactico parse(String codigo) {
        // Usamos FisioParser
        FisioParser parser = new FisioParser(new StringReader(codigo));

        try {
            // Esto llamará a la regla sintáctica principal que agregaremos abajo
            NodoPrograma ast = parser.Programa();
            return new ResultadoSintactico(ast, parser.errores());
        } catch (ParseException | TokenMgrError e) {
            throw new IllegalStateException(
                "Fallo de parsing no cubierto por los chequeos manuales de FISIO.jj - revisar FISIO.jj: " 
                + e.getMessage(), e);
        }
    }
}
