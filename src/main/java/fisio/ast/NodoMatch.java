package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Expresión match con patrones de variante, vinculaciones, guardas o comodín. */
public record NodoMatch(int linea, int columna, NodoAST expresion, List<Brazo> brazos) implements NodoAST {
    public NodoMatch {
        brazos = List.copyOf(brazos);
    }

    public enum FormaPatron { COMODIN, UNITARIO, TUPLA, ESTRUCTURA }

    public record Brazo(int linea, int columna, boolean comodin, TokenFisio tipo,
                        TokenFisio variante, List<TokenFisio> vinculaciones,
                        List<PatronVariante.Elemento> elementos,
                        List<VinculacionCampo> campos, FormaPatron forma, boolean resto,
                        NodoAST guarda, NodoAST resultado) {
        public Brazo {
            vinculaciones = List.copyOf(vinculaciones);
            elementos = elementos.isEmpty() && !vinculaciones.isEmpty()
                ? vinculaciones.stream().map(PatronVariante.Elemento::vinculacion).toList()
                : List.copyOf(elementos);
            campos = List.copyOf(campos);
            if (!elementos.isEmpty() && !campos.isEmpty())
                throw new IllegalArgumentException("Un patrón no puede ser posicional y nombrado a la vez");
        }

    }

    /** El campo contiene una vinculación local o un patrón anidado. */
    public record VinculacionCampo(TokenFisio campo, TokenFisio vinculacion, PatronVariante patron) {
        public VinculacionCampo {
            if ((vinculacion == null) == (patron == null)) {
                throw new IllegalArgumentException("Un campo debe tener vinculación o patrón anidado");
            }
        }

        public VinculacionCampo(TokenFisio campo, TokenFisio vinculacion) {
            this(campo, vinculacion, null);
        }

        public VinculacionCampo(TokenFisio campo, PatronVariante patron) {
            this(campo, null, patron);
        }

        public boolean esAnidado() {
            return patron != null;
        }
    }
}
