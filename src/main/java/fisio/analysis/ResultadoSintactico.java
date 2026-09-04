package fisio.analysis;

import java.util.List;

import fisio.ast.NodoPrograma;

/**
 * Resultado de FisioParserRunner.parse(): equivalente a la tupla
 * (NodoPrograma, list[ErrorSintactico]) que retorna Parser.parse() en
 * Python. En el Paso 5B-1 (sin recuperacion todavia) "errores" contiene
 * como maximo UN elemento: el primer error sintactico encontrado.
 *
 * Igual que en Python -donde parse() SIEMPRE retorna un NodoPrograma, aun
 * habiendo errores, con las sentencias exitosas parseadas antes del
 * primer error- el campo "ast" nunca es null: contiene el programa
 * completo si no hubo errores, o un NodoPrograma parcial (linea=1,
 * columna=1, con las sentencias de nivel superior parseadas exitosamente
 * ANTES del primer error) si los hubo.
 */
public record ResultadoSintactico(NodoPrograma ast, List<ErrorSintactico> errores) {

    public ResultadoSintactico {
        errores = List.copyOf(errores);
    }
}
