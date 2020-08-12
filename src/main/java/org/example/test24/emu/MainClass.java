package org.example.test24.emu;

import org.example.test24.emu.lib.ControlSumma;
import org.example.test24.emu.lib.ConvertDigit;
import org.example.test24.emu.rs232.BAUD;
import org.example.test24.emu.rs232.CommPort;

import java.io.*;

public class MainClass implements CallBackFromRS232 {
    private CommPort commPort = null;
    //private final BlockingQueue readQueue = new ArrayBlockingQueue(10);
    private final boolean[] readFlagOn = {true};
    private BufferedReader reader = null;
    private int tik = 0;
    private double ves = 200;

    private int n_cycle = 0;
    private int countPack = 0;
    private File file = null;
    private FileReader fileReader = null;

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
        int checkComm = commPort.Open(this, namePort, BAUD.baud57600);
        if (checkComm != CommPort.INITCODE_OK) {
            initErrorCommMessage(checkComm, commPort);
            System.exit(1);
        }

        System.out.println("порт \"" + namePort + "\" открыт успешно");

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

        try {
            ves = Double.parseDouble(args[2]);
            System.out.println("принят вес " + ves);
        } catch (java.lang.Throwable e) {
            //e.printStackTrace();
            System.out.println("вес по умолчанию 200");
        }

        try {
            n_cycle = Integer.parseInt(args[3]);
            System.out.println("автоматический режим " + n_cycle + " циклов");
        } catch (java.lang.Throwable e) {
            System.out.println("ручной режим");
            n_cycle = 0;
        }

        Thread mainThread = new Thread(()->run());
        mainThread.start();

        try {
            while (mainThread.isAlive()) {
                Thread.sleep(100);
                Thread.yield();
            }
        } catch (java.lang.Throwable e) {
            e.printStackTrace();
        }

        commPort.Close();
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
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
        byte[] bodyCurrentDistance = new byte[8];
        byte[] bodyVes = new byte[8];
        int tikCurrent = 0;

