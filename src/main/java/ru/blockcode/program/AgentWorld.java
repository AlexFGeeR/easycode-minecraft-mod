package ru.blockcode.program;

/**
 * Всё, что программа может сделать в мире. Интерпретатор знает только этот интерфейс,
 * поэтому его можно проверять без Minecraft (см. тесты).
 */
public interface AgentWorld {

    enum Dir { FORWARD, BACK, LEFT, RIGHT, UP, DOWN }

    /** Сдвинуть агента на 1 блок. false — путь занят. */
    boolean move(Dir dir);

    /** Повернуть агента налево или направо на 90°. */
    void turn(boolean left);

    /** Поставить блок рядом с агентом. false — место занято или блок не разрешён. */
    boolean place(Dir dir, String blockId);

    /** Сломать блок рядом с агентом (без выпадения предметов). */
    boolean destroy(Dir dir);

    /** Есть ли твёрдый блок в этом направлении. */
    boolean detect(Dir dir);

    /** Переместить агента к игроку. */
    void teleportToPlayer();

    /** Сообщение в чат от имени агента. */
    void say(String text);
}
