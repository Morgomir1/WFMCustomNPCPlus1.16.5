# Nashorn Java↔JS Reference — CustomNPC+ контекст

Справочник по interop Nashorn. События и API CustomNPC+ — в skill `customnpc-js-scripting` и его `reference.md`.

---

## Как Nashorn вызывает Java из JS

Nashorn автоматически конвертирует аргументы JS → Java по правилам ECMAScript «ToNumber», «ToString» и т.д. При вызове метода Java-объекта из JS:

- Примитивы JS (`number`, `string`, `boolean`) → соответствующие Java-типы или `Object`.
- JS-объекты → Java Map-подобные структуры или адаптеры.
- Java-объекты, переданные в JS, остаются Java-объектами (можно вызывать `.getClass()`, `.method()`).

**Ловушка:** если параметр Java-метода объявлен как `java.lang.Object`, строка из JS может прийти как `CharSequence`, число — как `Number`. Для строгих типов (`String`, `int`) Nashorn конвертирует явно.

---

## Java.type

Основной способ получить Java-класс в Nashorn:

```javascript
var ArrayList = Java.type("java.util.ArrayList");
var list = new ArrayList();
list.add("hello");

// массивы
var IntArray = Java.type("int[]");
var arr = new IntArray(10);
arr[0] = 42;

// вложенные классы
var FloatType = Java.type("java.awt.geom.Arc2D$Float");
```

В CustomNPC+ предпочитай API-обёртки. `Java.type` — для классов мода, когда wrapper недоступен:

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI");
var api = NpcAPI.Instance();
var entity = api.getIEntity(someMcEntity);
```

---

## Packages / javax / java

Краткие алиасы (если Nashorn их экспонирует в global):

```javascript
var HashMap = Java.type("java.util.HashMap");
// или
var map = new java.util.HashMap();
```

В скриптах CustomNPC+ **обычно не нужны** — используй `event.world`, `event.npc`, `event.player`.

---

## JavaImporter + with

Для локального импорта пакетов без загрязнения global scope:

```javascript
function readSomething() {
    with (new JavaImporter(java.io)) {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        // ...
    }
}
```

Редко используется в NPC-скриптах WFM.

---

## SAM-типы (Single Abstract Method)

Nashorn может передать JS-функцию туда, где Java ожидает интерфейс с одним абстрактным методом:

```javascript
var Timer = Java.type("java.util.Timer");
var timer = new Timer();
timer.schedule(function() {
    log("delayed");
}, 1000);
```

В CustomNPC+ таймеры NPC (`getTimers().start`) предпочтительнее `java.util.Timer` — они привязаны к lifecycle NPC.

---

## new AbstractClass { method: function() {} }

Nashorn-расширение для анонимных Java-подклассов:

```javascript
var Runnable = Java.type("java.lang.Runnable");
var r = new Runnable({
    run: function() { log("run"); }
});
```

Аналогичный синтаксис с `{ run: function() {} }` после `new Type`.

---

## Java.extend

Для наследования **неабстрактного** Java-класса:

```javascript
var ArrayList = Java.type("java.util.ArrayList");
var Ext = Java.extend(ArrayList);
var list = new Ext() {
    size: function() { return ArrayList.prototype.size.call(this); }
};
```

В WFM-скриптах почти не используется — проще `getMCEntity()` + прямой вызов MC API.

---

## Массивы Java из JS

```javascript
var StringArray = Java.type("java.lang.String[]");
var names = new StringArray(3);
names[0] = "a";
names[1] = "b";

// Iterable / Java arrays — for..in по индексам
for (var i in names) {
    log(names[i]);
}
```

CustomNPC `getNearbyEntities` возвращает JS-массив IEntity — используй обычный `for (var i = 0; i < arr.length; i++)`.

---

## Конвертация storeddata / tempdata

`IData.put(key, value)` принимает **number или string** (API CustomNPC+). При чтении:

```javascript
var v = data.get("count");     // может быть string "5"
var n = Number(v);             // явное приведение
if (isNaN(n)) n = 0;
data.put("count", n + 1);
```

---

## Обработка ошибок Nashorn

Nashorn расширяет `Error`:

| Свойство | Описание |
|---|---|
| `e.message` | текст ошибки |
| `e.stack` | JS stack trace |
| `e.lineNumber` | строка в скрипте |
| `e.columnNumber` | колонка |
| `e.fileName` | имя файла/eval |

```javascript
try {
    undefinedFunction();
} catch (e) {
    log(e.message);
    if (e.lineNumber) log("at line " + e.lineNumber);
    if (e.stack) log(e.stack);
}
```

`Error.dumpStack()` — полный Java stack текущего потока (глобальная функция Nashorn).

---

## Чего нет в Nashorn (не пытайся)

- ES6 `class`, modules, `import/export`
- Полноценные Promises / async-await
- Node.js API (`require`, `fs`, …)
- Browser DOM API
- GraalJS-only features (Nashorn ≠ GraalJS)

---

## Nashorn extensions (опционально)

Nashorn поддерживает расширения поверх ES5.1 ([OpenJDK Wiki](https://wiki.openjdk.org/spaces/Nashorn/pages/17957105/Nashorn+extensions)):

- `for each (x in arr)` — итерация по **значениям**
- conditional catch: `catch (e if e instanceof TypeError)`
- function expression closures: `function sqr(x) x*x`
- `-scripting` mode: heredoc `<<EOF`, `${expr}` в строках, `#` комментарии

CustomNPC+ **может не включать** `-scripting`. В WFM-скриптах используй только стандартный ES5.1 + `//` комментарии.

---

## ScriptEngine lifecycle (для понимания)

Псевдокод CustomNPC+ (не редактируется из JS):

```java
ScriptEngine engine = manager.getEngineByName("nashorn");
engine.eval(fullScriptText);                    // parse + compile once
((Invocable) engine).invokeFunction("tick", eventObject);
```

Implications:
- Синтаксическая ошибка в **любом месте** файла ломает весь скрипт при загрузке.
- Переопределение `function tick` при повторном `eval` заменяет функцию (но CustomNPC+ обычно eval один раз до reload).
- `load()` / `loadWithNewGlobal()` из Nashorn в GUI-скриптах **не используются** — один монолитный текст.

---

## Безопасность

Nashorn даёт полный доступ к JVM из скрипта (`Java.type("java.lang.Runtime")` и т.д.). На сервере WFM скрипты — **доверенный код** админов. Не выполняй недоверенный JS от игроков.
