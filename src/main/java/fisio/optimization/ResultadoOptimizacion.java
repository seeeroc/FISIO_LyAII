package fisio.optimization;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ResultadoOptimizacion(
        boolean exitoso,
        String codigoOriginal,
        String codigoOptimizado,
        List<CambioOptimizacion> cambios,
        MetricasOptimizacion metricas,
        Map<Integer, Integer> lineaOriginalPorOptimizada,
        String motivo) {

    public ResultadoOptimizacion {
        codigoOriginal = Objects.requireNonNullElse(codigoOriginal, "");
        codigoOptimizado = Objects.requireNonNullElse(codigoOptimizado, codigoOriginal);
        cambios = List.copyOf(cambios == null ? List.of() : cambios);
        lineaOriginalPorOptimizada = Map.copyOf(
            lineaOriginalPorOptimizada == null ? Map.of() : lineaOriginalPorOptimizada);
        motivo = Objects.requireNonNullElse(motivo, "");
    }

    public String reporteTexto() {
        StringBuilder reporte = new StringBuilder("REPORTE DE OPTIMIZACIÓN FISIO\n\n");
        reporte.append("Estado: ").append(exitoso ? "completado" : "bloqueado").append('\n');
        if (!motivo.isBlank()) reporte.append("Detalle: ").append(motivo).append('\n');
        if (metricas != null) {
            reporte.append("\nMÉTRICAS\n")
                .append("Líneas: ").append(metricas.lineasOriginales()).append(" -> ")
                    .append(metricas.lineasOptimizadas()).append('\n')
                .append("Caracteres: ").append(metricas.caracteresOriginales()).append(" -> ")
                    .append(metricas.caracteresOptimizados()).append('\n')
                .append("Bytes UTF-8: ").append(metricas.bytesOriginales()).append(" -> ")
                    .append(metricas.bytesOptimizados()).append('\n')
                .append("Reducción: ").append(String.format(java.util.Locale.ROOT, "%.2f%%",
                    metricas.porcentajeReduccion())).append('\n')
                .append("Cuádruplos: ").append(metricas.cuadruplosOriginales()).append(" -> ")
                    .append(metricas.cuadruplosOptimizados()).append('\n')
                .append("Tiempo del optimizador: ").append(String.format(java.util.Locale.ROOT, "%.3f",
                    metricas.tiempoNanosegundos() / 1_000_000.0)).append(" ms\n")
                .append("\nTÉCNICAS APLICADAS\n");
            if (metricas.cambiosPorTecnica().isEmpty()) {
                reporte.append("Ninguna transformación segura.\n");
            } else {
                metricas.cambiosPorTecnica().forEach((tecnica, cantidad) -> reporte.append("- ")
                    .append(tecnica.descripcion()).append(": ").append(cantidad).append('\n'));
            }
        }
        reporte.append("\nCAMBIOS (total: ").append(cambios.size()).append(")\n");
        for (CambioOptimizacion cambio : cambios) {
            reporte.append("\nCambio ").append(cambio.numero()).append(" — ")
                .append(cambio.tecnica().descripcion()).append('\n')
                .append("Línea original: ").append(cambio.lineaOriginalInicio());
            if (cambio.lineaOriginalFin() != cambio.lineaOriginalInicio())
                reporte.append('-').append(cambio.lineaOriginalFin());
            reporte.append("\nAntes:\n").append(cambio.antes().isBlank() ? "∅" : cambio.antes())
                .append("\nDespués:\n").append(cambio.despues().isBlank() ? "∅" : cambio.despues())
                .append("\nExplicación: ").append(cambio.explicacion()).append('\n');
        }
        return reporte.toString();
    }
}
