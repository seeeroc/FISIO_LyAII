package fisio.semantic;

import java.util.List;

/** Un analisis bloqueado nunca se presenta como un programa semanticamente valido. */
public record ResultadoSemantico(boolean analizado, List<ErrorSemantico> errores, String motivo) {
    public ResultadoSemantico {
        errores = List.copyOf(errores);
    }

    public boolean exitoso() {
        return analizado && errores.isEmpty();
    }
}
