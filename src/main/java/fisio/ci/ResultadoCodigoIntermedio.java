package fisio.ci;

import java.util.List;

/** Resultado inmutable de la fase de código intermedio. */
public record ResultadoCodigoIntermedio(
        boolean generado,
        List<ExpresionIntermedia> expresiones,
        List<ProblemaCodigoIntermedio> problemas,
        String motivo) {

    public ResultadoCodigoIntermedio {
        expresiones = List.copyOf(expresiones);
        problemas = List.copyOf(problemas);
    }

    public static ResultadoCodigoIntermedio bloqueado(String motivo) {
        return new ResultadoCodigoIntermedio(false, List.of(), List.of(), motivo);
    }

    public boolean exitoso() {
        return generado && problemas.isEmpty();
    }
}
