/* BlockCode: блоки агента, генерация Python и сборка программы для игры. */
'use strict';

// Тот же список проверяет сервер (AllowedBlocks.java)
const BLOCK_CHOICES = [
  ['дубовые доски', 'oak_planks'], ['еловые доски', 'spruce_planks'], ['берёзовые доски', 'birch_planks'],
  ['доски тёмного дуба', 'dark_oak_planks'], ['дубовое бревно', 'oak_log'], ['еловое бревно', 'spruce_log'],
  ['булыжник', 'cobblestone'], ['камень', 'stone'], ['каменные кирпичи', 'stone_bricks'], ['кирпичи', 'bricks'],
  ['песчаник', 'sandstone'], ['кварцевый блок', 'quartz_block'], ['стекло', 'glass'], ['стеклянная панель', 'glass_pane'],
  ['белая шерсть', 'white_wool'], ['красная шерсть', 'red_wool'], ['синяя шерсть', 'blue_wool'], ['жёлтая шерсть', 'yellow_wool'],
  ['листва дуба', 'oak_leaves'], ['земля', 'dirt'], ['блок травы', 'grass_block'], ['песок', 'sand'],
  ['дубовый забор', 'oak_fence'], ['факел', 'torch'], ['светокамень', 'glowstone'], ['морской фонарь', 'sea_lantern'],
];
const MOVE_DIRS = [['вперёд', 'FORWARD'], ['назад', 'BACK'], ['влево', 'LEFT'], ['вправо', 'RIGHT'], ['вверх', 'UP'], ['вниз', 'DOWN']];
const BLOCK_DIRS = [['вниз', 'DOWN'], ['вперёд', 'FORWARD'], ['назад', 'BACK'], ['влево', 'LEFT'], ['вправо', 'RIGHT'], ['вверх', 'UP']];
const AGENT_COLOUR = '#4C97FF';
const START_COLOUR = '#FFAB19';

// ------------------------------------------------------------------ совместимость версий Blockly
const PY = (window.python && window.python.pythonGenerator) || Blockly.Python;
const ORDER = (window.python && window.python.Order) || {
  ATOMIC: PY.ORDER_ATOMIC, NONE: PY.ORDER_NONE, FUNCTION_CALL: PY.ORDER_FUNCTION_CALL,
};
function pyGen(type, fn) {
  if (PY.forBlock) PY.forBlock[type] = fn; else PY[type] = (block) => fn(block, PY);
}

// Python 3 разрешает русские имена — оставляем «этаж», «слой» как есть, а не «_D1_8D…»
(function keepUnicodeNames() {
  const proto = Blockly.Names && Blockly.Names.prototype;
  if (!proto || !proto.safeName) return;
  proto.safeName = function (name) {
    if (!name) return 'безымянное';
    let n = String(name).replace(/[^\p{L}\p{N}_]/gu, '_');
    if (/^\p{N}/u.test(n)) n = '_' + n;
    return n;
  };
})();

// ------------------------------------------------------------------ блоки
Blockly.defineBlocksWithJsonArray([
  {
    type: 'bc_on_start',
    message0: '▶ при запуске',
    message1: '%1',
    args1: [{ type: 'input_statement', name: 'DO' }],
    colour: START_COLOUR,
    tooltip: 'Программа начинается здесь. Нажми «Запустить».',
  },
  {
    type: 'bc_move',
    message0: 'агент: лететь %1 на %2',
    args0: [
      { type: 'field_dropdown', name: 'DIR', options: MOVE_DIRS },
      { type: 'input_value', name: 'STEPS', check: 'Number' },
    ],
    inputsInline: true, previousStatement: null, nextStatement: null, colour: AGENT_COLOUR,
    tooltip: 'Агент перемещается на столько блоков. Сквозь блоки он не пролетает.',
  },
  {
    type: 'bc_turn',
    message0: 'агент: повернуть %1',
    args0: [{ type: 'field_dropdown', name: 'DIR', options: [['налево', 'LEFT'], ['направо', 'RIGHT']] }],
    previousStatement: null, nextStatement: null, colour: AGENT_COLOUR,
  },
  {
    type: 'bc_place',
    message0: 'агент: поставить %1 %2',
    args0: [
      { type: 'field_dropdown', name: 'BLOCK', options: BLOCK_CHOICES },
      { type: 'field_dropdown', name: 'DIR', options: BLOCK_DIRS },
    ],
    previousStatement: null, nextStatement: null, colour: AGENT_COLOUR,
    tooltip: 'Ставит блок рядом с агентом. Чаще всего — «вниз», под себя.',
  },
  {
    type: 'bc_destroy',
    message0: 'агент: сломать блок %1',
    args0: [{ type: 'field_dropdown', name: 'DIR', options: BLOCK_DIRS }],
    previousStatement: null, nextStatement: null, colour: AGENT_COLOUR,
  },
  {
    type: 'bc_detect',
    message0: 'агент видит блок %1',
    args0: [{ type: 'field_dropdown', name: 'DIR', options: MOVE_DIRS }],
    output: 'Boolean', colour: AGENT_COLOUR,
    tooltip: 'Правда, если в этом направлении есть твёрдый блок.',
  },
  {
    type: 'bc_tp',
    message0: 'агент: лететь ко мне',
    previousStatement: null, nextStatement: null, colour: AGENT_COLOUR,
  },
  {
    type: 'bc_say',
    message0: 'сказать в чат %1',
    args0: [{ type: 'input_value', name: 'TEXT' }],
    previousStatement: null, nextStatement: null, colour: '#9966FF',
  },
]);

