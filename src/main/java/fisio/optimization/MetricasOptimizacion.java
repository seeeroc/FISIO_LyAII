package fisio.optimization;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record MetricasOptimizacion(
        int lineasOriginales,
        int lineasOptimizadas,
        int caracteresOriginales,
        int caracteresOptimizados,
        long bytesOriginales,
        long bytesOptimizados,
        int cuadruplosOriginales,
        int cuadruplosOptimizados,
        long tiempoNanosegundos,
        Map<TecnicaOptimizacion, Integer> cambiosPorTecnica) {

    public MetricasOptimizacion {
        EnumMap<TecnicaOptimizacion, Integer> copia = new EnumMap<>(TecnicaOptimizacion.class);
        if (cambiosPorTecnica != null) copia.putAll(cambiosPorTecnica);
        cambiosPorTecnica = Collections.unmodifiableMap(copia);
    }

    public long reduccionBytes() {
        return bytesOriginales - bytesOptimizados;
    }

    public double porcentajeReduccion() {
        return bytesOriginales == 0 ? 0.0 : reduccionBytes() * 100.0 / bytesOriginales;
    }

}
