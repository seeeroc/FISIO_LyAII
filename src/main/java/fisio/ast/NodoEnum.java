package fisio.ast;

import fisio.token.TokenFisio;
import java.util.List;

/** Definición de un enum con variantes unitarias, posicionales o con campos nombrados. */
public record NodoEnum(int linea, int columna, boolean publico, TokenFisio nombre,
        List<Variante> variantes) implements NodoAST {
    public NodoEnum {
        variantes = List.copyOf(variantes);
    }

    public enum Forma { UNITARIA, TUPLA, ESTRUCTURA }

    public record Variante(TokenFisio nombre, List<TokenFisio> tipos, List<Campo> campos, Forma forma) {
        public Variante {
            tipos = List.copyOf(tipos);
            campos = List.copyOf(campos);
            if (!tipos.isEmpty() && !campos.isEmpty())
                throw new IllegalArgumentException("Una variante no puede ser posicional y nombrada a la vez");
            if (forma == Forma.TUPLA && !campos.isEmpty() || forma == Forma.ESTRUCTURA && !tipos.isEmpty())
                throw new IllegalArgumentException("Los datos no corresponden con la forma de la variante");
        }

        public String lexema() {
            return nombre.lexema();
        }
    }

    public record Campo(boolean publico, TokenFisio nombre, TokenFisio tipo) {}
}