        if (n_cycle < 2) {
            try {
                while (readFlagOn[0]) {
                    if (newCommand) {
                        String string = reader.readLine();
                        if (string == null) {
                            break;
                        }
                        subStrings = string.split(" ");

                        switch (subStrings[0].toLowerCase()) {
                            case "smf":
                                tikSample = Integer.parseInt(subStrings[1]);
                                tik = 0;
                                tikOld = 0;
                                distSample = -1000;
                                break;
                            case "sms":
                            case "smb":
                            case "stop":
                                tikSample = Integer.parseInt(subStrings[1]);
                                break;
                            case "dc":
                                tikOld = tikSample;
                                distOld = distSample;
                                bodyCurrentDistance[0] = Status.SEND2PC_DATA.getStat();
                                tikSample = Integer.parseInt(subStrings[1]);
                                distSample = Double.parseDouble(subStrings[2]);
                                if (distOld == -1000) distOld = distSample;
                                break;
                            case "total":
                                break;
                            default:
                                break;
                        }
                        newCommand = false;
                    }
                    else  {
                        if (!subStrings[0].toLowerCase().equals("dc")) {
                            newCommand = true;

                            switch (subStrings[0].toLowerCase()) {
                                case "smf":
                                    sendStatus(header, bodyStat, tikSample, Status.SEND2PC_MFORWARD);
                                    sendVes(header, bodyVes, tikSample, ves);
                                    System.out.println("вперед");
                                    break;
                                case "sms":
                                    sendStatus(header, bodyStat, tikSample, Status.SEND2PC_MSHELF);
                                    System.out.println("полка");
                                    break;
                                case "smb":
                                    sendStatus(header, bodyStat, tikSample, Status.SEND2PC_MBACK);
                                    System.out.println("назад");
                                    break;
                                case "stop":
                                    sendStatus(header, bodyStat, tikSample, Status.SEND2PC_STOP);
                                    System.out.println("count pack = " + countPack);
                                    System.out.println("стоп");
                                    readFlagOn[0] = false;
                                    break;
                            }
                            continue;
                        }

                        Thread.sleep(1);
                        tik++;
                        tikCurrent = tik;

                        if ((tikCurrent % 5) > 0) {
                            continue;
                        }

                        double distCurrent = 0;
                        int tikRazn = tikSample - tikOld;
                        if (tikRazn == 0) {
                            distCurrent = distSample;
                        } else {
                            double distRazn = distSample - distOld;
                            distCurrent = (distRazn / tikRazn * (tikCurrent - tikOld)) + distOld;
                        }
                        sendData(header, bodyCurrentDistance, tikCurrent, (int) distCurrent);

                        if (tikCurrent < tikSample) {
                            continue;
                        }
                        newCommand = true;
                    }
                }
                readFlagOn[0] = false;
            }
            catch (IOException e) {
                e.printStackTrace(); // read line
            }
            catch (java.lang.Throwable e) {
                e.printStackTrace();
            }
        } else {
            while (n_cycle > 0) {
                n_cycle--;
                try {
                    reader.close();
                    fileReader.close();
                    fileReader = new FileReader(file);
                    reader = new BufferedReader(fileReader);
                    newCommand = true;
                    readFlagOn[0] = true;
                    while (readFlagOn[0]) {
                        if (newCommand) {
                            String string = reader.readLine();
                            if (string == null) break;
                            subStrings = string.split(" ");

                            switch (subStrings[0].toLowerCase()) {
                                case "smf":
                                    tikSample = Integer.parseInt(subStrings[1]);
                                    tik = 0;
                                    tikOld = 0;
                                    distSample = -1000;
                                    break;
                                case "sms":
                                case "smb":
                                case "stop":
                                    tikSample = Integer.parseInt(subStrings[1]);
                                    break;
                                case "dc":
                                    tikOld = tikSample;
                                    distOld = distSample;
                                    bodyCurrentDistance[0] = Status.SEND2PC_DATA.getStat();
                                    tikSample = Integer.parseInt(subStrings[1]);
                                    distSample = Double.parseDouble(subStrings[2]);
                                    if (distOld == -1000) distOld = distSample;
                                    break;
                                case "total":
                                    break;
                                default:
                                    break;
                            }
                            newCommand = false;
                        }
                        else {
                            if (!subStrings[0].toLowerCase().equals("dc")) {
                                newCommand = true;

                                switch (subStrings[0].toLowerCase()) {
                                    case "smf":
                                        sendStatus(header, bodyStat, tikSample, Status.SEND2PC_CFORWARD);
                                        sendVes(header, bodyVes, tikSample, ves);
                                        System.out.println("вперед");
                                        break;
                                    case "sms":
                                        sendStatus(header, bodyStat, tikSample, Status.SEND2PC_CSHELF);
                                        System.out.println("полка");
                                        break;
                                    case "smb":
                                        sendStatus(header, bodyStat, tikSample, Status.SEND2PC_CBACK);
                                        System.out.println("назад");
                                        break;
                                    case "stop":
                                        if (n_cycle > 0) {
                                            sendStatus(header, bodyStat, tikSample, Status.SEND2PC_CDELAY);
                                            System.out.println("пауза");
                                        } else {
                                            sendStatus(header, bodyStat, tikSample, Status.SEND2PC_STOP);
                                            System.out.println("стоп");
                                            readFlagOn[0] = false;
                                        }
                                        System.out.println("count pack = " + countPack);
                                        break;
                                }

                                continue;
                            }

                            Thread.sleep(1);
                            tik++;
                            tikCurrent = tik;

                            if ((tikCurrent % 5) > 0) {
                                continue;
                            }

                            double distCurrent = 0;
                            int tikRazn = tikSample - tikOld;
                            if (tikRazn == 0) {
                                distCurrent = distSample;
                            } else {
                                double distRazn = distSample - distOld;
                                distCurrent = (distRazn / tikRazn * (tikCurrent - tikOld)) + distOld;
                            }
                            sendData(header, bodyCurrentDistance, tikCurrent, (int) distCurrent);

                            if (tikCurrent < tikSample) {
                                continue;
                            }
                            newCommand = true;
                        }
                    }
                    Thread.sleep(5_000);
                    countPack = 0;
                } catch (FileNotFoundException e) {
                    System.out.println("ошибка открытия файла: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("ошибка при закрытии файла: " + e.getMessage());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendData(byte[] header, byte[] body, int tik, int data) {
        System.out.println(tik + "\t" + data);
        countPack++;
        body[0] = Status.SEND2PC_DATA.getStat();
        ConvertDigit.Int2bytes(tik, body, 1);
        ConvertDigit.Int2bytes(data, body, 5, 2);
        body[body.length -1] = ControlSumma.crc8(body, body.length - 1);
        commPort.writeBlock(header);
        commPort.writeBlock(new byte[] {(byte) body.length});
        commPort.writeBlock(body);
    }

    private void sendStatus(byte[] header, byte[] body, int tik, Status stat) {
        body[0] = stat.getStat();
        ConvertDigit.Int2bytes(tik, body, 1);
        body[5] = ControlSumma.crc8(body, body.length - 1);
        commPort.writeBlock(header);
        commPort.writeBlock(new byte[] {(byte) body.length});
        commPort.writeBlock(body);
    }

    private void sendVes(byte[] header, byte[] body, int tik, double ves) {
        body[0] = Status.SEND2PC_VES.getStat();
        ConvertDigit.Int2bytes(tik, body, 1);
        ConvertDigit.Int2bytes((int) ves, body, 5, 2);
        body[7] = ControlSumma.crc8(body, body.length - 1);
        commPort.writeBlock(header);
        commPort.writeBlock(new byte[] {(byte) body.length});
        commPort.writeBlock(body);
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
}
