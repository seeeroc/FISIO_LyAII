package fisio.analysis;

import java.io.StringReader;

import fisio.FISIOParser;
import fisio.ParseException;
import fisio.TokenMgrError;
import fisio.ast.NodoPrograma;

/**
 * Ejecuta el analisis sintactico de FISIO — equivalente funcional de
 * Parser(tokens).parse() en Python (F6), re-tokenizando internamente a
 * partir del mismo String de codigo (arquitectura aprobada, punto 7 del
 * plan de migracion).
 *
 * Paso 5B-2: FISIOParser ya no lanza ninguna excepcion de control de flujo
 * para errores previstos del lenguaje — acumula ErrorSintactico en su
 * propia lista (equivalente a self.errores en Python) mientras construye
 * el AST parcial, exactamente como parser.py. Este metodo solo recolecta
 * ambos resultados al final. ParseException/TokenMgrError se conservan
 * unicamente como fallback defensivo para estados no previstos por los
 * chequeos manuales de FISIO.jj (nunca deberian dispararse).
 */
public final class FisioParserRunner {

    private FisioParserRunner() {
    }

    public static ResultadoSintactico parse(String codigo) {
        FISIOParser parser = new FISIOParser(new StringReader(codigo));

        try {
            NodoPrograma ast = parser.Programa();
            return new ResultadoSintactico(ast, parser.errores());
        } catch (ParseException | TokenMgrError e) {
            // No deberia ocurrir: cada punto de fallo previsto en la
            // gramatica ya esta cubierto por un chequeo manual que registra
            // ErrorSintactico y retorna null, igual que parser.py. Si esto
            // se dispara, hay una rama sin cubrir — no se oculta detras de
            // un ResultadoSintactico "normal".
            throw new IllegalStateException(
                "Fallo de parsing no cubierto por los chequeos manuales de FISIO.jj — revisar FISIO.jj: "
                    + e.getMessage(), e);
        }
    }
}
