package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Patrón reutilizable por match, if let y while let. */
public record PatronVariante(int linea, int columna, boolean comodin, TokenFisio tipo,
        TokenFisio variante, List<TokenFisio> vinculaciones, List<Elemento> elementos,
        List<NodoMatch.VinculacionCampo> campos, NodoMatch.FormaPatron forma, boolean resto) {
    public PatronVariante {
        vinculaciones = List.copyOf(vinculaciones);
        elementos = elementos.isEmpty() && !vinculaciones.isEmpty()
            ? vinculaciones.stream().map(Elemento::vinculacion).toList()
            : List.copyOf(elementos);
        campos = List.copyOf(campos);
    }

    public static PatronVariante desde(NodoMatch.Brazo brazo) {
        return new PatronVariante(brazo.linea(), brazo.columna(), brazo.comodin(), brazo.tipo(),
            brazo.variante(), brazo.vinculaciones(), brazo.elementos(), brazo.campos(),
            brazo.forma(), brazo.resto());
    }

    /** Indica que algún dato se filtra mediante otro patrón de variante. */
    public boolean tieneAnidados() {
        return elementos.stream().anyMatch(Elemento::esAnidado)
            || campos.stream().anyMatch(NodoMatch.VinculacionCampo::esAnidado);
    }

    /** Elemento posicional: exactamente una vinculación o un patrón anidado. */
    public record Elemento(TokenFisio nombre, PatronVariante patron) {
        public Elemento {
            if ((nombre == null) == (patron == null)) {
                throw new IllegalArgumentException("Un elemento debe ser una vinculación o un patrón anidado");
            }
        }

        public static Elemento vinculacion(TokenFisio nombre) {
            return new Elemento(nombre, null);
        }

        public static Elemento anidado(PatronVariante patron) {
            return new Elemento(null, patron);
        }

        public boolean esAnidado() {
            return patron != null;
        }
    }
}
