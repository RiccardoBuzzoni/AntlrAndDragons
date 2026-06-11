# AntlrAndDragons

## Indice

1. [Introduzione](#1-introduzione)
2. [Scelte progettuali](#2-scelte-progettuali)
3. [Guida Rapida](#3-guida-rapida)
4. [Sintassi](#4-sintassi)
5. [Semantica Operazionale](#5-semantica-operazionale)
6. [Implementazione](#6-implementazione)
7. [Programmi di Test](#7-programmi-di-test)

## 1. Introduzione

### Struttura del progetto

```
AntlrAndDragons/
├── .idea/                      # File di configurazione dell'IDE IntelliJ
├── gen/                        # Codice generato automaticamente (es. da ANTLR)
├── src/                        # Codice sorgente del progetto
│   └── main/
│       ├── antlr4/             # Cartella contenente le grammatiche ANTLR
│       │   └── it/univr/lang/
│       │       └── BagOfGrammar.g4  # File della grammatica ANTLR4
│       └── java/               # Codice sorgente Java
│           └── it.univr.lang/  # Package principale Java
│               ├── errors/     # Sotto-package per la gestione degli errori
│               │   └── RuntimeError
│               ├── type/       # Sotto-package per il sistema dei tipi
│               │   ├── ComType
│               │   ├── ErrType
│               │   ├── ExpType
│               │   ├── FuncType
│               │   ├── ObjectType
│               │   ├── SimpleType
│               │   ├── Type
│               │   ├── TypeUtils
│               │   └── VoidType
│               ├── value/      # Sotto-package per la gestione dei valori
│               ├── BagOfGrammarIntp  # Interprete della grammatica (visitor principale)
│               ├── BagOfGrammarTS    # Controllo dei tipi (Type System/Type Checker) (visitor secondario)
│               ├── Main              # Punto di ingresso dell'applicazione
│               └── Mem               # Gestione della memoria/scope
├── target/                     # File compilati e build artifacts (generati da Maven)
├── .gitattributes              # Configurazione degli attributi Git
├── .gitignore                  # File per ignorare file/cartelle nel controllo versione
├── antlrandragons.iml          # File di modulo di IntelliJ IDEA
├── doc.md                      # Documentazione del progetto in Markdown
└── pom.xml                     # File di configurazione di Maven (dipendenze e build)
```

**AntlrAndDragons** è un linguaggio di programmazione imperativo tipizzato sviluppato tramite ANTLR4 come DSL (*Domain Specific Language*) ispirato alle meccaniche dei giochi di ruolo fantasy, in particolare Dungeons & Dragons (DnD).

## 2. Scelte progettuali

Il linguaggio è stato progettato come DSL (Domain Specific Language) con l'obiettivo di fornire costrutti espressivi e tematicamente coerenti con il dominio RPG, mantenendo allo stesso tempo caratteristiche tipiche dei linguaggi di programmazione moderni:

- **Tipizzazione statica**, con tipi di dominio dedicati ai giochi di ruolo (`HP`, `Level`, `Damage`, `QuestName`, `Die`);
- **Classi e oggetti**, tramite il costrutto `creature`;
- **Funzioni**, tramite il costrutto `spell`;
- **Flusso di controllo condizionato**;
- **Conversioni di tipo**, sia implicite per tipi compatibili nella gerarchia, sia esplicite tramite cast;
- **Zucchero sintattico**: assegnamenti composti (`+=`, `-=`, `*=`, `/=`), incremento e decremento unitario (`++`, `--`) con precedenza pre e post, operatore ternario (`_ ? _ : _`), e interpolazione di espressioni nelle stringhe (`i"...${expr}..."`);
- **Gestione strutturata delle sezioni del programma**, con sezioni distinte per creature, globali, funzioni e blocco principale.
- **Input da Utente**: permette la massima libertà di scelta e infinite possibilità di gioco.

L'interprete è stato implementato con ANTLR4 su IntelliJ IDEA, sfruttando parser e visitor generati automaticamente dalla grammatica.

### 2.1 Contesto applicativo

Il linguaggio è pensato per chi vuole programmare esperienze di gioco di ruolo in modo naturale ed espressivo.

I casi d'uso principali includono la modellazione di creature con statistiche e comportamenti, la definizione di incantesimi e abilità come funzioni richiamabili, la simulazione di combattimenti tramite lanci di dadi e logiche condizionali, e la gestione di quest con eventi narrativi e flussi ramificati.

Per rafforzare questa coerenza tematica, le parole chiave del linguaggio sono ispirate al mondo fantasy: `creature` al posto di `class`, `spell` al posto di `function`, `quest` per il blocco principale, `summon` per la creazione di oggetti, `roll` per il lancio dei dadi, `narrate` per la stampa a schermo, `flee` per l'uscita dal programma. Questo rende il codice immediatamente leggibile nel contesto applicativo, avvicinando la sintassi al linguaggio naturale del dominio.

### 2.2 Meccaniche RPG

Il linguaggio integra direttamente alcune meccaniche tipiche dei giochi di ruolo, rendendole costrutti di prima classe:

- **Lancio dei dadi** tramite il costrutto `roll`, che supporta la notazione standard `NdX` (es. `roll 2d6` lancia due dadi a sei facce e restituisce la somma);
- **Tipo `Die`**, sottotipo di `String`, per rappresentare e manipolare notazioni di dado come valori del linguaggio;
- **Creazione di oggetti** tramite `summon NomeCreatura`, equivalente a una `new` tipizzata sul dominio;
- **Stringhe interpolate** con la sintassi `i"...${expr}..."`, che permette di incorporare espressioni valutate direttamente nel testo, utili per messaggi narrativi dinamici.

### 2.3 Costrutti di controllo

Il linguaggio supporta un insieme completo di costrutti per il controllo del flusso:

- `if / else if / else` per la selezione condizionale;
- `until (cond) { }` come alternativa al `while`, che continua finché la condizione è falsa e termina quando diventa vera;
- `for x from e1 to e2 { } else { }`, ciclo con variabile di iterazione esplicita e blocco `else` opzionale eseguito solo se il ciclo completa senza `break`;
- `switch (e) { case ... }` con supporto al fall-through e clausola `default` opzionale;
- operatore ternario `cond ? a : b` per selezione inline;
- `break` per uscita anticipata da cicli e switch;
- `return` per la restituzione di un valore da una funzione;
- `flee` per la terminazione immediata del programma.



## 3. Guida Rapida

### Requisiti

Per compilare ed eseguire il linguaggio sono necessari:

- **Java 17** o superiore
- **ANTLR 4** (versione 4.13.1 o superiore) 

---

### Generazione del parser e compilazione

Installare Maven su Windows:
```bash
winget install Apache.Maven
```

Installare Maven su Mac:
```bash
brew install maven
```

Installare Maven su Linux:
```bash
sudo apt update
sudo apt install maven
```

Per controllare che l'installazione sia avvenuta correttamente:
```bash
mvn -version
java -version
```

Il progetto usa Maven, che gestisce automaticamente la generazione del parser dalla grammatica e la compilazione:

```bash
mvn package
```
Questo comando:
* Invoca il plugin ANTLR4 che genera lexer, parser e visitor da BagOfGrammar.g4
* Compila tutti i sorgenti Java
* Produce il JAR eseguibile antlrandragons.jar nella cartella target/


### Esecuzione dell’interprete

```bash
java -jar target/antlrandragons.jar programs/<file.bag>
```

## 4. Sintassi

### 4.1 Struttura di un programma

Un programma è composto da tre sezioni principali:

```text
program : creaturesSection? globalSection? spellbookSection? questBlock EOF ;
```
La sezione `globalSection` è la sezione opzionale dedicata alla dichiarazione delle variabili globali.

Le sezioni `creaturesSection` e `spellbookSection` sono rispettivamente le sezioni opzionali dichiarative delle classi e delle funzioni.

La sezione `quest` rappresenta il punto di ingresso del programma, contiene il corpo del programma racchiuso in un blocco `{ ... }`.

---

### 4.2 Creature e classi

```
creatureDecl : CREATURE ID '{' creatureMember* '}' ;

creatureMember : type ID ';'                        # ClassField
               | type ID '(' paramList? ')' block   # ClassMethodReturn
               | VOID ID '(' paramList? ')' block   # ClassMethodVoid
               ;
```

Una classe può contenere:

* campi;
* metodi con valore di ritorno;
* metodi void;

---

### 4.3 Funzioni

Le funzioni vengono definite nella sezione `spellbook`.

```
spellDecl : SPELL type ID '(' paramList? ')' block   # FuncDeclReturn
          | SPELL VOID ID '(' paramList? ')' block   # FuncDeclVoid
          ;
```

Possono:

* restituire valori;
* essere `void`;
* ricevere parametri tipizzati.


---

### 4.4 Tipi di dato

| Tipo        | Descrizione                                       |
| ----------- | ------------------------------------------------- |
| `Int`       | Intero con segno                                  |
| `Float`     | Numero reale                                      |
| `Bool`      | Valore booleano                                   |
| `String`    | Stringa di testo                                  |
| `HP`        | Punti vita, sottotipo semantico di `Int`          |
| `Damage`    | Danno, sottotipo semantico di `Int`               |
| `Level`     | Livello personaggio, sottotipo semantico di `Int` |
| `QuestName` | Nome di quest, derivato da `String`               |
| `Die`       | Tipo dedicato ai dadi RPG, sottotipo di `String`  |
| `Any`       | Supertipo universale                              |

I tipi `HP`,`Damage` e `Level` sono tipi di dominio che estendono `Int`, mentre `QuestName` e `Die` sono tipi di dominio che estende `String`. I tipi `HP`,`Damage` e `Level` sono compatibili nelle espressioni aritmetiche con `Int` e tra loro (con conversione implicita a `Int`). I tipi `QuestName` e `Die` sono compatibili nelle espressioni aritmetiche con `String` (con conversione implicita a `String`).


### 4.5 Zucchero Sintattico 

Sono supportati operatori composti:

* `+=`
* `-=`
* `*=`
* `/=`
* `%=`

### Assegnamento composto

```text
Mage.hp=20;
atk = goblin.cast ClawAttack(); //(type Damage)

Mage.hp -= atk; //conversione implicita
```

### Incremento e decremento

Il linguaggio supporta pre e post incremento/decremento unitario:

```text
++x;
x++;
--x;
x--;
```

---
### 4.6 Espressioni

La nostra gerarchia è coerente con i linguaggi tradizionali.

| Precedenza (dalla più alta alla più bassa) | Operatori                                                                |
| ------------------------------------------ | ------------------------------------------------------------------------ |
| 1                                          | `.` (accesso a campi e chiamata a metodi)                                |
| 2                                          | Operatori unari: `not`, `-`, cast `(Type)`, `++`, `--`, `roll`, `summon` |
| 3                                          | `*`, `/`, `%`                                                            |
| 4                                          | `+`, `-`                                                                 |
| 5                                          | `lt`, `gt`, `lte`, `gte`                                                 |
| 6                                          | `eq`, `neq`                                                              |
| 7                                          | `and`                                                                    |
| 8                                          | `or`                                                                     |
| 9                                          | `?:` (operatore ternario, **associativo a destra**)                      |
| 10                                         | Espressioni primarie                                                     |

---

### 4.7 Costrutti RPG

### Lancio dadi

```
roll 1d20        // lancia 1 dado a 20 facce
roll 2d6         // somma 2 dadi a 6 facce
roll d8          // equivalente a 1d8
```

Dadi supportati: `d4`, `d6`, `d8`, `d10`, `d12`, `d20`, `d%` (percentuale, 1–100).

### Creazione oggetti

```text
Warrior guerriero = summon Warrior();
```

### Chiamata funzione

```text
cast presentaQuest(nome_quest);
```

### Accesso a campo e invocazione di metodo

```text
hero.hp;
hero.attack();
```

---

### 4.8 Stringhe interpolate

Le stringhe prefissate con `i` supportano l'interpolazione di espressioni tramite `${...}`:

```
narrate i"HP rimasti: ${vita}";
narrate i"Danno base + bonus = ${danno + 5}";
```

---

### 4.9 Input utente

```
declare(Tipo, "messaggio prompt")
```

Mostra il prompt all'utente, attende un input da tastiera e lo converte nel tipo specificato. È necessario indicare esplicitamente il tipo atteso:

```
Int eta = declare(Int, "Quanti anni hai?");
Any nome = declare(Any, "Come ti chiami?");
Any x = declare(Int, "Scegli un numero:");
Int x = declare(Any, "Scegli un numero:");
```

I tipi `HP`, `Damage` e `Level` vengono trattati come `Int` durante la lettura.

Limiti:
* Per riconoscere il tipo senza doverlo dedurre dal contesto, abbiamo imposto la necessità di specificare il tipo di dato nel costrutto declare.
* Dopo la dichiarazione del tipo, sono necessarie la virgola e le doppie apici, anche vuote all'interno.

---

### 4.10 Costrutti di controllo

### If

```text
if (hp gt 0) {
    narrate "Alive";
}
else {
    narrate "Dead";
}
```
---

### Until

`until` continua il ciclo finché la condizione è **falsa** — termina quando diventa vera. Equivale a `while(not cond)`.

```text
until (hp lte 0) {
    hp -= 1;
}
```

---

### For

```text
for i from 1 to 10 {
    narrate i"${i}";
}
```

---

### Switch

`switch` con fall-through: una volta trovato il `case` corrispondente, l'esecuzione continua nei `case` successivi finché non si incontra un `break`.

```text
switch(level) {
    case 1:
        narrate "Beginner";

    default:
        narrate "Unknown";
}
```

---

### 4.11 Regole lessicali

### Identificatori

```text
ID : [a-zA-Z_] [a-zA-Z_0-9]* ;
```

### Letterali

```
INT_LIT     : [0-9]+ ;
FLOAT_LIT   : [0-9]+ '.' [0-9]+ ;
BOOL_LIT    : 'true' | 'false' ;
STRING_LIT  : '"' (~["\\\n] | '\\' .)* '"' ;
DIE_LIT     : 'd'('4'|'6'|'8'|'10'|'%'|'12'|'20') ;
```

### Commenti e spazi bianchi

```
// commento su singola linea

/* commento
   multilinea */
```

Gli spazi bianchi (`\t`, `\r`, `\n`, spazio) vengono ignorati dal lexer.

---

## 5. Semantica operazionale

Di seguito mostriamo le regole di transizione della semantica operazionale di AntlrAndDragons. 

Lo stato è una coppia $(\overline{\sigma}, c)$ dove $\overline{\sigma} = \sigma_1 \cdot \sigma_2 \cdot \ldots \cdot \sigma_n$ è una pila di memorie (una memoria $\sigma$ è una mappa da identificatori a valori) e $c$ è il comando (o l'espressione) da valutare.
 
### 5.1 Until

$$
\text{Until-False} ~ \frac{
    (\overline{\sigma},\ e) \rightarrow \texttt{false}
    \quad
    (\overline{\sigma},\ c) \rightarrow \overline{\sigma}'
    \quad
    (\overline{\sigma}',\ \texttt{until}\ (e)\ \{c\}) \rightarrow \overline{\sigma}''
}{
    (\overline{\sigma},\ \texttt{until}\ (e)\ \{c\}) \rightarrow \overline{\sigma}''
}
$$



$$
\text{Until-True} ~ \frac{
    (\overline{\sigma},\ e) \rightarrow \texttt{true}
}{
    (\overline{\sigma},\ \texttt{until}\ (e)\ \{c\}) \rightarrow \overline{\sigma}
}
$$



$$
\text{Until-Break} ~ \frac{
    (\overline{\sigma},\ e) \rightarrow \texttt{false}
    \quad
    (\overline{\sigma},\ c) \rightarrow \texttt{break}
}{
    (\overline{\sigma},\ \texttt{until}\ (e)\ \{c\}) \rightarrow \overline{\sigma}
}
$$

---

### 5.2 Switch-case-default

Utilizziamo la notazione $\texttt{switch}\ (e)\ \{cs\}$ dove $cs$ è la lista di clause, e scrivo i case come $\texttt{case}\ v: \overline{s}$ e il default come $\texttt{default}: \overline{s}$.


$$
\text{Switch-case} ~ \frac{
    (\overline{\sigma},\ e) \rightarrow v
    \quad
    (\overline{\sigma},\ e_i) \rightarrow v
    \quad
    (\overline{\sigma},\ \overline{s}_i) \rightarrow \overline{\sigma}'
}{
    (\overline{\sigma},\ \texttt{switch}\ (e)\ \{\ldots,\ \texttt{case}\ e_i: \overline{s}_i,\ \ldots\}) \rightarrow \overline{\sigma}'
}
$$



$$
\text{Switch-Default} ~ \frac{
    (\overline{\sigma},\ e) \rightarrow v
    \quad
    \forall i.\ (\overline{\sigma},\ e_i) \rightarrow v_i \land v_i \neq v
    \quad
    (\overline{\sigma},\ \overline{s}_d) \rightarrow \overline{\sigma}'
}{
    (\overline{\sigma},\ \texttt{switch}\ (e)\ \{\texttt{case}\ e_1: \overline{s}_1,\ \ldots,\ \texttt{case}\ e_n: \overline{s}_n,\ \texttt{default}: \overline{s}_d\}) \rightarrow \overline{\sigma}'
}
$$

---

### 5.3 For-else-break

`for x from e1 to e2 { c } else { c_else }` — itera `x` da `e1` a `e2`, con un blocco `else` opzionale che viene eseguito **solo se il ciclo completa normalmente** (senza `break`).

Nel For-Break: Con scoping dinamico, ogni blocco pusha un nuovo frame sulla pila e lo poppa alla fine.

Uso la notazione $\sigma_x = \{x \mapsto n_1\}$ per il frame contenente solo `x`, e la pila diventa $\sigma_x \cdot \overline{\sigma}$ all'ingresso del blocco, e torna $\overline{\sigma}$ all'uscita.


$$
\text{For-Done} ~ \frac{
    (\overline{\sigma},\ e_1) \rightarrow n_1
    \quad
    (\overline{\sigma},\ e_2) \rightarrow n_2
    \quad
    n_1 > n_2
}{
    (\overline{\sigma},\ \texttt{for}\ x\ \texttt{from}\ e_1\ \texttt{to}\ e_2\ \{c\}) \rightarrow \overline{\sigma}
}
$$



$$
\text{For-Else} ~ \frac{
    (\overline{\sigma},\ e_1) \rightarrow n_1
    \quad
    (\overline{\sigma},\ e_2) \rightarrow n_2
    \quad
    n_1 > n_2
    \quad
    (\overline{\sigma},\ c_{else}) \rightarrow \overline{\sigma}'
}{
    (\overline{\sigma},\ \texttt{for}\ x\ \texttt{from}\ e_1\ \texttt{to}\ e_2\ \{c\}\ \texttt{else}\ \{c_{else}\}) \rightarrow \overline{\sigma}'
}
$$



$$
\text{For-Break} ~ \frac{
    (\overline{\sigma},\ e_1) \rightarrow n_1
    \quad
    (\overline{\sigma},\ e_2) \rightarrow n_2
    \quad
    n_1 \leq n_2
    \quad
    (\{x \mapsto n_1\} \cdot \overline{\sigma},\ c) \rightarrow \texttt{break}
}{
    (\overline{\sigma},\ \texttt{for}\ x\ \texttt{from}\ e_1\ \texttt{to}\ e_2\ \{c\}\ \texttt{else}\ \{c_{else}\}) \rightarrow \overline{\sigma}
}
$$


---

## 6. Implementazione

### 6.1 Gestione della memoria — `Mem`

La classe `Mem` è il cuore della gestione degli scope. Mantiene una `Deque<Scope>` dove ogni `Scope` contiene:

- `Map<String, ExpType> types` — tipo dichiarato di ogni variabile
- `Map<String, ExpValue<?>> values` — valore runtime (può essere `null` se non inizializzata)

#### Operazioni principali

| Metodo | Descrizione |
|---|---|
| `pushScope()` | Aggiunge un nuovo frame in cima alla pila |
| `popScope()` | Rimuove il frame in cima |
| `declare(id, type)` | Dichiara una variabile senza valore |
| `declareInit(id, type, val)` | Dichiara e inizializza una variabile |
| `getValue(id)` | Cerca il valore risalendo la pila degli scope |
| `getType(id)` | Cerca il tipo dichiarato risalendo la pila |
| `setValue(id, val)` | Aggiorna il valore risalendo la pila |
| `contains(id)` | Vero se la variabile è dichiarata *e* inizializzata |
| `isDeclared(id)` | Vero se la variabile è dichiarata (anche senza valore) |
| `mergeFrom(other)` | Reintegra i valori da una memoria clonata |
| `copyOf(other)` | Crea una copia indipendente (per il branching) |

La ricerca in `getValue`, `getType` e `setValue` percorre la pila dall'alto verso il basso, implementando la semantica di scoping dinamico.

---

### 6.2 Scoping dinamico

AntlrAndDragons implementa uno **scoping dinamico**: la risoluzione delle variabili avviene sulla pila di scope attiva *a runtime*, non sulla struttura lessicale statica del codice sorgente.

#### Come funziona

La memoria è gestita dalla classe `Mem`, che mantiene una `Deque<Scope>` — una pila di frame. Ogni frame contiene una mappa `nome → tipo` e una mappa `nome → valore`. La ricerca di una variabile percorre la pila dall'alto (scope più interno) verso il basso (scope globale), restituendo il primo match trovato.

Quando si entra in un blocco `{ ... }`, viene eseguito `pushScope()`. All'uscita (anche in caso di eccezioni) viene eseguito `popScope()`, garantito da un blocco `finally`.

#### Esempio

```
world:
    Int x = 10;

spellbook:
    spell Int leggiX() {
        return x;
    }

quest:
{
    narrate cast leggiX();    // stampa 10 (vede x = 10 dallo scope globale)
    {
        Int x = 11;
        narrate cast leggiX();    // stampa 11 (ora in cima alla pila c'è x = 11)
    }
    narrate cast leggiX();    // stampa 10 (il blocco interno è terminato, x = 11 non esiste più)
}
```

**Output:** `10`, `11`, `10`

---

### 6.3 Shadowing delle variabili

BagOfGrammar supporta il fenomeno dello **shadowing** (oscuramento delle variabili).

Lo shadowing si verifica quando una variabile dichiarata in uno scope interno possiede lo stesso nome di una variabile dichiarata in uno scope esterno. In questo caso la variabile interna ha la precedenza e nasconde temporaneamente quella esterna per tutta la durata dello scope in cui è definita.

Esempio:

```text
Int hp = 100;

{
    Int hp = 50;

    narrate hp;
}

narrate hp;
```

**Output:** `50`, `100`

Lo shadowing è gestito naturalmente dalla struttura a pila degli scope utilizzata dall'interprete. Durante la ricerca di una variabile, l'interprete esamina gli scope attivi partendo da quello più interno; la prima dichiarazione trovata viene utilizzata e tutte le eventuali dichiarazioni omonime negli scope esterni vengono ignorate.


---

### 6.4 Passaggio dei parametri per valore

I parametri delle funzioni sono passati **per valore**: al momento della chiamata, i valori degli argomenti vengono copiati in nuove variabili locali nello scope della funzione. Modifiche ai parametri all'interno della funzione non influenzano le variabili del chiamante.

**Sequenza di esecuzione di una chiamata:**

1. Gli argomenti vengono valutati nello scope del chiamante.
2. Viene creato un nuovo scope con `pushScope()`.
3. I parametri formali vengono dichiarati e inizializzati con i valori copiati (`bindParams`).
4. Viene eseguito il corpo della funzione.
5. In caso di `return`, viene lanciata una `ReturnException` che porta il valore di ritorno.
6. Lo scope locale viene rimosso con `popScope()` nel blocco `finally`.


### Osservazione — Passaggio dei parametri e oggetti

Il linguaggio adotta **pass-by-value** uniformemente.

Per i **tipi primitivi** (`Int`, `Float`, `Bool`, `String`, e i tipi di dominio) questo garantisce isolamento completo: la funzione riceve una copia, le modifiche locali non si propagano al chiamante.

Per gli **oggetti**, il valore passato è un *riferimento* all'istanza heap — non l'oggetto stesso. La funzione riceve quindi una copia del riferimento, che punta allo stesso oggetto del chiamante. Ne consegue che:

- **riassegnare il parametro** (es. `param = summon AltroOggetto()`) non ha effetto all'esterno;
- **modificare i campi** (es. `param.vita -= 10`) produce un side effect visibile al chiamante, perché entrambi puntano alla stessa istanza.

Questo comportamento è identico a quello di Java.

```
creatures:
    creature Goblin { HP vita; }

spellbook:
    spell void cura(Goblin g) {
        g.vita += 20;           // modifica visibile al chiamante
        g = summon Goblin();    // riassegnazione locale: ignorata dal chiamante
    }

quest:
{
    Goblin nemico = summon Goblin();
    nemico.vita = 30;
    cast cura(nemico);
    narrate nemico.vita;   // stampa 50, non 30
}
```
---

### 6.5 Il type checker (`BagOfGrammarTS`)

Il type checker è implementato come secondo visitor (`BagOfGrammarBaseVisitor<ExpType>`). Viene eseguito **prima** dell'interprete e verifica la correttezza dei tipi in modo statico, usando una struttura `Mem` analoga ma che lavora con i tipi invece che con i valori.

Le verifiche principali includono:

- Dichiarazioni e utilizzo di variabili
- Compatibilità dei tipi nelle espressioni e assegnamenti
- Correttezza dei `return` rispetto al tipo di ritorno della funzione
- Corrispondenza degli argomenti nelle chiamate a funzione
- Esistenza di classi e metodi negli accessi agli oggetti

### Tipizzazione statica

Il linguaggio adotta una **tipizzazione statica**: ogni variabile ha un tipo fissato alla dichiarazione, verificato prima dell'esecuzione dal type checker (`BagOfGrammarTS`). Tentativi di assegnare valori incompatibili vengono rilevati staticamente.

Esempio corretto:

```text
Int x = 10;
```

Esempio errato:

```text
Int x = "hello";
```

L’errore viene rilevato durante il controllo semantico.


### Gerarchia dei tipi

Il linguaggio implementa una gerarchia semantica dei tipi.

```
Any
├── Bool
├── String
│   ├── QuestName
│   └── Die
└── Float
    └── Int
        ├── HP
        ├── Damage
        └── Level
```

* La gerarchia dei tipi contiene un tipo radice `Any` dal quale derivano tutti i tipi del linguaggio.
* `HP`, `Damage` e `Level` sono sottotipi semantici di `Int`, possono essere usati dove è atteso un `Int`.
* `QuestName` e `Die` sono sottotipi semantici di `String`, possono essere usati dove è atteso `String`.

Le conversioni numeriche seguono le catene:

$$
    \text{HP} \subsetneq \text{Int} \subsetneq \text{Float} \\
$$
$$
    \text{Damage} \subsetneq \text{Int} \subsetneq \text{Float} \\
$$
$$
    \text{Level} \subsetneq \text{Int} \subsetneq \text{Float} \\
$$

Le conversioni non numeriche seguono le catene:

$$
    \text{QuestName} \subsetneq \text{String} \\
$$
$$
    \text{Die} \subsetneq \text{String} \\
$$



### Compatibilità dei tipi

Una variabile può ricevere:

* valori dello stesso tipo;
* valori di sottotipi compatibili o valori convertibili implicitamente;

Esempio:

```text
Int x = 10;
Damage d = 5;

x = d;
```

Poiché `Damage` è semanticamente compatibile con `Int`, l’assegnamento è valido.

La compatibilità è centralizzata nel metodo `isAssignable(Type from, Type to)`, che codifica tutte le regole di assegnabilità del linguaggio in un unico punto:

```java
private boolean isAssignable(Type from, Type to) {
    if (from instanceof ErrType || to instanceof ErrType) return true;
    if (to == SimpleType.ANY) return true;
    if (from.equals(to)) return true;
    if (to == SimpleType.INT &&
            (from == SimpleType.HP || from == SimpleType.DAMAGE || from == SimpleType.LEVEL))
        return true;
    // ...
}
```

Il metodo restituisce `true` nei seguenti casi: se uno dei due tipi è `ErrType` (errore già segnalato in precedenza, si evita il cascading di errori); se il tipo destinazione è `Any` (accetta qualsiasi valore); se i tipi sono identici; se `from` è un sottotipo di `to` secondo la gerarchia definita (ad esempio `HP → Int`, `Die → String`, `Int → Float`). Il metodo è usato pervasivamente nel type checker — nelle dichiarazioni di variabile, negli assegnamenti, nei ritorni di funzione e nei controlli sugli argomenti — garantendo che le regole di compatibilità siano applicate in modo uniforme in tutto il sistema.

###  Conversione di tipo

#### Cast esplicito

Nonostante siano state implementate conversioni implicite tra tipi compatibili, per estendere le funzionalità della grammatica, sono ammesse anche conversioni esplicite.

```text
(Float) x
(Int) y
```

La grammatica:

```text
'(' type ')' expr
```
---

### 6.7 L'interprete (`BagOfGrammarIntp`)

L'interprete è implementato come un **tree-walking interpreter**: visita direttamente l'AST(Abstract Syntax Tree) prodotto da ANTLR4 senza nessuna fase intermedia di compilazione o generazione di bytecode. Estende `BagOfGrammarBaseVisitor<ExpValue<?>>`, dove ogni metodo `visit*` corrisponde a una regola della grammatica e restituisce un `ExpValue<?>` — il supertipo di tutti i valori del linguaggio (`IntValue`, `DecValue`, `BoolValue`, `StringValue`, `ObjectValue`).

### Memoria e scoping dinamico

La memoria è gestita dalla classe `Mem`, che internamente mantiene una **pila di frame** (scope stack). Ogni frame è una mappa da identificatori a valori. Le operazioni principali sono `pushScope()`, `popScope()`, `declareInit()` e `setValue()` / `getValue()`.

Lo scoping è **dinamico**: quando si entra in un blocco viene pushato un nuovo frame, e quando se ne esce viene poppato — indipendentemente da dove il blocco è stato definito. Questo significa che una funzione chiamata vede le variabili del contesto da cui viene invocata, non quello in cui è stata dichiarata.

```java
@Override
public ExpValue<?> visitBlock(BagOfGrammarParser.BlockContext ctx) {
    mem.pushScope();
    try {
        for (BagOfGrammarParser.StatContext s : ctx.stat())
            visit(s);
    } finally {
        mem.popScope(); // pop garantito anche in caso di eccezione
    }
    return null;
}
```

Il blocco `finally` garantisce che il frame venga sempre rimosso anche se viene lanciata una `BreakException` o `ReturnException` durante l'esecuzione.

---

### Segnali di controllo del flusso

Il linguaggio supporta `break`, `return` e `flee` (exit). Anziché usare valori sentinella o flag booleani, l'interprete sfrutta le eccezioni Java come **segnali di controllo del flusso**, un pattern efficace nei tree-walking interpreter:

```java
private static class ReturnException extends RuntimeException {
    final ExpValue<?> value;
    ReturnException(ExpValue<?> value) { ... }
}

private static class BreakException extends RuntimeException {
    static final BreakException INSTANCE = new BreakException();
}

private static class ExitException extends RuntimeException {
    static final ExitException INSTANCE = new ExitException();
}
```

Tutte e tre disabilitano il fill dello stack trace (`super(null, null, true, false)`) per efficienza, dato che non rappresentano errori ma semplici salti di controllo. Ad esempio, `visitStatBreak` si limita a lanciare `BreakException.INSTANCE`, e il ciclo che lo contiene (`for`, `until`, `switch`) è responsabile di catturarla.

---

### Branching — `newBranch()`

Una delle scelte architetturali più importanti dell'interprete è il meccanismo di **branching**, usato ogni volta che si entra in un blocco condizionale o in un ciclo. L'idea di base è: anziché eseguire il blocco direttamente sulla memoria corrente, si crea una copia isolata dell'interprete — un *branch* — che lavora su una copia della memoria. Al termine, le modifiche vengono riportate sulla memoria principale tramite `mergeFrom()`.

```java
private BagOfGrammarIntp newBranch() {
    BagOfGrammarIntp branch = new BagOfGrammarIntp(Mem.copyOf(this.mem));
    branch.creatures.putAll(this.creatures);
    branch.spells.putAll(this.spells);
    return branch;
}
```

Il branch eredita una **copia profonda** della memoria corrente (`Mem.copyOf`), oltre alla tabella delle creature (classi) e degli spell (funzioni), che sono condivisi per riferimento perché sono in sola lettura durante l'esecuzione.

Questo pattern è usato ad esempio nel costrutto `if`:

```java
@Override
public ExpValue<?> visitIfStat(BagOfGrammarParser.IfStatContext ctx) {
    BagOfGrammarIntp branch = newBranch();
    // ...
    if (visitBoolExpr(exprs.get(i)).toJavaValue()) {
        branch.visit(blocks.get(i));
        matched = true;
        break;
    }
    // ...
    mem.mergeFrom(branch.getMem());
    return null;
}
```

Il ramo eseguito lavora su `branch`, poi `mergeFrom` sincronizza le variabili modificate (già esistenti nella memoria principale) senza introdurre nel contesto esterno le variabili dichiarate localmente nel blocco. Questo garantisce il corretto **isolamento dello scope** pur propagando gli effetti sui binding già esistenti.

Lo stesso accade in `visitUntilStat` e nel corpo del `for`, dove ogni iterazione crea un branch fresco:

```java
BagOfGrammarIntp branch = newBranch();
try {
    branch.visit(ctx.block(0));
} catch (BreakException e) {
    mem.mergeFrom(branch.getMem());
    brokeOut = true;
    break;
}
mem.mergeFrom(branch.getMem());
```

In caso di `break`, il merge avviene comunque, per preservare eventuali effetti collaterali su variabili esterne già avvenuti prima del break nel corpo del ciclo.

---

### Chiamate a funzioni — `callFunction` e `bindParams`

Gli spell (funzioni) vengono registrati nella mappa `spells` durante la fase di setup in `registerSpells`, prima dell'esecuzione del quest block. Quando viene incontrata una chiamata, `visitSpellCall` recupera la dichiarazione e delega a `callFunction`:

```java
private ExpValue<?> callFunction(BagOfGrammarParser.ParamListContext params,
                                 BagOfGrammarParser.BlockContext body,
                                 List<ExpValue<?>> args) {
    mem.pushScope();
    bindParams(params, args);
    ExpValue<?> result = null;
    try {
        executeBody(body);
    } catch (ReturnException e) {
        result = e.value;
    } finally {
        mem.popScope();
    }
    return result;
}
```

Il meccanismo è semplice: si pusha un nuovo scope, si legano i parametri formali agli argomenti attuali, si esegue il corpo, e si cattura l'eventuale `ReturnException` per estrarne il valore. Il `finally` garantisce il pop dello scope anche se il corpo lancia eccezioni inattese.

Il binding dei parametri è demandato a `bindParams`, che itera parallelamente lista dei parametri formali e lista dei valori calcolati:

```java
private void bindParams(BagOfGrammarParser.ParamListContext params, List<ExpValue<?>> args) {
    if (params == null) return;
    List<BagOfGrammarParser.ParamContext> ps = params.param();
    for (int i = 0; i < ps.size(); i++)
        mem.declareInit(ps.get(i).ID().getText(), resolveType(ps.get(i).type()), args.get(i));
}
```

Ogni parametro viene dichiarato nello scope corrente (quello appena pushato) con il tipo specificato nella firma e il valore passato come argomento. Il passaggio è **per valore**: la funzione riceve una copia del valore, non un riferimento alla variabile del chiamante.

---

### Chiamate a metodi — `callMethod`

I metodi sono definiti all'interno delle creature (classi). `callMethod` riceve il receiver già valutato come `ObjectValue` e il contesto della chiamata:

```java
private ExpValue<?> callMethod(ObjectValue receiver, BagOfGrammarParser.SpellCallContext callCtx) {
    String className  = receiver.getClassName();
    String methodName = callCtx.ID().getText();
    CreatureDescriptor desc = creatures.get(className);
    // ...
    mem.pushScope();
    mem.declareInit("self", new ObjectType(className), receiver);
    // ...
    bindParams(mr.paramList(), args);
    result = executeBody(mr.block());
    // ...
    mem.popScope();
    return result;
}
```

La differenza principale rispetto a `callFunction` è che nello scope della chiamata viene iniettata automaticamente la variabile `self`, legata al receiver. Questo rende l'oggetto corrente accessibile all'interno del corpo del metodo con la stessa sintassi usata per qualsiasi altra variabile. I parametri vengono poi legati con lo stesso `bindParams` usato per le funzioni libere, rendendo il meccanismo uniforme.

Le creature vengono registrate in `registerCreatures` che distingue tra **field** (dichiarazioni di tipo) e **method** (contesti di dichiarazione di funzione), memorizzando tutto nel `CreatureDescriptor` associato al nome della classe. Al momento della `summon`, `visitExprNew` recupera il descriptor e inizializza i campi ai loro valori di default tramite `defaultValue()`.

---

### 6.8 Gestione degli errori

Il linguaggio distingue due categorie di errori, rilevati in fasi distinte dell'esecuzione: gli **errori statici**, individuati dal type checker prima dell'esecuzione, e gli **errori dinamici**, individuati dall'interprete a runtime.
 
---

#### Errori statici — type checker

Il type checker (`BagOfGrammarTS`) analizza l'AST prima di qualsiasi esecuzione. Poiché opera sui tipi dichiarati e sulla struttura del programma — e non sui valori concreti — può rilevare solo errori che sono **sempre sbagliati indipendentemente da ciò che accade a runtime**.

Esempi di errori statici:

- Utilizzo di una variabile non dichiarata
- Assegnamento di un valore di tipo incompatibile
- Chiamata a uno spell con argomenti di tipo errato
- Chiamata a un metodo inesistente su una creature (`w.fly()` quando `fly` non è definito su `Warrior`)
- Istanziazione di una creature non dichiarata (`summon Dragon()` quando `Dragon` non esiste)
  Se il type checker rileva uno o più errori, l'esecuzione viene **interrotta prima di iniziare**:

```
[TypeCheck] line 48:8 – Unknown method 'fly' on creature Warrior
[TypeCheck] line 53:19 – Unknown creature (class): Dragon
Type error(s) found. Execution aborted.
```

Questo garantisce che il programma non venga mai eseguito in uno stato strutturalmente inconsistente.
 
---

#### Errori dinamici — interprete

Alcuni errori non sono rilevabili staticamente perché dipendono dai **valori concreti** disponibili solo a runtime. Questi vengono gestiti dall'interprete tramite la classe `RuntimeError`.

```java
public class RuntimeError extends RuntimeException {
    private final int line;
 
    public RuntimeError(String msg, int line) {
        super(msg);
        this.line = line;
    }
 
    // ...
    
    @Override
    public String toString() {
        if (line >= 0)
            return "[Runtime Error] line " + line + ": " + getMessage();
        else
            return "[Runtime Error] " + getMessage();
    }
}
```

Quando l'interprete rileva una condizione di errore, lancia un `RuntimeError` con un messaggio descrittivo e, dove disponibile, il numero di riga nel sorgente. L'eccezione risale lo stack fino al `Main`, che la cattura e la stampa in formato leggibile senza produrre uno stack trace:

```java
try {
    BagOfGrammarIntp interpreter = new BagOfGrammarIntp();
    interpreter.visit(tree);
} catch (RuntimeError e) {
    System.err.println(e.toString());
    System.exit(1);
}
```

Gli errori dinamici rilevati includono:

| Scenario | Messaggio |
|---|---|
| Divisione per zero | `Can't divide by 0!` |
| Input utente non convertibile al tipo atteso | `Expected an integer value, got: 'pippo'` |
| Accesso a un campo su un valore non-oggetto | `Cannot access field 'name' on a non-creature value` |

Nell'interprete sono gestiti altri tipi di errore, tuttavia questi errori vengono catturati prima dal type checker.
Questi controlli sono presenti nell'interprete come rete di sicurezza difensiva, nel caso in cui l'interprete venga utilizzato in futuro senza passare per il type checker.

| Scenario | Messaggio |
|---|---|
| Chiamata a un metodo inesistente a runtime | `Unknown method 'fly' on creature Warrior` |
| Istanziazione di una creature sconosciuta a runtime | `Unknown creature: 'Dragon'` |
| Spell non dichiarata | `Unknown spell: 'phantom_strike'` |

Ad esempio, la divisione per zero viene intercettata in `visitExprMulDivMod`, prima che la divisione venga effettivamente eseguita, controllando se il divisore è zero:

```java
if ("/".equals(op) || "%".equals(op)) {
    Object javaValue = right.toJavaValue();
    if (javaValue instanceof Number && ((Number) javaValue).doubleValue() == 0.0)
        throw new RuntimeError("Can't divide by 0!", ctx.start.getLine());
}
return applyArith(left, right, op);
```

Il controllo usa `doubleValue() == 0.0` per coprire uniformemente sia operandi `Int` che `Float`.
 
---

#### Separazione delle responsabilità

La distinzione tra le due categorie di errore rispecchia una separazione precisa delle responsabilità:

- Il **type checker** rileva errori di *tipo e struttura* — ciò che è sbagliato per costruzione, indipendentemente dall'esecuzione.
- L'**interprete** rileva errori di *valore e comportamento* — ciò che dipende da dati noti solo a runtime, come l'input dell'utente o il risultato di un calcolo.
  Questa architettura garantisce che i programmi ben tipati vengano eseguiti in modo sicuro, e che gli errori inevitabilmente dinamici vengano comunque presentati all'utente in forma comprensibile.

---

## 7. Programmi di Test

### `DemoBattle.bag`
```
// ==================================================
//  ANTLR & DRAGONS — DemoBattle.bag
//  Small showcase of Antlr&Dragons functionalities
//
//  Features:
//      - Classi e oggetti
//      - Funzioni
//      - Flusso di controllo condizionato (switch)
//      - Zucchero sintattico
//      - Cast (implicito ed esplicito)
// ==================================================

// Class definition
creatures:

    creature Hero{
        HP hp;
        Level level;
        Int strength_bonus;
        Int dexterity_bonus;
        Int proficiency_bonus;
        Int armour_class;

        Damage longswordDamage(Bool is_critical, Int bonus){
            narrate "The hero strikes the goblin warrior with his sword!";
            if(is_critical eq true){
                return roll 2d8 + bonus;
            }
            else{
                return roll d8 + bonus;
            }
        }

        Damage longbowDamage(Bool is_critical, Int bonus){
            narrate "The hero hits the goblin warrior with his bow!";
            if(is_critical eq true){
                return roll 2d8 + bonus;
            }
            else{
                return roll d8 + bonus;
            }
        }

        HP healingPotion(){
            narrate "The hero drinks a small healing potion!";
            return roll 2d4 + 2;
        }
    }

    creature GoblinWarrior{
        HP hp;
        Int attack_bonus;
        Int damage_bonus;
        Int armour_class;


        Damage scimitarDamage(Bool is_critical, Int bonus){
            narrate "The goblin warrior strikes the hero with his scimitar!";
            if(is_critical eq true){
                return roll 2d6 + bonus;
            }
            else{
                return roll d6 + bonus;
            }
        }

    }

// Function definition
spellbook:

    spell void describeHero(HP hero_hp, Level hero_level) {
        narrate "--- HeroStatus ---";
        narrate i"  HP:    ${hero_hp}";
        narrate i"  Level: ${hero_level}";
        narrate "------------------";
    }

// Main
quest:
{
    QuestName mission = "Slay the goblin warrior!";
    String mission_name = mission; // non-numeric type chain
    narrate "====================";
    narrate mission_name;
    narrate "====================";

    Hero hero = summon Hero();
    GoblinWarrior goblin_warrior = summon GoblinWarrior();

    hero.hp = (HP) 40; // explicit casting
    hero.level = (Level) 3;
    hero.strength_bonus = 5;
    hero.dexterity_bonus = 3;
    hero.proficiency_bonus = 2;
    hero.armour_class = 17;

    goblin_warrior.hp = 25; // implicit casting
    goblin_warrior.attack_bonus = 4;
    goblin_warrior.damage_bonus = 2;
    goblin_warrior.armour_class = 15;


    narrate "Initial state:";
    cast describeHero(hero.hp, hero.level);
    narrate i"Goblin warrior -> HP = ${goblin_warrior.hp} \n";

    // Battle loop
    Int turn = 1;
    until((hero.hp lte 0) or (goblin_warrior.hp lte 0)){
        narrate i"===== TURN ${turn} =====";
        narrate "Select an action:";
        narrate "  1 -> Longsword attack";
        narrate "  2 -> Longbow attack";
        narrate "  3 -> Heal";
        narrate "  4 -> Flee";
        narrate "";

        Damage damage = 0;
        Bool valid_action = false;
        Bool is_critical = false;

        until(valid_action eq true){
            Int action = declare(Int, "Select a valid action: ");

            switch(action){
                case 1:
                    Int attack_roll = roll d20;
                    if(attack_roll eq 20){
                        narrate "Critical success!";
                        is_critical = true;
                        damage = hero.cast longswordDamage(is_critical, hero.strength_bonus);
                        narrate i"The goblin warrior is hit for ${damage} hit points!";
                        goblin_warrior.hp -= damage;
                    }
                    else{
                        attack_roll += hero.strength_bonus;
                        attack_roll += hero.proficiency_bonus;
                        if(attack_roll gte goblin_warrior.armour_class){
                            damage = hero.cast longswordDamage(is_critical, hero.strength_bonus);
                            narrate i"Successful attack! [Attack roll = ${attack_roll}]";
                            narrate i"The goblin warrior is hit for ${damage} hit points!";
                            goblin_warrior.hp -= damage;
                        }
                        else{
                            narrate i"Attack failed! [Attack roll = ${attack_roll}]";
                        }
                    }
                    valid_action = true;
                    break;
                case 2:
                    Int attack_roll = roll d20;
                    if(attack_roll eq 20){
                        narrate "Critical success!";
                        is_critical = true;
                        damage = hero.cast longbowDamage(is_critical, hero.dexterity_bonus);
                        narrate i"The goblin warrior is hit for ${damage} hit points!";
                        goblin_warrior.hp -= damage;
                    }
                    else{
                        attack_roll += hero.dexterity_bonus;
                        attack_roll += hero.proficiency_bonus;
                        if(attack_roll gte goblin_warrior.armour_class){
                            damage = hero.cast longbowDamage(is_critical, hero.dexterity_bonus);
                            narrate i"Successful attack! [Attack roll = ${attack_roll}]";
                            narrate i"The goblin warrior is hit for ${damage} hit points!";
                            goblin_warrior.hp -= damage;
                        }
                        else{
                            narrate i"Attack failed! [Attack roll = ${attack_roll}]";
                        }
                    }
                    valid_action = true;
                    break;
                case 3:
                    HP heal = hero.cast healingPotion();
                    if(hero.hp + heal gte 40){
                        narrate "The hero is fully healed!";
                        hero.hp = 40;
                    }
                    else{
                        narrate i"The hero is healed for ${heal} hit points!";
                        hero.hp += heal;
                        narrate i"The hero currently has ${hero.hp} hit points!";
                    }
                    valid_action = true;
                    break;
                case 4:
                    narrate "The hero flees from battle... coward!";
                    flee;
                default:
                    narrate "Invalid choice!";
            }
        }

        if(goblin_warrior.hp lte 0){
            narrate "====================";
            narrate "The hero has slain the goblin!";
            narrate "====================";
            break;
        }

        // Goblin's turn
        is_critical = false;
        narrate "The goblin attacks!";
        Int attack_roll = roll d20;
        if(attack_roll eq 20){
            narrate "Critical success!";
            is_critical = true;
            damage = goblin_warrior.cast scimitarDamage(is_critical, goblin_warrior.damage_bonus);
            narrate i"The hero is hit for ${damage} hit points!";
            hero.hp -= damage;
        }
        else{
            attack_roll += goblin_warrior.attack_bonus;
            if(attack_roll gte hero.armour_class){
                damage = goblin_warrior.cast scimitarDamage(is_critical, goblin_warrior.damage_bonus);
                narrate i"Successful attack! [Attack roll = ${attack_roll}]";
                narrate i"The hero is hit for ${damage} hit points!";
                hero.hp -= damage;
            }
            else{
                narrate i"Attack failed! [Attack roll = ${attack_roll}]";
            }
        }

        if(hero.hp lte 0){
            narrate "====================";
            narrate " The hero fell in battle!";
            narrate "====================";
        }
        narrate "";
        turn += 1;
    }

}
```

--- 

### `TimeLimit.bag`


```
// ==================================================
//  ANTLR & DRAGONS — DemoForFireball.bag
//  Second showcase of Antlr&Dragons functionalities
//
//  Features:
//      - Ciclo for con break ed else
//      - Incremento/decremento pre e post
//      - Zucchero sintattico /=
//      - Funzione fireball su più nemici
//      - Cast esplicito (Damage)
//      - Operatore ternario
// ==================================================

creatures:

    creature Hero {
        HP hp;
        Level level;
        Int spell_slots;
        String name;
    }

    creature Goblin {
        HP hp;
        Int dex;
        String name;
    }

spellbook:

    // Fireball: area spell that hits three goblins.
    // Goblins that succeed a Dexterity saving throw (DC 14) take half damage.
    // Zucchero sintattico /= to halve damage on a successful save.
    spell void fireball(Hero caster, Goblin g1, Goblin g2, Goblin g3) {

        Damage totalDmg = roll 8d6;
        narrate i"${caster.name} casts Fireball for ${totalDmg} fire damage!";

        // Saving throw per ogni goblin (d20 + loro dex, CD 14)
        Int save1 = roll d20 + g1.dex;
        Int save2 = roll d20 + g2.dex;
        Int save3 = roll d20 + g3.dex;

        Damage dmg1 = totalDmg;
        Damage dmg2 = totalDmg;
        Damage dmg3 = totalDmg;

        // --- ZUCCHERO SINTATTICO: /= ---
        // Integer division floors automatically, just like D&D rules.
        if (save1 gte 14) {
            dmg1 /= 2;
        }
        if (save2 gte 14) {
            dmg2 /= 2;
        }
        if (save3 gte 14) {
            dmg3 /= 2;
        }

        g1.hp -= dmg1;
        g2.hp -= dmg2;
        g3.hp -= dmg3;

        narrate i"${g1.name} takes ${dmg1} damage (save: ${save1}) – HP left: ${g1.hp}";
        narrate i"${g2.name} takes ${dmg2} damage (save: ${save2}) – HP left: ${g2.hp}";
        narrate i"${g3.name} takes ${dmg3} damage (save: ${save3}) – HP left: ${g3.hp}";

        // Consume one spell slot
        // --- DECREMENTO POST ---
        caster.spell_slots--;
        narrate i"Spell slots remaining: ${caster.spell_slots}";
    }

quest:
{
    Hero hero = summon Hero();
    hero.hp = (HP) 40;
    hero.level = (Level) 5;
    hero.spell_slots = 2;
    hero.name = "Elminster Aumar";

    Goblin g1 = summon Goblin();
    g1.hp = (HP) 18;
    g1.dex = 1;
    g1.name = "Gruk";

    Goblin g2 = summon Goblin();
    g2.hp = (HP) 18;
    g2.dex = 3;
    g2.name = "Skab";

    Goblin g3 = summon Goblin();
    g3.hp = (HP) 18;
    g3.dex = 2;
    g3.name = "Morg";

    narrate "========================================";
    narrate "        LET THE BATTLE BEGIN!           ";
    narrate "========================================";
    narrate "";

    // -------------------------------------------------------
    // FOR con BREAK ed ELSE
    //
    // The hero has a limited number of rounds (MAX_ROUNDS = 5)
    // to kill all three goblins using Fireball.
    // - break: exits early if all goblins are dead before time runs out.
    // - else:  fires if the loop completes without a break
    //          (the hero failed to kill them all in time).
    // -------------------------------------------------------

    narrate "The hero has 5 rounds to defeat all goblins with Fireball!";
    narrate "";

    for round from 1 to 5 {

        narrate i"---------- Round ${round} ----------";

        // --- INCREMENTO PRE: boost dex of g1 each round (gets harder to burn) ---
        ++g1.dex;
        narrate i"${g1.name} braces for impact! (dex: ${g1.dex})";

        // Cast fireball if the hero still has spell slots
        if (hero.spell_slots gt 0) {
            cast fireball(hero, g1, g2, g3);
        }
        else {
            narrate "No spell slots left!";
        }

        narrate "";

        // Break early if all goblins are down
        if ((g1.hp lte 0) and (g2.hp lte 0) and (g3.hp lte 0)) {
            narrate "All goblins have fallen — victory before time ran out!";
            break;
        }
    }
    else {
        // Fired only if the loop completed all 5 rounds without a break
        narrate "========================================";
        narrate " The goblins survived all 5 rounds...";
        narrate " The hero has failed his quest!";
        narrate "========================================";
    }

    narrate "";
    narrate "===== Final Status =====";
    narrate i"Hero HP: ${hero.hp}  |  Spell slots left: ${hero.spell_slots}";
    narrate i"${g1.name} HP: ${g1.hp}";
    narrate i"${g2.name} HP: ${g2.hp}";
    narrate i"${g3.name} HP: ${g3.hp}";
}
```

---

### `ScopingParamTest.bag`

```
creatures:

    creature Hero{
        HP hp;

        void modifyObject(Hero hero){
            hero.hp = 99;
        }
    }

world:
    Int x = 1;

spellbook:

    spell Int getX() {
        return x;
    }

    spell void modifyPrimitive(Int n) {
        n = 99;
    }

    spell void modifyObject(Hero h) {
        h.hp = 99;
    }

quest:
{
    // --- TEST 1: dynamic scoping ---
    narrate cast getX();

    {
        Int x = 2;
        narrate cast getX();

        {
            Int x = 3;
            narrate cast getX();
        }

        narrate cast getX();
    }

    narrate cast getX();

    // --- TEST 2: pass-by-value ---
    Int y = 10;
    cast modifyPrimitive(y);
    narrate y;

    // --- TEST 3: pass-by-value on object ---
    Hero h = summon Hero();
    h.hp = 50;
    narrate h.hp;
    cast modifyObject(h);
    narrate h.hp;

}
```
Output: `1 2 3 2 1 10 50 99 `

### `DemoErrors.bag`

```
// ==================================================
//  ANTLR & DRAGONS — DemoErrors.bag
//  Runtime Error showcase for Antlr&Dragons
//
//  Features demonstrated:
//      - Division by zero
//      - Invalid user input (non-integer for Int type)
//      - Field access on a non-object value
// ==================================================

creatures:

    creature Warrior {
        HP hp;
        Damage atk;
        String name;
    }

spellbook:

    spell void demo_div_zero() {
        narrate "-- Scenario: A warrior tries to split the loot among 0 companions. --";
        Int loot   = 500;
        Int allies = 0;
        Int share  = loot / allies;
        narrate i"Each companion gets ${share} gold.";
    }

    spell void demo_bad_input() {
        narrate "-- Scenario: The dungeon master asks for the number of players. --";
        narrate "  (Tip: type something that is NOT a number, e.g. 'pippo')";
        Int players = declare(Int, "How many players are there?");
        narrate i"Party size: ${players}";
    }

    spell void demo_field_on_nonobject() {
        narrate "-- Scenario: A scribe tries to read the name of a plain number. --";
        Int treasure = 42;
        narrate i"Treasure name: ${treasure.name}";
    }


quest:
{
    narrate "========================================";
    narrate "   ANTLR & DRAGONS — Runtime Errors    ";
    narrate "========================================";
    narrate "";
    narrate "Choose a scenario to witness a runtime error:";
    narrate "  1. Division by zero";
    narrate "  2. Invalid user input";
    narrate "  3. Field access on a non-object";
    narrate "";

    Int choice = declare(Int, "Choose a number between [1-3]");

    narrate "";

    if (choice eq 1) {
        cast demo_div_zero();
    }
    else if (choice eq 2) {
        cast demo_bad_input();
    }
    else if (choice eq 3) {
        cast demo_field_on_nonobject();
    }
    else {
        narrate "Unknown scenario. Choose a number between 1 and 3.";
    }
}

```