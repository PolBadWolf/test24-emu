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
    //private final BlockingQueue readQueue = new ArrayBlockingQueue(10);
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
        System.out.println("файл \"" + file.getName() + "\" успешно открыт");

        Thread mainThread = new Thread(()->run());
        mainThread.start();

        try {
            while (mainThread.isAlive()) {
                Thread.sleep(10);
                tik.addAndGet(1);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        commPort.Close();
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
        //readQueue.add(this);
        //readQueue.add(this);
        //readQueue.add(this);
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
        boolean newCommand = true;
        String[] subStrings = {""};
        int tikSample = 0;
        int tikOld = 0;
        double distSample = 0;
        double distOld = 0;
        byte[] header = { (byte) 0xe6, (byte) 0x19, (byte) 0x55, (byte) 0xaa };
        byte[] bodyStat = new byte[6];
        byte[] bodyCurrentDat = new byte[10];
        byte[] bodyTotalDat = new byte[30];
        boolean flagSendOff = false;

        try {
            while (readFlagOn[0]) {
                Thread.sleep(1);
                if (newCommand) {
                    //Object x = readQueue.poll(1, TimeUnit.MILLISECONDS);
                    //if (x == null)  continue;
                    String string = reader.readLine();
                    if (string == null) {
                        break;
                    }
                    subStrings = string.split(" ");

                    switch (subStrings[0].toLowerCase()) {
                        case "smf":
                            bodyStat[0] = Status.SEND2PC_MFORWARD.getStat();
                            tikSample = Integer.parseInt(subStrings[1]);
                            tik.set(tikSample);
                            distSample = -1000;
                            break;
                        case "sms":
                            bodyStat[0] = Status.SEND2PC_MSHELF.getStat();
                            tikSample = Integer.parseInt(subStrings[1]);
                            break;
                        case "smb":
                            bodyStat[0] = Status.SEND2PC_MBACK.getStat();
                            tikSample = Integer.parseInt(subStrings[1]);
                            break;
                        case "dc":
                            tikOld = tikSample;
                            distOld = distSample;
                            bodyCurrentDat[0] = Status.SEND2PC_DATA.getStat();
                            tikSample = Integer.parseInt(subStrings[1]);
                            distSample = Double.parseDouble(subStrings[2]);
                            if (distOld == -1000) distOld = distSample;
                            break;
                        case "stop":
                            bodyStat[0] = Status.SEND2PC_STOP.getStat();
                            tikSample = Integer.parseInt(subStrings[1]);
                            break;
                        case "total":
                            break;
                        default:
                            break;
                    }
                    newCommand = false;
                }
                else {
                    int tikCurrent = tik.get();
                    if ((tikCurrent % 5) > 0) {
                        flagSendOff = false;
                        continue;
                    }

                    if (flagSendOff)    {
                        continue;
                    }

                    if (subStrings[0].toLowerCase().equals("dc")) {
                        flagSendOff = true;
                        double distCurrent = 0;
                        int tikRazn = tikSample - tikOld;
                        if (tikRazn == 0) {
                            distCurrent = distSample;
                        } else {
                            double distRazn = distSample - distOld;
                            distCurrent = (distRazn / tikRazn * (tikCurrent - tikOld)) + distOld;
                        }
                        ConvertDigit.Int2bytes(tikCurrent, bodyCurrentDat, 1);
                        ConvertDigit.Int2bytes((int)distCurrent, bodyCurrentDat, 5, 2);
                        ConvertDigit.Int2bytes(0, bodyCurrentDat, 7, 2);    // ves
                        bodyCurrentDat[9] = ControlSumma.crc8(bodyCurrentDat, bodyCurrentDat.length - 1);
                        commPort.writeBlock(header);
                        commPort.writeBlock(new byte[] {(byte) bodyCurrentDat.length});
                        commPort.writeBlock(bodyCurrentDat);
                    }

                    if (tikCurrent <tikSample)  continue;
                    newCommand = true;

                    switch (subStrings[0].toLowerCase()) {
                        case "stop":
                        case "sms":
                        case "smf":
                            ConvertDigit.Int2bytes(tikSample, bodyStat, 1);
                            bodyStat[5] = ControlSumma.crc8(bodyStat, bodyStat.length - 1);
                            commPort.writeBlock(header);
                            commPort.writeBlock(new byte[] { (byte) bodyStat.length });
                            commPort.writeBlock(bodyStat);
                            break;
                    }
                }
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
