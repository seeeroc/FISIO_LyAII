package fisio.analysis;

import java.util.List;

import fisio.ast.NodoPrograma;

/** AST completo o parcial y todos los errores recuperados, en una lista inmutable. */
public record ResultadoSintactico(NodoPrograma ast, List<ErrorSintactico> errores) {

    public ResultadoSintactico {
        errores = List.copyOf(errores);
    }
}
