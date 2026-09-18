package fisio.semantic;

import java.util.List;
import java.util.Objects;
import fisio.analysis.FisioLexerRunner;
import fisio.analysis.FisioParserRunner;
import fisio.analysis.ResultadoLexico;
import fisio.analysis.ResultadoSintactico;

/** Entrada independiente de Swing. Nunca analiza un AST con errores previos. */
public final class FisioSemanticRunner {
    private FisioSemanticRunner() {}

    public static ResultadoSemantico analizar(String codigo) {
        Objects.requireNonNull(codigo, "codigo");
        ResultadoLexico lexico = FisioLexerRunner.tokenize(codigo);
        if (!lexico.errores().isEmpty()) return bloqueado("Corrige primero los errores lexicos.");
        return analizar(lexico, FisioParserRunner.parse(codigo));
    }

    /** Los dos resultados deben pertenecer al mismo texto, como garantiza la GUI. */
    public static ResultadoSemantico analizar(ResultadoLexico lexico, ResultadoSintactico sintaxis) {
        Objects.requireNonNull(lexico, "lexico");
        Objects.requireNonNull(sintaxis, "sintaxis");
        if (!lexico.errores().isEmpty()) return bloqueado("Corrige primero los errores lexicos.");
        if (!sintaxis.errores().isEmpty() || sintaxis.ast() == null) {
            return bloqueado("Corrige primero los errores sintacticos.");
        }
        return new AnalizadorSemantico().analizar(sintaxis.ast());
    }

    private static ResultadoSemantico bloqueado(String motivo) {
        return new ResultadoSemantico(false, List.of(), motivo);
    }
}
