package org.example.test24.emu;

import org.example.test24.emu.lib.ControlSumma;
import org.example.test24.emu.lib.ConvertDigit;
import org.example.test24.emu.rs232.BAUD;
import org.example.test24.emu.rs232.CommPort;

import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainClass implements CallBackFromRS232 {
    private CommPort commPort = null;
    private final BlockingQueue readQueue = new ArrayBlockingQueue(10);
    private final boolean[] readFlagOn = {true};
    private BufferedReader reader = null;
    private AtomicInteger tik = new AtomicInteger(0);

    public static void main(String[] args) {
        new MainClass().start(args);
    }

    private void start(String[] args) {
        String namePort = "";
        commPort = new CommPort();

        if (args.length < 2) {
            System.out.println("small arguments");
            initErrorCommMessage(CommPort.INITCODE_NOTEXIST, commPort);
            System.exit(1);
        }

        namePort = args[0];
        int checkComm = commPort.Open(this, namePort, BAUD.baud115200);
        if (checkComm != CommPort.INITCODE_OK) {
            initErrorCommMessage(checkComm, commPort);
            System.exit(1);
        }

        System.out.println("порт \"" + namePort + "\" открыт успешно");

        File file = null;
        FileReader fileReader = null;
        try {
            file = new File(args[1]);
            fileReader = new FileReader(file);
            reader = new BufferedReader(fileReader);
        }
        catch (FileNotFoundException e) {
            System.out.println("файл \"" + file.getName() + "\" не найден");
            System.exit(1);
        }
        catch (java.lang.Throwable e) {
            e.printStackTrace();
            System.out.println("х.з.");
            System.exit(1);
        }

        new Thread(()->run()).start();
        readQueue.add(this);
        readQueue.add(this);
        readQueue.add(this);
    }

    private void initErrorCommMessage(int checkComm, CommPort commPort) {
        switch (checkComm) {
            case CommPort.INITCODE_ERROROPEN:
                System.out.println("ошибка открытия порта");
                break;
            case CommPort.INITCODE_NOTEXIST:
                System.out.println("указанный порт отсутствует");
                System.out.println("имеющиеся порты в системе:");
                for (String name : commPort.getListPortsName()) {
                    System.out.println(name);
                }
                break;
        }
    }

    private void run() {
        byte[] header = { (byte) 0xe6, (byte) 0x19, (byte) 0x55, (byte) 0xaa };
        byte[] bodyStat = new byte[6];
        byte[] bodyCurrentDat = new byte[10];
        byte[] bodyTotalDat = new byte[30];

        try {
            while (readFlagOn[0]) {
                Object x = readQueue.poll(4, TimeUnit.MILLISECONDS);
                if (x == null)  continue;
                String string = reader.readLine();
                if (string == null) {
                    break;
                }
                String[] subStrings = string.split(" ");

                switch (subStrings[0].toLowerCase()) {
                    case "sf":
                        bodyStat[0] = Status.SEND2PC_MFORWARD.getStat();
                        int tikCur = tik.get();
                        ConvertDigit.Int2bytes(tikCur, bodyStat, 1);
                        bodyStat[5] = ControlSumma.crc8(bodyStat, 5);
                        commPort.writeBlock();
                        break;
                    case "current":
                        break;
                    case "total":
                        break;
                    default:
                        break;
                }
                double zn = Double.parseDouble(subStrings[0]);
                double tm = Double.parseDouble(subStrings[1]);
            }
            readFlagOn[0] = false;
        } catch (InterruptedException e) {
            e.printStackTrace(); // queue poll
        } catch (IOException e) {
            e.printStackTrace(); // read line
        }
        catch (java.lang.Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void reciveRsPush(byte[] bytes, int lenght) {

    }

    private enum Status {
        SEND2PC_MALARM      ((byte) 0),
        SEND2PC_MBACK       ((byte) 1),
        SEND2PC_STOP        ((byte) 2),
        SEND2PC_MFORWARD    ((byte) 3),
        SEND2PC_MSHELF      ((byte) 4),
        SEND2PC_DATA        ((byte)11),
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
}
