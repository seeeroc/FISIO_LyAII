package fisio.analysis;

import fisio.token.TokenFisio;

/** Diagnóstico sintáctico asociado al token y la posición donde se detectó. */
public record ErrorSintactico(TokenFisio token, String mensaje, int linea, int columna) {
}
