package fisio.gui;

/** Estados de presentación compartidos por todos los módulos. */
enum EstadoAnalisis {
    NO_EJECUTADO("No ejecutado"), CORRECTO("Correcto"), CON_ERRORES("Con errores"),
    BLOQUEADO_POR_FASE_ANTERIOR("Bloqueado por fase anterior"),
    DESACTUALIZADO("Desactualizado"), EN_EJECUCION("En ejecución"), ERROR_INTERNO("Error interno");

    final String texto;
    EstadoAnalisis(String texto) { this.texto = texto; }
}
