package io.agora.media;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utils {
    public static final int VERSION_LENGTH = 3;

    public static String base64Encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] base64Decode(String data) {
        return Base64.decode(data, Base64.NO_WRAP);
    }

    public static int getTimestamp() {
        return (int) (new Date().getTime() / 1000);
    }

    public static int randomInt() {
        return new SecureRandom().nextInt();
    }

    public static boolean isUUID(String uuid) {
        if (uuid == null || uuid.length() != 32) {
            return false;
        }
        return uuid.matches("\\p{XDigit}+");
    }

    public static byte[] compress(byte[] data) {
        byte[] output;
        Deflater deflater = new Deflater();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);

        try {
            deflater.reset();
            deflater.setInput(data);
            deflater.finish();

            byte[] buf = new byte[data.length];
            while (!deflater.finished()) {
                int size = deflater.deflate(buf);
                bos.write(buf, 0, size);
            }
            output = bos.toByteArray();
        } catch (Exception e) {
            output = data;
            e.printStackTrace();
        } finally {
            deflater.end();
        }

        return output;
    }

    public static byte[] decompress(byte[] data) {
        Inflater inflater = new Inflater();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);

        try {
            inflater.setInput(data);
            byte[] buf = new byte[8192];
            int len;

            while ((len = inflater.inflate(buf)) > 0) {
                bos.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            inflater.end();
        }

        return bos.toByteArray();
    }
}
