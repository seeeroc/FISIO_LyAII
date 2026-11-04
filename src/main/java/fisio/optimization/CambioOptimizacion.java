package fisio.optimization;

import java.util.Objects;

public record CambioOptimizacion(
        int numero,
        TecnicaOptimizacion tecnica,
        int lineaOriginalInicio,
        int lineaOriginalFin,
        int lineaOptimizadaInicio,
        int lineaOptimizadaFin,
        String antes,
        String despues,
        String explicacion) {

    public CambioOptimizacion {
        if (numero < 1) throw new IllegalArgumentException("El número de cambio debe ser positivo");
        Objects.requireNonNull(tecnica, "tecnica");
        antes = Objects.requireNonNullElse(antes, "");
        despues = Objects.requireNonNullElse(despues, "");
        explicacion = Objects.requireNonNullElse(explicacion, "");
    }
}
