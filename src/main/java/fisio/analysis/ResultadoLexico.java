package fisio.analysis;

import java.util.List;

import fisio.token.ErrorLexico;
import fisio.token.TokenFisio;

/**
 * Resultado de FisioLexerRunner.tokenize(): equivalente a la tupla
 * (tokens, errores) que retorna Lexer.analizar() en Python. Igual que en
 * Python, {@code tokens} tambien incluye una entrada TokenFisio con
 * categoria ERROR por cada error lexico detectado (ademas de su
 * ErrorLexico correspondiente en {@code errores}) — ver
 * _agregar_error en lexer.py.
 */
public record ResultadoLexico(List<TokenFisio> tokens, List<ErrorLexico> errores) {
}
