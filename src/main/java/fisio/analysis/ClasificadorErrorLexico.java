package fisio.analysis;

import fisio.token.AlfabetoFisio;

/**
 * Reclasifica lexemas de error (crudos de LEXEMA_INVALIDO/OP_INCOMPLETO, o ya
 * fusionados por contiguidad en FisioLexerRunner — ver Paso 3.1) hacia los
 * mismos 5 mensajes que produce lexer.py, replicando -en el mismo orden de
 * prioridad y con las mismas condiciones exactas- la logica de _leer_numero,
 * _leer_decimal_sin_entero, _leer_identificador_o_reservada,
 * _leer_operador_relacional y _leer_caracter_invalido. No se reformulan ni
 * mejoran los mensajes.
 *
 * La clasificacion depende UNICAMENTE del primer caracter del lexema (nunca
 * del "kind" original de JavaCC): esto es lo que permite reutilizar esta
 * misma logica tanto para un LEXEMA_INVALIDO/OP_INCOMPLETO aislado como para
 * un lexema fusionado por FisioLexerRunner (p. ej. "abc:", "12:", "::"),
 * porque lexer.py tampoco distingue por "que funcion origino el fragmento"
 * sino por el primer caracter de la secuencia consumida.
 *
 * Nota de fidelidad (hallazgo documentado, no corregido aqui): en
 * lexer.py, _leer_identificador_o_reservada siempre reporta
 * "Caracter fuera del alfabeto FISIO" para su continuacion mal formada,
 * incluso cuando el fragmento capturado esta enteramente dentro de
 * ALFABETO_VALIDO (p. ej. "abc:xyz" — todos sus caracteres son validos).
 * Ese mismo comportamiento se replica aqui a proposito.
 */
final class ClasificadorErrorLexico {

    static final String NUMERO_DECIMAL_SIN_PARTE_ENTERA = "Numero decimal sin parte entera";
    static final String NUMERO_MAL_FORMADO = "Numero mal formado";
    static final String OPERADOR_RELACIONAL_INCOMPLETO = "Operador relacional incompleto o invalido";
    static final String CARACTER_FUERA_DE_ALFABETO = "Caracter fuera del alfabeto FISIO";
    static final String CARACTER_NO_RECONOCIDO = "Caracter no reconocido en este contexto";

    private ClasificadorErrorLexico() {
    }

    static String clasificar(String lexema) {
        char primero = lexema.charAt(0);

        if (primero == '.') {
            // _leer_decimal_sin_entero: mensaje fijo, sin chequeo de alfabeto.
            return NUMERO_DECIMAL_SIN_PARTE_ENTERA;
        }

        if (Character.isDigit(primero)) {
            // _leer_numero: "Numero mal formado" salvo caracter fuera de alfabeto.
            return AlfabetoFisio.contieneCaracterFueraDeAlfabeto(lexema)
                ? CARACTER_FUERA_DE_ALFABETO
                : NUMERO_MAL_FORMADO;
        }

        if ((primero >= 'a' && primero <= 'z') || (primero >= 'A' && primero <= 'Z')) {
            // _leer_identificador_o_reservada: mensaje fijo (ver nota de fidelidad arriba).
            return CARACTER_FUERA_DE_ALFABETO;
        }

        if (primero == ':' || primero == '!') {
            // _leer_operador_relacional con continuacion mal formada (":x", "!5"...).
            return OPERADOR_RELACIONAL_INCOMPLETO;
        }

        // _leer_caracter_invalido: el primer caracter de LEXEMA_INVALIDO en este
        // punto solo puede provenir de fuera de ALFABETO_VALIDO (todo caracter
        // valido ya fue capturado por una rama anterior), asi que este chequeo
        // replica la estructura de Python fielmente aunque CARACTER_NO_RECONOCIDO
        // resulte inalcanzable en la practica (ver reporte del Paso 3).
        return AlfabetoFisio.contieneCaracterFueraDeAlfabeto(lexema)
            ? CARACTER_FUERA_DE_ALFABETO
            : CARACTER_NO_RECONOCIDO;
    }
}
