"""
Definiciones de tokens para el analizador lexico del lenguaje Rust (FISIO modificado).
"""

from __future__ import annotations

from dataclasses import dataclass
import string


class TipoToken:
    """Tipos de token usados por la GUI y el analizador lexico."""
    PR = "PR"
    OPM = "OPM"
    OPR = "OPR"
    SIG = "SIG"
    ID = "ID"
    NUM = "NUM"
    STRING = "STRING"
    ERROR = "ERROR"


PALABRAS_RESERVADAS: dict[str, tuple[str, str]] = {
    "fn": (TipoToken.PR, "PR_01"),
    "let": (TipoToken.PR, "PR_02"),
    "mut": (TipoToken.PR, "PR_03"),
    "if": (TipoToken.PR, "PR_04"),
    "else": (TipoToken.PR, "PR_05"),
    "while": (TipoToken.PR, "PR_06"),
    "for": (TipoToken.PR, "PR_07"),
    "in": (TipoToken.PR, "PR_08"),
    "loop": (TipoToken.PR, "PR_09"),
    "match": (TipoToken.PR, "PR_10"),
    "return": (TipoToken.PR, "PR_11"),
    "struct": (TipoToken.PR, "PR_12"),
    "enum": (TipoToken.PR, "PR_13"),
    "impl": (TipoToken.PR, "PR_14"),
    "trait": (TipoToken.PR, "PR_15"),
    "pub": (TipoToken.PR, "PR_16"),
    "use": (TipoToken.PR, "PR_17"),
    "true": (TipoToken.PR, "PR_18"),
    "false": (TipoToken.PR, "PR_19"),
    "type": (TipoToken.PR, "PR_20"),
    "const": (TipoToken.PR, "PR_21"),
    "static": (TipoToken.PR, "PR_22"),
    "as": (TipoToken.PR, "PR_23"),
}

OPERADORES_MATEMATICOS: dict[str, tuple[str, str]] = {
    "+": (TipoToken.OPM, "OPM_01"),
    "-": (TipoToken.OPM, "OPM_02"),
    "*": (TipoToken.OPM, "OPM_03"),
    "/": (TipoToken.OPM, "OPM_04"),
    "%": (TipoToken.OPM, "OPM_05"),
}

OPERADORES_RELACIONALES: dict[str, tuple[str, str]] = {
    "==": (TipoToken.OPR, "OPR_01"),
    "!=": (TipoToken.OPR, "OPR_02"),
    "<": (TipoToken.OPR, "OPR_03"),
    ">": (TipoToken.OPR, "OPR_04"),
    "<=": (TipoToken.OPR, "OPR_05"),
    ">=": (TipoToken.OPR, "OPR_06"),
}

SIGNOS: dict[str, tuple[str, str]] = {
    "(": (TipoToken.SIG, "SIG_01"),
    ")": (TipoToken.SIG, "SIG_02"),
    "[": (TipoToken.SIG, "SIG_03"),
    "]": (TipoToken.SIG, "SIG_04"),
    "{": (TipoToken.SIG, "SIG_05"),
    "}": (TipoToken.SIG, "SIG_06"),
    ",": (TipoToken.SIG, "SIG_07"),
    ".": (TipoToken.SIG, "SIG_08"),
    ";": (TipoToken.SIG, "SIG_09"),
    ":": (TipoToken.SIG, "SIG_10"),
    "=": (TipoToken.SIG, "SIG_11"), 
    "!": (TipoToken.SIG, "SIG_12"), 
    "&": (TipoToken.SIG, "SIG_13"), 
    "|": (TipoToken.SIG, "SIG_14"), 
    '"': (TipoToken.SIG, "SIG_15"),
    "->": (TipoToken.SIG, "SIG_16"),
    "=>": (TipoToken.SIG, "SIG_17"),
    "::": (TipoToken.SIG, "SIG_18"),
    "&&": (TipoToken.SIG, "SIG_19"),
    "||": (TipoToken.SIG, "SIG_20"),
}

ALFABETO_VALIDO: set[str] = set(
    string.ascii_letters
    + string.digits
    + " \t\r\n_"
    + "".join(OPERADORES_MATEMATICOS)
    + "".join(set(char for lexema in SIGNOS for char in lexema))
    + "".join(set(char for lexema in OPERADORES_RELACIONALES for char in lexema))
)

COLORES_GUI: dict[str, str] = {
    "bg_main": "#1e1e2e",
    "bg_editor": "#181825",
    "bg_panel": "#11111b",
    "bg_toolbar": "#313244",
    "bg_status": "#181825",
    "bg_row_alt": "#1e1e2e",
    "border": "#45475a",
    "fg_main": "#cdd6f4",
    "fg_dim": "#a6adc8",
    "fg_lineno": "#6c7086",
    "accent": "#89b4fa",
    "ok_green": "#a6e3a1",
    "col_PR": "#cba6f7",
    "col_OPM": "#f9e2af",
    "col_OPR": "#fab387",
    "col_SIG": "#89dceb",
    "col_ID": "#cdd6f4",
    "col_NUM": "#a6e3a1",
    "col_ERROR": "#f38ba8",
}


@dataclass(frozen=True, slots=True)
class Token:
    lexema: str
    tipo: str
    id_tok: str
    linea: int
    columna: int


@dataclass(frozen=True, slots=True)
class ErrorLexico:
    lexema: str
    mensaje: str
    linea: int
    columna: int
