package fisio.gui;

import fisio.analysis.FisioLexerRunner;
import fisio.analysis.FisioParserRunner;
import fisio.analysis.ResultadoLexico;
import fisio.analysis.ResultadoSintactico;
import fisio.ci.GeneradorCodigoIntermedio;
import fisio.ci.ResultadoCodigoIntermedio;
import fisio.semantic.FisioSemanticRunner;
import fisio.semantic.ResultadoSemantico;

/** Orquestación sin componentes Swing; recibe una instantánea del documento. */
final class CoordinadorAnalisis {
    record Resultado(String codigo, ResultadoLexico lexico, ResultadoSintactico sintactico,
                     ResultadoSemantico semantico, ResultadoCodigoIntermedio codigoIntermedio) {
        EstadoAnalisis estadoLexico() { return estado(lexico.errores().size()); }
        EstadoAnalisis estadoSintactico() {
            return sintactico == null ? EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR : estado(sintactico.errores().size());
        }
        EstadoAnalisis estadoSemantico() {
            return semantico == null || !semantico.analizado() ? EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR : estado(semantico.errores().size());
        }
        EstadoAnalisis estadoCodigoIntermedio() {
            return codigoIntermedio.generado() ? estado(codigoIntermedio.problemas().size())
                : EstadoAnalisis.BLOQUEADO_POR_FASE_ANTERIOR;
        }
        int errores() {
            return lexico.errores().size() + (sintactico == null ? 0 : sintactico.errores().size())
                + (semantico == null ? 0 : semantico.errores().size()) + codigoIntermedio.problemas().size();
        }
        private static EstadoAnalisis estado(int errores) {
            return errores == 0 ? EstadoAnalisis.CORRECTO : EstadoAnalisis.CON_ERRORES;
        }
    }

    Resultado ejecutar(String codigo) {
        var lexico = FisioLexerRunner.tokenize(codigo);
        if (!lexico.errores().isEmpty()) return new Resultado(codigo, lexico, null, null,
            ResultadoCodigoIntermedio.bloqueado("La fase léxica contiene errores. Corrige el código y vuelve a ejecutar."));
        var sintactico = FisioParserRunner.parse(codigo);
        if (!sintactico.errores().isEmpty()) return new Resultado(codigo, lexico, sintactico, null,
            ResultadoCodigoIntermedio.bloqueado("La fase sintáctica contiene errores. Corrige el código y vuelve a ejecutar."));
        var semantico = FisioSemanticRunner.analizar(lexico, sintactico);
        var codigoIntermedio = semantico.exitoso()
            ? GeneradorCodigoIntermedio.generar(sintactico.ast())
            : ResultadoCodigoIntermedio.bloqueado("La fase semántica contiene errores. Corrige el código y vuelve a ejecutar.");
        return new Resultado(codigo, lexico, sintactico, semantico, codigoIntermedio);
    }
}
