package fisio.ci;

import java.util.List;

/** Todas las representaciones generadas para una raíz aritmética del AST. */
public record ExpresionIntermedia(
        String contexto,
        int linea,
        int columna,
        String infija,
        String postfija,
        String prefija,
        List<Cuadruplo> cuadruplos,
        List<PasoResolucion> pasos,
        String resultadoFinal,
        String detalleResultado) {

    public ExpresionIntermedia {
        cuadruplos = List.copyOf(cuadruplos);
        pasos = List.copyOf(pasos);
    }

    public boolean resultadoDisponible() {
        return resultadoFinal != null;
    }
}
