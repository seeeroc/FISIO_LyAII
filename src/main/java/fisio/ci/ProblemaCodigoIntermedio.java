package fisio.ci;

/** Problema detectado al intentar resolver numéricamente una expresión. */
public record ProblemaCodigoIntermedio(int linea, int columna, String mensaje) {
}
