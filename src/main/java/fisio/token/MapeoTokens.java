package fisio.token;

import fisio.FisioParserConstants; // Importamos las constantes autogeneradas
import java.util.HashMap;
import java.util.Map;

public class MapeoTokens {

    // Estructura ligera para almacenar la dupla (ej. "PR", "PR_01")
    public static class InfoToken {

        public final CategoriaToken categoria;
        public final String idTok;

        public InfoToken(CategoriaToken categoria, String idTok) {
            this.categoria = categoria;
            this.idTok = idTok;
        }
    }

    private static final Map<Integer, InfoToken> MAPEO = new HashMap<>();

    static {
        // ==========================================
        // Palabras Reservadas (PR)
        // ==========================================
        MAPEO.put(FisioParserConstants.FN, new InfoToken(CategoriaToken.PR, "PR_01"));
        MAPEO.put(FisioParserConstants.LET, new InfoToken(CategoriaToken.PR, "PR_02"));
        MAPEO.put(FisioParserConstants.MUT, new InfoToken(CategoriaToken.PR, "PR_03"));
        MAPEO.put(FisioParserConstants.IF, new InfoToken(CategoriaToken.PR, "PR_04"));
        MAPEO.put(FisioParserConstants.ELSE, new InfoToken(CategoriaToken.PR, "PR_05"));
        MAPEO.put(FisioParserConstants.WHILE, new InfoToken(CategoriaToken.PR, "PR_06"));
        MAPEO.put(FisioParserConstants.FOR, new InfoToken(CategoriaToken.PR, "PR_07"));
        MAPEO.put(FisioParserConstants.IN, new InfoToken(CategoriaToken.PR, "PR_08"));
        MAPEO.put(FisioParserConstants.LOOP, new InfoToken(CategoriaToken.PR, "PR_09"));
        MAPEO.put(FisioParserConstants.MATCH, new InfoToken(CategoriaToken.PR, "PR_10"));
        MAPEO.put(FisioParserConstants.RETURN, new InfoToken(CategoriaToken.PR, "PR_11"));
        MAPEO.put(FisioParserConstants.STRUCT, new InfoToken(CategoriaToken.PR, "PR_12"));
        MAPEO.put(FisioParserConstants.ENUM, new InfoToken(CategoriaToken.PR, "PR_13"));
        MAPEO.put(FisioParserConstants.IMPL, new InfoToken(CategoriaToken.PR, "PR_14"));
        MAPEO.put(FisioParserConstants.TRAIT, new InfoToken(CategoriaToken.PR, "PR_15"));
        MAPEO.put(FisioParserConstants.PUB, new InfoToken(CategoriaToken.PR, "PR_16"));
        MAPEO.put(FisioParserConstants.USE, new InfoToken(CategoriaToken.PR, "PR_17"));
        MAPEO.put(FisioParserConstants.TRUE, new InfoToken(CategoriaToken.PR, "PR_18"));
        MAPEO.put(FisioParserConstants.FALSE, new InfoToken(CategoriaToken.PR, "PR_19"));
        MAPEO.put(FisioParserConstants.TYPE, new InfoToken(CategoriaToken.PR, "PR_20"));
        MAPEO.put(FisioParserConstants.CONST, new InfoToken(CategoriaToken.PR, "PR_21"));
        MAPEO.put(FisioParserConstants.STATIC, new InfoToken(CategoriaToken.PR, "PR_22"));
        MAPEO.put(FisioParserConstants.AS, new InfoToken(CategoriaToken.PR, "PR_23"));
        
        // Nuevas Palabras Reservadas (Rust 100%)
        MAPEO.put(FisioParserConstants.MOD_KW, new InfoToken(CategoriaToken.PR, "PR_24"));
        MAPEO.put(FisioParserConstants.BREAK, new InfoToken(CategoriaToken.PR, "PR_25"));
        MAPEO.put(FisioParserConstants.CONTINUE, new InfoToken(CategoriaToken.PR, "PR_26"));
        MAPEO.put(FisioParserConstants.WHERE, new InfoToken(CategoriaToken.PR, "PR_27"));
        MAPEO.put(FisioParserConstants.REF, new InfoToken(CategoriaToken.PR, "PR_28"));
        MAPEO.put(FisioParserConstants.UNSAFE, new InfoToken(CategoriaToken.PR, "PR_29"));

        // ==========================================
        // Operadores Matemáticos (OPM)
        // ==========================================
        MAPEO.put(FisioParserConstants.MAS, new InfoToken(CategoriaToken.OPM, "OPM_01"));
        MAPEO.put(FisioParserConstants.MENOS, new InfoToken(CategoriaToken.OPM, "OPM_02"));
        MAPEO.put(FisioParserConstants.MULT, new InfoToken(CategoriaToken.OPM, "OPM_03"));
        MAPEO.put(FisioParserConstants.DIV, new InfoToken(CategoriaToken.OPM, "OPM_04"));
        MAPEO.put(FisioParserConstants.MOD, new InfoToken(CategoriaToken.OPM, "OPM_05"));

        // ==========================================
        // Operadores Relacionales (OPR)
        // ==========================================
        MAPEO.put(FisioParserConstants.IGUAL_QUE, new InfoToken(CategoriaToken.OPR, "OPR_01"));
        MAPEO.put(FisioParserConstants.DIFERENTE, new InfoToken(CategoriaToken.OPR, "OPR_02"));
        MAPEO.put(FisioParserConstants.MENOR_QUE, new InfoToken(CategoriaToken.OPR, "OPR_03"));
        MAPEO.put(FisioParserConstants.MAYOR_QUE, new InfoToken(CategoriaToken.OPR, "OPR_04"));
        MAPEO.put(FisioParserConstants.MENOR_IGUAL, new InfoToken(CategoriaToken.OPR, "OPR_05"));
        MAPEO.put(FisioParserConstants.MAYOR_IGUAL, new InfoToken(CategoriaToken.OPR, "OPR_06"));

        // ==========================================
        // Signos (SIG)
        // ==========================================
        MAPEO.put(FisioParserConstants.PAREN_IZQ, new InfoToken(CategoriaToken.SIG, "SIG_01"));
        MAPEO.put(FisioParserConstants.PAREN_DER, new InfoToken(CategoriaToken.SIG, "SIG_02"));
        MAPEO.put(FisioParserConstants.CORCHETE_IZQ, new InfoToken(CategoriaToken.SIG, "SIG_03"));
        MAPEO.put(FisioParserConstants.CORCHETE_DER, new InfoToken(CategoriaToken.SIG, "SIG_04"));
        MAPEO.put(FisioParserConstants.LLAVE_IZQ, new InfoToken(CategoriaToken.SIG, "SIG_05"));
        MAPEO.put(FisioParserConstants.LLAVE_DER, new InfoToken(CategoriaToken.SIG, "SIG_06"));
        MAPEO.put(FisioParserConstants.COMA, new InfoToken(CategoriaToken.SIG, "SIG_07"));
        MAPEO.put(FisioParserConstants.PUNTO, new InfoToken(CategoriaToken.SIG, "SIG_08"));
        MAPEO.put(FisioParserConstants.PUNTO_COMA, new InfoToken(CategoriaToken.SIG, "SIG_09"));
        MAPEO.put(FisioParserConstants.DOS_PUNTOS, new InfoToken(CategoriaToken.SIG, "SIG_10"));
        MAPEO.put(FisioParserConstants.ASIGNACION, new InfoToken(CategoriaToken.SIG, "SIG_11"));
        MAPEO.put(FisioParserConstants.NEGACION, new InfoToken(CategoriaToken.SIG, "SIG_12"));
        MAPEO.put(FisioParserConstants.AMPERSAND, new InfoToken(CategoriaToken.SIG, "SIG_13"));
        MAPEO.put(FisioParserConstants.PIPE, new InfoToken(CategoriaToken.SIG, "SIG_14"));
        // SIG_15 está libre, respetamos tu numeración original
        MAPEO.put(FisioParserConstants.FLECHA, new InfoToken(CategoriaToken.SIG, "SIG_16"));
        MAPEO.put(FisioParserConstants.FLECHA_GRUESA, new InfoToken(CategoriaToken.SIG, "SIG_17"));
        MAPEO.put(FisioParserConstants.DOBLE_DOS_PUNTOS, new InfoToken(CategoriaToken.SIG, "SIG_18"));
        MAPEO.put(FisioParserConstants.AND, new InfoToken(CategoriaToken.SIG, "SIG_19"));
        MAPEO.put(FisioParserConstants.OR, new InfoToken(CategoriaToken.SIG, "SIG_20"));

        // Asignaciones compuestas y rangos (Tus agregados)
        MAPEO.put(FisioParserConstants.MAS_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_21"));
        MAPEO.put(FisioParserConstants.MENOS_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_22"));
        MAPEO.put(FisioParserConstants.MULT_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_23"));
        MAPEO.put(FisioParserConstants.DIV_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_24"));
        MAPEO.put(FisioParserConstants.MOD_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_25"));
        MAPEO.put(FisioParserConstants.RANGO, new InfoToken(CategoriaToken.SIG, "SIG_26"));

        // Nuevos Signos y Operadores (Rust 100%)
        MAPEO.put(FisioParserConstants.SHIFT_IZQ_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_27"));
        MAPEO.put(FisioParserConstants.SHIFT_DER_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_28"));
        MAPEO.put(FisioParserConstants.AND_BIT_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_29"));
        MAPEO.put(FisioParserConstants.OR_BIT_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_30"));
        MAPEO.put(FisioParserConstants.XOR_BIT_IGUAL, new InfoToken(CategoriaToken.SIG, "SIG_31"));
        MAPEO.put(FisioParserConstants.RANGO_INCLUSIVO, new InfoToken(CategoriaToken.SIG, "SIG_32"));
        MAPEO.put(FisioParserConstants.SHIFT_IZQ, new InfoToken(CategoriaToken.SIG, "SIG_33"));
        MAPEO.put(FisioParserConstants.SHIFT_DER, new InfoToken(CategoriaToken.SIG, "SIG_34"));
        MAPEO.put(FisioParserConstants.CARET, new InfoToken(CategoriaToken.SIG, "SIG_35"));
        MAPEO.put(FisioParserConstants.ARROBA, new InfoToken(CategoriaToken.SIG, "SIG_36"));
        MAPEO.put(FisioParserConstants.HASH, new InfoToken(CategoriaToken.SIG, "SIG_37"));
        MAPEO.put(FisioParserConstants.INTERROGACION, new InfoToken(CategoriaToken.SIG, "SIG_38"));

        // ==========================================
        // Tipos Dinámicos y Errores
        // ==========================================
        MAPEO.put(FisioParserConstants.ID, new InfoToken(CategoriaToken.ID, "ID"));
        MAPEO.put(FisioParserConstants.NUM, new InfoToken(CategoriaToken.NUM, "NUM"));
        MAPEO.put(FisioParserConstants.STRING, new InfoToken(CategoriaToken.STRING, "STRING"));
        MAPEO.put(FisioParserConstants.ERROR, new InfoToken(CategoriaToken.ERROR, "ERROR"));
    }

    public static InfoToken obtenerInfo(int kind) {
        // Usamos CategoriaToken.ERROR como respaldo si el token no está mapeado
        return MAPEO.getOrDefault(kind, new InfoToken(CategoriaToken.ERROR, "DESCONOCIDO"));
    }
}
