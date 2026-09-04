package fisio.token;

/**
 * Traduccion fiel de ALFABETO_VALIDO (token_types.py): letras (a-z, A-Z),
 * digitos, espacios/tab/CR/LF, operadores matematicos (+ - * / ^), signos
 * ( ) [ ] , . ; y los caracteres ": = ! < >" usados en operadores
 * relacionales/asignacion. Centralizado aqui para que tanto la
 * clasificacion de errores lexicos como cualquier otro componente futuro
 * usen exactamente la misma definicion, sin duplicarla.
 */
public final class AlfabetoFisio {

    private static final String CARACTERES_VALIDOS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
          + "0123456789"
          + " \t\r\n"
          + "+-*/^"
          + "()[],.;"
          + ":=!<>";

    private AlfabetoFisio() {
    }

    public static boolean esValido(char c) {
        return CARACTERES_VALIDOS.indexOf(c) >= 0;
    }

    /** Equivalente a _contiene_caracter_fuera_alfabeto en lexer.py. */
    public static boolean contieneCaracterFueraDeAlfabeto(String lexema) {
        for (int i = 0; i < lexema.length(); i++) {
            if (!esValido(lexema.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
