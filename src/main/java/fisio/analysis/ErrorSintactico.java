package fisio.analysis;

import fisio.token.TokenFisio;

/**
 * Equivalente funcional de ErrorSintactico en parser.py (token, mensaje,
 * linea, columna). "token" es SIEMPRE un TokenFisio -incluido el caso EOF
 * (categoria=CategoriaToken.EOF, lexema="", idFisio="EOF")- replicando
 * _crear_eof(): linea/columna del pseudo-token EOF son las del ULTIMO
 * token realmente consumido, desplazadas por la longitud de su lexema; o
 * (1,1) si no se consumio ningun token todavia.
 */
public record ErrorSintactico(TokenFisio token, String mensaje, int linea, int columna) {
}
