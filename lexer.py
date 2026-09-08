"""
Analizador lexico lineal para Rust.
"""

from __future__ import annotations

from token_types import (
    ALFABETO_VALIDO,
    OPERADORES_MATEMATICOS,
    OPERADORES_RELACIONALES,
    PALABRAS_RESERVADAS,
    SIGNOS,
    ErrorLexico,
    TipoToken,
    Token,
)


class Lexer:
    def __init__(self, codigo: str):
        self.codigo = codigo
        self.pos = 0
        self.linea = 1
        self.columna = 1
        self.tokens: list[Token] = []
        self.errores: list[ErrorLexico] = []

    def analizar(self) -> tuple[list[Token], list[ErrorLexico]]:
        while not self._fin():
            actual = self._actual()
            siguiente = self._siguiente()

            # Ignorar espacios
            if actual in " \t":
                self._avanzar()
                continue

            # Saltos de linea
            if actual in "\r\n":
                self._avanzar_linea()
                continue

            # Comentarios de linea (//)
            if actual == "/" and siguiente == "/":
                self._ignorar_comentario_linea()
                continue

            # Comentarios de bloque (/* */)
            if actual == "/" and siguiente == "*":
                self._ignorar_comentario_bloque()
                continue

            # Numeros
            if actual.isdigit():
                self._leer_numero()
                continue

            # Identificadores y Palabras Reservadas
            if self._es_letra(actual):
                self._leer_identificador_o_reservada()
                continue

            # Decimales sin entero (.5)
            if actual == "." and siguiente.isdigit():
                self._leer_decimal_sin_entero()
                continue

            # Prioridad 1: Tokens compuestos de 2 caracteres (==, ->, ::, etc.)
            lexema_doble = actual + siguiente
            if lexema_doble in OPERADORES_RELACIONALES:
                self._agregar_token(lexema_doble, OPERADORES_RELACIONALES[lexema_doble])
                self._avanzar()
                self._avanzar()
                continue

            if lexema_doble in SIGNOS:
                self._agregar_token(lexema_doble, SIGNOS[lexema_doble])
                self._avanzar()
                self._avanzar()
                continue

            # Prioridad 2: Tokens de 1 caracter (=, <, +, ;, etc.)
            if actual in OPERADORES_RELACIONALES:
                self._agregar_token(actual, OPERADORES_RELACIONALES[actual])
                self._avanzar()
                continue

            if actual in OPERADORES_MATEMATICOS:
                self._agregar_token(actual, OPERADORES_MATEMATICOS[actual])
                self._avanzar()
                continue

            # Detectar cadenas de texto entre comillas dobles (Debe ir antes de los signos)
            if actual == '"':
                self._leer_cadena_texto()
                continue

            if actual in SIGNOS:
                self._agregar_token(actual, SIGNOS[actual])
                self._avanzar()
                continue

            # Si no encaja en nada, es un error
            self._leer_caracter_invalido()

        return self.tokens, self.errores

    def _ignorar_comentario_linea(self) -> None:
        while not self._fin() and self._actual() not in "\r\n":
            self._avanzar()

    def _ignorar_comentario_bloque(self) -> None:
        self._avanzar() # Consume /
        self._avanzar() # Consume *
        while not self._fin():
            if self._actual() == "*" and self._siguiente() == "/":
                self._avanzar() # Consume *
                self._avanzar() # Consume /
                break
            
            if self._actual() in "\r\n":
                self._avanzar_linea()
            else:
                self._avanzar()

    def _leer_identificador_o_reservada(self) -> None:
        linea_inicio = self.linea
        columna_inicio = self.columna
        lexema = self._consumir_mientras(
            lambda char: self._es_letra(char) or char.isdigit()
        )

        if self._hay_continuacion_mal_formada():
            lexema += self._consumir_fragmento_mal_formado()
            self._agregar_error(
                lexema,
                "Caracter fuera del alfabeto Rust",
                linea_inicio,
                columna_inicio,
            )
            return

        if lexema in PALABRAS_RESERVADAS:
            tipo, id_tok = PALABRAS_RESERVADAS[lexema]
        else:
            tipo, id_tok = TipoToken.ID, "ID"

        self.tokens.append(Token(lexema, tipo, id_tok, linea_inicio, columna_inicio))

    def _leer_numero(self) -> None:
        linea_inicio = self.linea
        columna_inicio = self.columna
        lexema = self._consumir_mientras(str.isdigit)
        tiene_decimal = False
        mal_formado = False

        if self._actual() == ".":
            tiene_decimal = True
            lexema += self._avanzar()

            if not self._actual().isdigit():
                mal_formado = True
            else:
                lexema += self._consumir_mientras(str.isdigit)

        if self._actual() == ".":
            mal_formado = True
            lexema += self._consumir_fragmento_mal_formado()
        elif self._hay_continuacion_mal_formada(punto_es_delimitador=False):
            mal_formado = True
            lexema += self._consumir_fragmento_mal_formado()

        if (
            not mal_formado
            and not tiene_decimal
            and len(lexema) > 1
            and lexema.startswith("0")
        ):
            mal_formado = True

        if mal_formado:
            mensaje = "Numero mal formado"
            if self._contiene_caracter_fuera_alfabeto(lexema):
                mensaje = "Caracter fuera del alfabeto Rust"

            self._agregar_error(
                lexema,
                mensaje,
                linea_inicio,
                columna_inicio,
            )
            return

        self.tokens.append(Token(lexema, TipoToken.NUM, "NUM", linea_inicio, columna_inicio))

    def _leer_decimal_sin_entero(self) -> None:
        linea_inicio = self.linea
        columna_inicio = self.columna
        lexema = self._avanzar()
        lexema += self._consumir_mientras(str.isdigit)

        if self._hay_continuacion_mal_formada(punto_es_delimitador=False):
            lexema += self._consumir_fragmento_mal_formado()

        self._agregar_error(
            lexema,
            "Numero decimal sin parte entera",
            linea_inicio,
            columna_inicio,
        )

    def _leer_caracter_invalido(self) -> None:
        linea_inicio = self.linea
        columna_inicio = self.columna
        lexema = self._avanzar()
        lexema += self._consumir_fragmento_mal_formado()
        mensaje = "Caracter fuera del alfabeto Rust"

        if not self._contiene_caracter_fuera_alfabeto(lexema):
            mensaje = "Caracter no reconocido en este contexto"

        self._agregar_error(lexema, mensaje, linea_inicio, columna_inicio)

    def _agregar_error(
        self,
        lexema: str,
        mensaje: str,
        linea: int,
        columna: int,
    ) -> None:
        self.errores.append(ErrorLexico(lexema, mensaje, linea, columna))
        self.tokens.append(Token(lexema, TipoToken.ERROR, "ERROR", linea, columna))

    def _consumir_fragmento_mal_formado(self) -> str:
        return self._consumir_mientras(
            lambda char: not self._es_delimitador_lexema(
                char,
                punto_es_delimitador=False,
            )
        )

    def _consumir_mientras(self, condicion) -> str:
        lexema = ""
        while not self._fin() and condicion(self._actual()):
            lexema += self._avanzar()
        return lexema

    def _agregar_token(self, lexema: str, definicion: tuple[str, str]) -> None:
        tipo, id_tok = definicion
        self.tokens.append(Token(lexema, tipo, id_tok, self.linea, self.columna))

    def _contiene_caracter_fuera_alfabeto(self, lexema: str) -> bool:
        return any(char not in ALFABETO_VALIDO for char in lexema)

    def _hay_continuacion_mal_formada(
        self,
        *,
        punto_es_delimitador: bool = True,
    ) -> bool:
        return not self._es_delimitador_lexema(
            self._actual(),
            punto_es_delimitador=punto_es_delimitador,
        )

    def _es_delimitador_lexema(
        self,
        char: str,
        *,
        punto_es_delimitador: bool = True,
    ) -> bool:
        if char == "\0" or char in " \t\r\n":
            return True

        if char in OPERADORES_MATEMATICOS or char in "=<>!&|":
            return True

        if char in "()[]{},.;:":
            return punto_es_delimitador or char != "."

        return False

    def _leer_cadena_texto(self) -> None:
        linea_inicio = self.linea
        columna_inicio = self.columna
        lexema = self._avanzar()

        while not self._fin() and self._actual() != '"' and self._actual() not in "\r\n":
            lexema += self._avanzar()

        if self._actual() == '"':
            lexema += self._avanzar()  # Consume la comilla de cierre (")
            self.tokens.append(Token(lexema, TipoToken.STRING, "STRING", linea_inicio, columna_inicio))
        else:
            self._agregar_error(lexema, "Cadena de texto sin cerrar", linea_inicio, columna_inicio)

    def _actual(self) -> str:
        if self._fin():
            return "\0"
        return self.codigo[self.pos]

    def _siguiente(self) -> str:
        indice = self.pos + 1
        if indice >= len(self.codigo):
            return "\0"
        return self.codigo[indice]

    def _avanzar(self) -> str:
        char = self.codigo[self.pos]
        self.pos += 1
        self.columna += 1
        return char

    def _avanzar_linea(self) -> None:
        if self._actual() == "\r" and self._siguiente() == "\n":
            self.pos += 2
        else:
            self.pos += 1

        self.linea += 1
        self.columna = 1

    def _fin(self) -> bool:
        return self.pos >= len(self.codigo)

    @staticmethod
    def _es_letra(char: str) -> bool:
        return ("a" <= char <= "z") or ("A" <= char <= "Z") or char == "_"