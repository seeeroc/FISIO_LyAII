package fisio.token;

/**
 * Categorias lexicas de FISIO, equivalente a TipoToken en token_types.py.
 * ERROR se usa para los lexemas capturados por LEXEMA_INVALIDO/OP_INCOMPLETO
 * una vez reclasificados (ver fisio.analysis.FisioLexerRunner).
 *
 * EOF (Paso 5B-1, unica adicion a esta clase "congelada" desde el Paso
 * 3.1): representa el pseudo-token de fin de archivo que parser.py usa
 * como "token actual" en ErrorSintactico cuando el error se detecta con
 * la entrada agotada (_crear_eof() en parser.py). token_types.TipoToken
 * en Python TAMPOCO declara "EOF" entre sus constantes oficiales -lo usa
 * como string suelto solo dentro de _crear_eof()-, asi que esta adicion
 * es puramente aditiva: ningun camino lexico (PR/OPM/OPR/SIG/ID/NUM/ERROR)
 * cambia de comportamiento; ver fisio.analysis.ErrorSintactico.
 */
public enum CategoriaToken {
    PR,
    OPM,
    OPR,
    SIG,
    ID,
    NUM,
    ERROR,
    EOF
}
