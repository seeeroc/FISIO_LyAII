package fisio.ast;

import java.util.List;

import fisio.token.TokenFisio;

/**
 * NodoFuncion(NodoAST): nombre: Token, parametros: list[NodoParametroFuncion],
 * tipoRetorno: Token opcional (null si no se anoto "-> tipo"), cuerpo: NodoBloque.
 *
 * Fase 2 (fn nombre() { ... }); ampliado en la revision general Rust-like
 * para conservar los parametros (antes se parseaban y se descartaban) y el
 * tipo de retorno opcional ("-> i32"). Sin validacion semantica de tipos ni
 * de aridad.
 */
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
