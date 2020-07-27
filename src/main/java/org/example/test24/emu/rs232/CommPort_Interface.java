package org.example.test24.emu.rs232;

import org.example.test24.emu.CallBackFromRS232;

public interface CommPort_Interface {
    String[] getListPortsName();
    int Open(CallBackFromRS232 runner, String portName, BAUD baud);
    final int INITCODE_OK           = 0;
    final int INITCODE_NOTEXIST     = 1;
    final int INITCODE_ERROROPEN    = 2;
    void Close();
    boolean ReciveStart();
    void ReciveStop();
}
