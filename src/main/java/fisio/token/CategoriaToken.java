package fisio.token;

/** Categorías emitidas por el analizador léxico, incluido el marcador EOF. */
public enum CategoriaToken {
    PR,
    OPM,
    OPR,
    SIG,
    ID,
    NUM,
    STRING,
    ERROR,
    EOF
}
