package org.example.test24.emu.rs232;

public enum BAUD {
    baud300         (300),
    baud600         (600),
    baud1200        (1200),
    baud2400        (2400),
    baud4800        (4800),
    baud9600        (9600),
    baud14400       (14400),
    baud19200       (19200),
    baud28800       (28800),
    baud38400       (38400),
    baud56000       (56000),
    baud57600       (57600),
    baud115200      (115200),
    baud250000      (250000);

    private int baud;

    BAUD(int baud) {
        this.baud = baud;
    }

    public int getBaud() {
        return baud;
    }
}