// ------------------------------------------------------------------ Python (как в MakeCode)
const val = (b, g, name, def) => g.valueToCode(b, name, ORDER.NONE) || def;

pyGen('bc_on_start', (b, g) => {
  const body = g.statementToCode(b, 'DO') || g.INDENT + 'pass\n';
  return 'def on_start():\n' + body + '\non_start()\n';
});
pyGen('bc_move', (b, g) => `agent.move(${b.getFieldValue('DIR')}, ${val(b, g, 'STEPS', '1')})\n`);
pyGen('bc_turn', (b) => `agent.turn(${b.getFieldValue('DIR')}_TURN)\n`);
pyGen('bc_place', (b) => `agent.place(${b.getFieldValue('DIR')}, ${b.getFieldValue('BLOCK').toUpperCase()})\n`);
pyGen('bc_destroy', (b) => `agent.destroy(${b.getFieldValue('DIR')})\n`);
pyGen('bc_detect', (b) => [`agent.detect(${b.getFieldValue('DIR')})`, ORDER.FUNCTION_CALL]);
pyGen('bc_tp', () => 'agent.teleport_to_player()\n');
pyGen('bc_say', (b, g) => `player.say(${val(b, g, 'TEXT', '""')})\n`);

// ------------------------------------------------------------------ панель блоков
const n = (v) => ({ shadow: { type: 'math_number', fields: { NUM: v } } });
const TOOLBOX = {
  kind: 'categoryToolbox',
  contents: [
    { kind: 'category', name: 'Агент', colour: AGENT_COLOUR, contents: [
      { kind: 'block', type: 'bc_on_start' },
      { kind: 'block', type: 'bc_move', inputs: { STEPS: n(1) } },
      { kind: 'block', type: 'bc_turn' },
      { kind: 'block', type: 'bc_place' },
      { kind: 'block', type: 'bc_destroy' },
      { kind: 'block', type: 'bc_detect' },
      { kind: 'block', type: 'bc_tp' },
      { kind: 'block', type: 'bc_say', inputs: { TEXT: { shadow: { type: 'text', fields: { TEXT: 'Привет!' } } } } },
    ] },
    { kind: 'category', name: 'Циклы', colour: '#FFAB19', contents: [
      { kind: 'block', type: 'controls_repeat_ext', inputs: { TIMES: n(4) } },
      { kind: 'block', type: 'controls_for', inputs: { FROM: n(1), TO: n(5), BY: n(1) } },
      { kind: 'block', type: 'controls_whileUntil' },
    ] },
    { kind: 'category', name: 'Логика', colour: '#59C059', contents: [
      { kind: 'block', type: 'controls_if' },
      { kind: 'block', type: 'controls_if', extraState: { hasElse: true } },
      { kind: 'block', type: 'logic_compare' },
      { kind: 'block', type: 'logic_operation' },
      { kind: 'block', type: 'logic_negate' },
      { kind: 'block', type: 'logic_boolean' },
    ] },
    { kind: 'category', name: 'Математика', colour: '#40BF4A', contents: [
      { kind: 'block', type: 'math_number' },
      { kind: 'block', type: 'math_arithmetic', inputs: { A: n(1), B: n(1) } },
      { kind: 'block', type: 'math_modulo', inputs: { DIVIDEND: n(7), DIVISOR: n(2) } },
      { kind: 'block', type: 'math_random_int', inputs: { FROM: n(1), TO: n(10) } },
      { kind: 'block', type: 'text' },
    ] },
    { kind: 'category', name: 'Переменные', colour: '#FF8C1A', custom: 'VARIABLE' },
    { kind: 'category', name: 'Функции', colour: '#FF6680', custom: 'PROCEDURE' },
  ],
};

// ------------------------------------------------------------------ блоки → программа для игры
class CompileError extends Error {
  constructor(message, block) { super(message); this.block = block; }
}

const enabled = (b) => !b.isEnabled || b.isEnabled();
const varName = (b, field) => b.getField(field).getText();

function compileStatements(first) {
  const out = [];
  for (let b = first; b; b = b.getNextBlock()) {
    if (enabled(b)) out.push(compileStatement(b));
  }
  return out;
}

function expr(b, input, def) {
  const t = b.getInputTargetBlock(input);
  return t && enabled(t) ? compileExpr(t) : def;
}
const NUM = (v) => ({ t: 'num', v });

