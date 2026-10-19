/* Готовые примеры для урока. */
'use strict';

const num = (v) => `<shadow type="math_number"><field name="NUM">${v}</field></shadow>`;
const vget = (id, name) => `<block type="variables_get"><field name="VAR" id="${id}">${name}</field></block>`;
const move = (dir, steps, next = '') =>
  `<block type="bc_move"><field name="DIR">${dir}</field><value name="STEPS">${num(steps)}</value>${next ? `<next>${next}</next>` : ''}</block>`;
const turn = (dir, next = '') =>
  `<block type="bc_turn"><field name="DIR">${dir}</field>${next ? `<next>${next}</next>` : ''}</block>`;
const place = (block, next = '') =>
  `<block type="bc_place"><field name="BLOCK">${block}</field><field name="DIR">DOWN</field>${next ? `<next>${next}</next>` : ''}</block>`;
const cmp = (op, a, b) =>
  `<block type="logic_compare"><field name="OP">${op}</field><value name="A">${a}</value><value name="B">${b}</value></block>`;
const and = (a, b) =>
  `<block type="logic_operation"><field name="OP">AND</field><value name="A">${a}</value><value name="B">${b}</value></block>`;
const numBlock = (v) => `<block type="math_number"><field name="NUM">${v}</field></block>`;
const forLoop = (id, name, from, to, body, next = '') =>
  `<block type="controls_for"><field name="VAR" id="${id}">${name}</field>` +
  `<value name="FROM">${num(from)}</value><value name="TO">${num(to)}</value><value name="BY">${num(1)}</value>` +
  `<statement name="DO">${body}</statement>${next ? `<next>${next}</next>` : ''}</block>`;
const call = (name, next = '') =>
  `<block type="procedures_callnoreturn"><mutation name="${name}"></mutation>${next ? `<next>${next}</next>` : ''}</block>`;

/**
 * Домик 5×5: стены высотой 4 с дверным проёмом и крыша.
 * Та же программа лежит в тестах мода (house.json) — там проверено, что получается именно домик.
 */
const HOUSE_XML = `<xml xmlns="https://developers.google.com/blockly/xml">
<variables>
  <variable id="v_floor">этаж</variable>
  <variable id="v_side">сторона</variable>
  <variable id="v_step">шаг</variable>
  <variable id="v_row">ряд</variable>
</variables>

<block type="bc_on_start" x="30" y="30">
  <statement name="DO">
    <block type="bc_tp"><next>
    ${move('FORWARD', 2, move('UP', 1,
      forLoop('v_floor', 'этаж', 1, 4, call('слой', move('UP', 1)),
        call('крыша',
          `<block type="bc_say"><value name="TEXT"><shadow type="text"><field name="TEXT">Домик готов!</field></shadow></value></block>`))))}
    </next></block>
  </statement>
</block>

<block type="procedures_defnoreturn" x="30" y="330">
  <field name="NAME">слой</field>
  <comment pinned="false" h="80" w="260">Один ряд стен: 4 стороны по 4 блока. На первой стене, в первых двух этажах, оставляем дверь.</comment>
  <statement name="STACK">
    ${forLoop('v_side', 'сторона', 1, 4,
      forLoop('v_step', 'шаг', 1, 4,
        `<block type="controls_if">
           <value name="IF0">
             <block type="logic_negate"><value name="BOOL">
               ${and(and(cmp('EQ', vget('v_side', 'сторона'), numBlock(1)), cmp('EQ', vget('v_step', 'шаг'), numBlock(3))),
                     cmp('LTE', vget('v_floor', 'этаж'), numBlock(2)))}
             </value></block>
           </value>
           <statement name="DO0">${place('oak_planks')}</statement>
           <next>${move('FORWARD', 1)}</next>
         </block>`,
        turn('LEFT')))}
  </statement>
</block>

<block type="procedures_defnoreturn" x="560" y="330">
  <field name="NAME">крыша</field>
  <statement name="STACK">
    ${forLoop('v_row', 'ряд', 1, 5,
      `<block type="controls_repeat_ext">
         <value name="TIMES">${num(4)}</value>
         <statement name="DO">${place('spruce_planks', move('FORWARD', 1))}</statement>
         <next>${place('spruce_planks',
           `<block type="controls_if">
              <value name="IF0">${cmp('LT', vget('v_row', 'ряд'), numBlock(5))}</value>
              <statement name="DO0">
                <block type="controls_if">
                  <mutation else="1"></mutation>
                  <value name="IF0">${cmp('EQ',
                    `<block type="math_modulo"><value name="DIVIDEND">${vget('v_row', 'ряд')}</value><value name="DIVISOR">${num(2)}</value></block>`,
                    numBlock(1))}</value>
                  <statement name="DO0">${turn('LEFT', move('FORWARD', 1, turn('LEFT')))}</statement>
                  <statement name="ELSE">${turn('RIGHT', move('FORWARD', 1, turn('RIGHT')))}</statement>
                </block>
              </statement>
            </block>`)}</next>
       </block>`)}
  </statement>
</block>
</xml>`;

const STARTER_XML = `<xml xmlns="https://developers.google.com/blockly/xml">
<block type="bc_on_start" x="40" y="40">
  <statement name="DO">${move('FORWARD', 1)}</statement>
</block>
</xml>`;
