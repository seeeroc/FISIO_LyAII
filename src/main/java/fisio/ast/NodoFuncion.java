package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/** Declaración de función con parámetros, retorno opcional y cuerpo. */
public record NodoFuncion(
        int linea,
        int columna,
        TokenFisio nombre,
        List<NodoParametroFuncion> parametros,
        TokenFisio tipoRetorno,
        NodoBloque cuerpo)
        implements NodoAST {

    public NodoFuncion {
        parametros = List.copyOf(parametros);
    }
}
