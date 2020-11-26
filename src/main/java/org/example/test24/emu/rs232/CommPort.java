package org.example.test24.emu.rs232;

import com.fazecast.jSerialComm.SerialPort;
import org.example.test24.emu.CallBackFromRS232;
import org.example.test24.emu.lib.ControlSumma;

public class CommPort implements CommPort_Interface {
    @Override
    public String[] getListPortsName() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] namePorts = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            namePorts[i] = ports[i].getSystemPortName().toUpperCase();
        }
        return namePorts;
    }

    private SerialPort port = null;
    private Thread threadRS = null;
    private CallBackFromRS232 runner_interface;

    @Override
    public int Open(CallBackFromRS232 runner, String portName, BAUD baud) {
        if (port != null) {
            Close();
        }

        boolean flagTmp = false;
        String[] portsName = getListPortsName();
        String portNameCase = portName.toUpperCase();
        for (int i = 0; i < portsName.length; i++) {
            if (portsName[i].equals(portNameCase))  {
                flagTmp = true;
                break;
            }
        }

        if (!flagTmp)   return INITCODE_NOTEXIST;

        port = SerialPort.getCommPort(portNameCase);
        port.setComPortParameters(baud.getBaud(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 100, 2);

        if (port.openPort()) {
            runner_interface = runner;
            return INITCODE_OK;
        }

        return INITCODE_ERROROPEN;
    }

    @Override
    public void Close() {
        if (port == null)   return;

        ReciveStop();

        port.closePort();
        port = null;
    }

    @Override
    public boolean ReciveStart() {
        if (port == null)   return false;
        if (!port.isOpen()) return false;

        threadRS = new Thread( ()->runnerReciver() );
        threadRS.start();
        return false;
    }

    @Override
    public void ReciveStop() {
        onCycle = false;

        try {
            if (threadRS != null) {
                while (!threadRS.isAlive()) {
                    Thread.yield();
                }
            }
        }
        catch (java.lang.Throwable th) {
            th.printStackTrace();
        }
    }

    // ---------------------
    final private int headBufferLenght  = 5;
    final private int timeOutLenght     = 5;
    // ---------------------
    private int timeOutSynhro = 1;
    private boolean noSynhro = true;
    private boolean flagHead = true;
    private byte[]  headBuffer = new byte[headBufferLenght];
    private int lenghtRecive;
    private int lenghtReciveSumm;
    private byte crc;
    // ================================================
    //                режим работы
    private static final int reciveMode_SYNHRO = 0;
    private static final int reciveMode_LENGHT = 1;
    private static final int reciveMode_BODY = 2;
    private static final int reciveMode_OUT = 3;
    private int reciveMode = reciveMode_SYNHRO;
    // ---------------------
    //        SYNHRO
    private static final int reciveHeader_lenght = 4;
    private byte[] reciveHeader = new byte[reciveHeader_lenght];
    private byte[] reciveHeader_in = new byte[1];
    // ---------------------
    //        LENGHT
    private int reciveBody_lenght;
    // ---------------------
    private byte[] reciveBody_Buffer = new byte[256];
    private int reciveBody_Index;
    // ---------------------
    private boolean onCycle;
    int recive_num;
    // ================================================
    private void runnerReciver() {
        onCycle = true;
        reciveMode = reciveMode_SYNHRO;
        recive_num = 0;
        try {
            while (onCycle) {
                if (recive_num == 0) {
                    Thread.sleep(1);
                }
                switch (reciveMode) {
                    case reciveMode_SYNHRO:
                        recive_synhro();
                        break;
                    case reciveMode_LENGHT:
                        recive_lenght();
                        break;
                    case reciveMode_BODY:
                        recive_body();
                        break;
                    case reciveMode_OUT:
                        recive_out();
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + reciveMode);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void recive_synhro() throws Exception {
        recive_num = port.readBytes(reciveHeader_in, 1);
        if (recive_num == 0) return;
        // shift
        for (int i = 0; i < reciveHeader_lenght - 1; i++) {
            reciveHeader[i] = reciveHeader[i + 1];
        }
        // new byte
        reciveHeader[reciveHeader_lenght - 1] = reciveHeader_in[0];
        // check
        if ((reciveHeader[0] & 0xff) != 0xe6) return;
        if ((reciveHeader[1] & 0xff) != 0x19) return;
        if ((reciveHeader[2] & 0xff) != 0x55) return;
        if ((reciveHeader[3] & 0xff) != 0xaa) return;
        // ok
        reciveMode = reciveMode_LENGHT;
    }
    private void recive_lenght() throws Exception {
        recive_num = port.readBytes(reciveHeader_in, 1);
        if (recive_num == 0) return;
        reciveBody_lenght = reciveHeader_in[0] & 0xff;
        reciveBody_Index = 0;
        reciveMode = reciveMode_BODY;
    }
    private void recive_body() throws Exception {
        int lenght = reciveBody_lenght - reciveBody_Index;
        recive_num = port.readBytes(reciveBody_Buffer, lenght, reciveBody_Index);
        if (recive_num == 0) return;
        reciveBody_Index += recive_num;
        if (reciveBody_Index > reciveBody_lenght) throw new Exception("переполнение буффера приема");
        if (reciveBody_Index < reciveBody_lenght) return;
        reciveMode = reciveMode_OUT;
    }
    private void recive_out() throws Exception {
        if (ControlSumma.crc8(reciveBody_Buffer, reciveBody_lenght - 1) == reciveBody_Buffer[reciveBody_lenght - 1]) {
            if (runner_interface != null) {
                runner_interface.reciveRsPush(reciveBody_Buffer, reciveBody_lenght - 1);
            }
        }
        reciveMode = reciveMode_SYNHRO;
    }

    public void writeBlock(byte[] bytes) {
        port.writeBytes(bytes, bytes.length);
    }
}
