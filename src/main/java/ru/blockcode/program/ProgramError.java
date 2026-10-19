package ru.blockcode.program;

/** Ошибка в программе ученика. Сообщение показывается в чате, поэтому оно на русском. */
public class ProgramError extends RuntimeException {
    public ProgramError(String message) { super(message); }
}
