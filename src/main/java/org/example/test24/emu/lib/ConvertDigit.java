package org.example.test24.emu.lib;

public class ConvertDigit {
    public static void Int2bytes(int source, byte[] target, int indx) {
        int i = 4;
        do {
            target[indx] = (byte) (source & 0xff);
            source >>= 8;
            indx++;
            i--;
        } while (i > 0);
    }
    public static void Int2bytes(int source, byte[] target, int indx, int lenght) {
        do {
            target[indx] = (byte) (source & 0xff);
            source >>= 8;
            indx++;
            lenght--;
        } while (lenght > 0);
    }
}