function compileStatement(b) {
  const body = (name) => compileStatements(b.getInputTargetBlock(name));
  switch (b.type) {
    case 'bc_move': return { t: 'move', dir: b.getFieldValue('DIR'), n: expr(b, 'STEPS', NUM(1)) };
    case 'bc_turn': return { t: 'turn', dir: b.getFieldValue('DIR') };
    case 'bc_place': return { t: 'place', dir: b.getFieldValue('DIR'), block: b.getFieldValue('BLOCK') };
    case 'bc_destroy': return { t: 'destroy', dir: b.getFieldValue('DIR') };
    case 'bc_tp': return { t: 'tp' };
    case 'bc_say': return { t: 'say', v: expr(b, 'TEXT', { t: 'text', v: '' }) };
    case 'controls_repeat_ext': return { t: 'repeat', n: expr(b, 'TIMES', NUM(0)), body: body('DO') };
    case 'controls_repeat': return { t: 'repeat', n: NUM(Number(b.getFieldValue('TIMES'))), body: body('DO') };
    case 'controls_for':
      return { t: 'for', var: varName(b, 'VAR'), from: expr(b, 'FROM', NUM(0)), to: expr(b, 'TO', NUM(0)),
               by: expr(b, 'BY', NUM(1)), body: body('DO') };
    case 'controls_whileUntil':
      return { t: 'while', until: b.getFieldValue('MODE') === 'UNTIL', cond: expr(b, 'BOOL', { t: 'bool', v: false }), body: body('DO') };
    case 'controls_if': {
      const branches = [];
      for (let i = 0; b.getInput('IF' + i); i++) {
        branches.push({ cond: expr(b, 'IF' + i, { t: 'bool', v: false }), body: body('DO' + i) });
      }
      return { t: 'if', branches, else: b.getInput('ELSE') ? body('ELSE') : null };
    }
    case 'variables_set': return { t: 'set', var: varName(b, 'VAR'), v: expr(b, 'VALUE', NUM(0)) };
    case 'math_change': return { t: 'change', var: varName(b, 'VAR'), v: expr(b, 'DELTA', NUM(1)) };
    case 'procedures_callnoreturn': return { t: 'call', name: b.getFieldValue('NAME') };
    case 'bc_on_start':
      throw new CompileError('Блок «при запуске» должен быть один и стоять отдельно', b);
    default:
      throw new CompileError('Этот блок пока нельзя запускать в игре', b);
  }
}

function compileExpr(b) {
  switch (b.type) {
    case 'math_number': return NUM(Number(b.getFieldValue('NUM')));
    case 'text': return { t: 'text', v: b.getFieldValue('TEXT') };
    case 'logic_boolean': return { t: 'bool', v: b.getFieldValue('BOOL') === 'TRUE' };
    case 'variables_get': return { t: 'var', name: varName(b, 'VAR') };
    case 'bc_detect': return { t: 'detect', dir: b.getFieldValue('DIR') };
    case 'logic_negate': return { t: 'not', a: expr(b, 'BOOL', { t: 'bool', v: false }) };
    case 'logic_compare':
      return { t: 'cmp', op: b.getFieldValue('OP'), a: expr(b, 'A', NUM(0)), b: expr(b, 'B', NUM(0)) };
    case 'logic_operation':
      return { t: 'logic', op: b.getFieldValue('OP'), a: expr(b, 'A', { t: 'bool', v: false }), b: expr(b, 'B', { t: 'bool', v: false }) };
    case 'math_arithmetic':
      return { t: 'arith', op: b.getFieldValue('OP'), a: expr(b, 'A', NUM(0)), b: expr(b, 'B', NUM(0)) };
    case 'math_modulo':
      return { t: 'arith', op: 'MOD', a: expr(b, 'DIVIDEND', NUM(0)), b: expr(b, 'DIVISOR', NUM(1)) };
    case 'math_random_int': return { t: 'rand', from: expr(b, 'FROM', NUM(1)), to: expr(b, 'TO', NUM(10)) };
    default:
      throw new CompileError('Этот блок пока нельзя использовать в программе для агента', b);
  }
}

/** Собрать программу из рабочей области. Ошибки — понятным языком и с подсветкой блока. */
function compileWorkspace(ws) {
  const tops = ws.getTopBlocks(true).filter(enabled);
  const starts = tops.filter((b) => b.type === 'bc_on_start');
  if (starts.length === 0) throw new CompileError('Добавь блок «▶ при запуске» из раздела «Агент» и вставь в него команды');
  if (starts.length > 1) throw new CompileError('Блок «при запуске» должен быть только один', starts[1]);

  const procs = {};
  for (const b of tops) {
    if (b.type === 'procedures_defnoreturn') {
      if (b.getVars && b.getVars().length) throw new CompileError('Функции с параметрами пока не поддерживаются', b);
      procs[b.getFieldValue('NAME')] = compileStatements(b.getInputTargetBlock('STACK'));
    } else if (b.type === 'procedures_defreturn') {
      throw new CompileError('Функции, которые возвращают значение, пока не поддерживаются', b);
    }
  }
  const main = compileStatements(starts[0].getInputTargetBlock('DO'));
  if (main.length === 0) throw new CompileError('Внутри «при запуске» пока пусто — добавь команды для агента', starts[0]);
  return { v: 1, main, procs };
}
