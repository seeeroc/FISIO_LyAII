"""
Parser descendente recursivo para el subconjunto de Rust.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import ClassVar, Optional

from token_types import TipoToken, Token


@dataclass(frozen=True, slots=True)
class NodoAST:
    """Clase base para todos los nodos del AST."""
    linea: int
    columna: int


@dataclass(frozen=True, slots=True)
class NodoPrograma(NodoAST):
    funciones: list[NodoAST]


@dataclass(frozen=True, slots=True)
class NodoBloque(NodoAST):
    sentencias: list[NodoAST]


@dataclass(frozen=True, slots=True)
class NodoFuncion(NodoAST):
    nombre: Token
    parametros: list[Token]
    cuerpo: NodoBloque


@dataclass(frozen=True, slots=True)
class NodoDeclaracion(NodoAST):
    es_mut: bool
    identificador: Token
    tipo_dato: Optional[Token]
    expresion: NodoAST


@dataclass(frozen=True, slots=True)
class NodoAsignacion(NodoAST):
    identificador: Token
    expresion: NodoAST


@dataclass(frozen=True, slots=True)
class NodoIf(NodoAST):
    condicion: NodoAST
    bloque_verdadero: NodoBloque
    bloque_falso: Optional[NodoBloque]


@dataclass(frozen=True, slots=True)
class NodoWhile(NodoAST):
    condicion: NodoAST
    cuerpo: NodoBloque


@dataclass(frozen=True, slots=True)
class NodoLlamadaFuncion(NodoAST):
    nombre: Token
    argumentos: list[NodoAST]
    es_macro: bool


@dataclass(frozen=True, slots=True)
class NodoOperacion(NodoAST):
    operador: Token
    izquierda: NodoAST
    derecha: NodoAST


@dataclass(frozen=True, slots=True)
class NodoNumero(NodoAST):
    token: Token


@dataclass(frozen=True, slots=True)
class NodoIdentificador(NodoAST):
    token: Token


@dataclass(frozen=True, slots=True)
class ErrorSintactico:
    token: Token
    mensaje: str
    linea: int
    columna: int


class Parser:
    OP_RELACIONALES: ClassVar[frozenset[str]] = frozenset(
        {"==", "!=", "<", ">", "<=", ">="}
    )
    SINCRONIZACION: ClassVar[frozenset[str]] = frozenset({";", "}", "fn", "let", "if", "while"})

    def __init__(self, tokens: list[Token]):
        self.tokens = tokens
        self.pos = 0
        self.errores: list[ErrorSintactico] = []
        self.eof = self._crear_eof()

    def parse(self) -> tuple[NodoAST, list[ErrorSintactico]]:
        funciones: list[NodoAST] = []

        while not self._fin():
            if self._check_lexema("fn"):
                funcion = self.parse_funcion()
                if funcion:
                    funciones.append(funcion)
            else:
                self._error_actual("Se esperaba la declaracion de una funcion ('fn')")
                self._sincronizar()

        return NodoPrograma(1, 1, funciones), self.errores

    def parse_funcion(self) -> Optional[NodoFuncion]:
        inicio = self._avanzar()  # Consume 'fn'

        if not self._check_tipo(TipoToken.ID):
            self._error_actual("Se esperaba el nombre de la funcion")
            return None
        nombre = self._avanzar()

        if not self._coincidir_lexema("("):
            self._error_actual("Se esperaba '(' despues del nombre de la funcion")
            return None

        # Por ahora simplificamos los parametros ignorandolos en la estructura profunda
        parametros = []
        while not self._fin() and not self._check_lexema(")"):
            if self._check_tipo(TipoToken.ID):
                parametros.append(self._avanzar())
                if self._check_lexema(":"):
                    self._avanzar()
                    if self._check_tipo(TipoToken.ID) or self._check_tipo(TipoToken.PR):
                        self._avanzar() # Consume el tipo
            if not self._coincidir_lexema(","):
                break

        if not self._coincidir_lexema(")"):
            self._error_actual("Se esperaba ')' para cerrar los parametros")
            return None

        cuerpo = self.parse_bloque()
        if cuerpo is None:
            return None

        return NodoFuncion(inicio.linea, inicio.columna, nombre, parametros, cuerpo)

    def parse_bloque(self) -> Optional[NodoBloque]:
        if not self._check_lexema("{"):
            self._error_actual("Se esperaba '{' para iniciar el bloque")
            return None
        
        inicio = self._avanzar()
        sentencias = []

        while not self._fin() and not self._check_lexema("}"):
            sentencia = self.parse_sentencia()
            if sentencia:
                sentencias.append(sentencia)
            else:
                self._sincronizar()

        if not self._coincidir_lexema("}"):
            self._error_actual("Se esperaba '}' para cerrar el bloque")
            return None

        return NodoBloque(inicio.linea, inicio.columna, sentencias)

    def parse_sentencia(self) -> Optional[NodoAST]:
        if self._check_lexema("let"):
            return self.parse_declaracion()
        
        if self._check_lexema("if"):
            return self.parse_if()
            
        if self._check_lexema("while"):
            return self.parse_while()

        if self._check_tipo(TipoToken.ID):
            return self.parse_asignacion_o_llamada()

        self._error_actual("Sentencia no reconocida (se esperaba let, if, while o asignacion)")
        return None

    def parse_declaracion(self) -> Optional[NodoDeclaracion]:
        inicio = self._avanzar() # Consume 'let'
        es_mut = self._coincidir_lexema("mut")

        if not self._check_tipo(TipoToken.ID):
            self._error_actual("Se esperaba un identificador para la variable")
            return None
        identificador = self._avanzar()

        tipo_dato = None
        if self._coincidir_lexema(":"):
            if self._check_tipo(TipoToken.ID) or self._check_tipo(TipoToken.PR):
                tipo_dato = self._avanzar()
            else:
                self._error_actual("Se esperaba un tipo de dato despues de ':'")
                return None

        if not self._coincidir_lexema("="):
            self._error_actual("Se esperaba '=' en la declaracion")
            return None

        expresion = self.parse_expresion()
        if not expresion:
            return None

        if not self._coincidir_lexema(";"):
            self._error_actual("Se esperaba ';' al final de la declaracion")
            return None

        return NodoDeclaracion(inicio.linea, inicio.columna, es_mut, identificador, tipo_dato, expresion)

    def parse_asignacion_o_llamada(self) -> Optional[NodoAST]:
        identificador = self._avanzar()

        # Es una llamada a macro (ej. println!)
        es_macro = False
        if self._coincidir_lexema("!"):
            es_macro = True

        # Es una llamada a funcion / macro
        if self._coincidir_lexema("("):
            argumentos = []
            while not self._fin() and not self._check_lexema(")"):
                arg = self.parse_expresion()
                if arg: argumentos.append(arg)
                if not self._coincidir_lexema(","): break
            
            if not self._coincidir_lexema(")"):
                self._error_actual("Se esperaba ')' al final de la llamada")
                return None
            
            if not self._coincidir_lexema(";"):
                self._error_actual("Se esperaba ';' al final de la instruccion")
                return None
                
            return NodoLlamadaFuncion(identificador.linea, identificador.columna, identificador, argumentos, es_macro)

        # Es una asignacion normal
        if self._coincidir_lexema("="):
            expresion = self.parse_expresion()
            if not expresion:
                return None
            
            if not self._coincidir_lexema(";"):
                self._error_actual("Se esperaba ';' al final de la asignacion")
                return None
                
            return NodoAsignacion(identificador.linea, identificador.columna, identificador, expresion)

        self._error_actual("Se esperaba '=' o '(' despues del identificador")
        return None

    def parse_if(self) -> Optional[NodoIf]:
        inicio = self._avanzar() # Consume 'if'
        
        condicion = self.parse_expresion()
        if not condicion:
            return None
            
        bloque_verdadero = self.parse_bloque()
        if not bloque_verdadero:
            return None
            
        bloque_falso = None
        if self._coincidir_lexema("else"):
            if self._check_lexema("if"):
                # Rust soporta 'else if', lo envolvemos en un bloque para el AST
                bloque_falso = NodoBloque(inicio.linea, inicio.columna, [self.parse_if()])
            else:
                bloque_falso = self.parse_bloque()

        return NodoIf(inicio.linea, inicio.columna, condicion, bloque_verdadero, bloque_falso)

    def parse_while(self) -> Optional[NodoWhile]:
        inicio = self._avanzar() # Consume 'while'
        
        condicion = self.parse_expresion()
        if not condicion:
            return None
            
        cuerpo = self.parse_bloque()
        if not cuerpo:
            return None
            
        return NodoWhile(inicio.linea, inicio.columna, condicion, cuerpo)

    def parse_expresion(self) -> Optional[NodoAST]:
        return self.parse_exp_relacional()

    def parse_exp_relacional(self) -> Optional[NodoAST]:
        izquierda = self.parse_exp_aritmetica()
        if izquierda is None: return None

        if self._check_conjunto(self.OP_RELACIONALES):
            operador = self._avanzar()
            derecha = self.parse_exp_aritmetica()
            if derecha is None: return None
            return NodoOperacion(operador.linea, operador.columna, operador, izquierda, derecha)

        return izquierda

    def parse_exp_aritmetica(self) -> Optional[NodoAST]:
        izquierda = self.parse_termino()
        if izquierda is None: return None

        while self._check_lexema("+", "-"):
            operador = self._avanzar()
            derecha = self.parse_termino()
            if derecha is None: return None
            izquierda = NodoOperacion(operador.linea, operador.columna, operador, izquierda, derecha)

        return izquierda

    def parse_termino(self) -> Optional[NodoAST]:
        izquierda = self.parse_factor()
        if izquierda is None: return None

        while self._check_lexema("*", "/", "%"):
            operador = self._avanzar()
            derecha = self.parse_factor()
            if derecha is None: return None
            izquierda = NodoOperacion(operador.linea, operador.columna, operador, izquierda, derecha)

        return izquierda

    def parse_factor(self) -> Optional[NodoAST]:
        if self._check_tipo(TipoToken.NUM):
            numero = self._avanzar()
            return NodoNumero(numero.linea, numero.columna, numero)

        if self._check_tipo(TipoToken.STRING):
            cadena = self._avanzar()
            return NodoIdentificador(cadena.linea, cadena.columna, cadena)

        if self._check_tipo(TipoToken.ID):
            identificador = self._avanzar()
            return NodoIdentificador(identificador.linea, identificador.columna, identificador)

        if self._coincidir_lexema("("):
            expresion = self.parse_expresion()
            if not self._coincidir_lexema(")"):
                self._error_actual("Se esperaba ')' para cerrar la expresion")
                return None
            return expresion

        self._error_actual("Se esperaba un numero, identificador o '('")
        return None

    def _coincidir_lexema(self, *lexemas: str) -> bool:
        if self._check_lexema(*lexemas):
            self._avanzar()
            return True
        return False

    def _check_tipo(self, tipo: str) -> bool:
        return not self._fin() and self._actual().tipo == tipo

    def _check_lexema(self, *lexemas: str) -> bool:
        return not self._fin() and self._actual().lexema in lexemas

    def _check_conjunto(self, lexemas: frozenset[str]) -> bool:
        return not self._fin() and self._actual().lexema in lexemas

    def _sincronizar(self) -> None:
        while not self._fin() and not self._check_conjunto(self.SINCRONIZACION):
            self._avanzar()
        if self._check_lexema(";"):
            self._avanzar()

    def _error_actual(self, mensaje: str) -> None:
        token = self._actual()
        self.errores.append(ErrorSintactico(token, mensaje, token.linea, token.columna))

    def _actual(self) -> Token:
        if self._fin(): return self.eof
        return self.tokens[self.pos]

    def _avanzar(self) -> Token:
        token = self._actual()
        if not self._fin(): self.pos += 1
        return token

    def _fin(self) -> bool:
        return self.pos >= len(self.tokens)

    def _crear_eof(self) -> Token:
        if not self.tokens: return Token("", "EOF", "EOF", 1, 1)
        ultimo = self.tokens[-1]
        return Token("", "EOF", "EOF", ultimo.linea, ultimo.columna + len(ultimo.lexema))
