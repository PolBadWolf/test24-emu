package org.example.test24.emu;

public enum Status {
    SEND2PC_MALARM      ((byte) 0),
    SEND2PC_MBACK       ((byte) 1),
    SEND2PC_STOP        ((byte) 2),
    SEND2PC_MFORWARD    ((byte) 3),
    SEND2PC_MSHELF      ((byte) 4),
    SEND2PC_CALARM      ((byte) 5),
    SEND2PC_CBACK       ((byte) 6),
    SEND2PC_CDELAY      ((byte) 7),
    SEND2PC_CFORWARD    ((byte) 8),
    SEND2PC_CSHELF      ((byte) 9),
    SEND2PC_DATA        ((byte)11),
    SEND2PC_VES         ((byte)12),
    SEND2PC_MDATA       ((byte)14),
    SEND2PC_CDATA       ((byte)15);

    private byte stat;

    Status(byte stat) {
        this.stat = stat;
    }

    byte getStat() {
        return stat;
    }
}
