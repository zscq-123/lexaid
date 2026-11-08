package com.lexaid.common.image;

import javax.imageio.stream.ImageInputStream;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Read only the EXIF orientation tag, so camera photos can drop EXIF without rotating on screen. */
final class JpegOrientation {
    private JpegOrientation() {}

    static int read(ImageInputStream input) throws IOException {
        input.seek(0);
        try {
            if (input.readUnsignedShort() != 0xffd8) return 1;
            while (input.read() == 0xff) {
                int marker;
                do { marker = input.read(); } while (marker == 0xff);
                if (marker < 0 || marker == 0xda || marker == 0xd9) break;
                int size = input.readUnsignedShort() - 2;
                if (size < 0) break;
                if (marker == 0xe1) {
                    byte[] segment = new byte[size];
                    input.readFully(segment);
                    int orientation = exifTag(segment);
                    if (orientation > 0) return orientation;
                } else {
                    input.skipBytes(size);
                }
            }
            return 1;
        } finally {
            input.seek(0);
        }
    }

    private static int exifTag(byte[] segment) {
        if (segment.length < 16 || ByteBuffer.wrap(segment).getInt() != 0x45786966
                || segment[4] != 0 || segment[5] != 0) return 0;
        boolean little = segment[6] == 'I' && segment[7] == 'I';
        if (!little && (segment[6] != 'M' || segment[7] != 'M')) return 0;
        ByteBuffer data = ByteBuffer.wrap(segment).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (data.getShort(8) != 42) return 0;
        long directory = 6 + Integer.toUnsignedLong(data.getInt(10));
        if (directory < 14 || directory + 2 > segment.length) return 0;
        int n = Short.toUnsignedInt(data.getShort((int) directory));
        for (int i = 0, at = (int) directory + 2; i < n && at + 12 <= segment.length; i++, at += 12) {
            if (data.getShort(at) == 0x0112 && data.getShort(at + 2) == 3 && data.getInt(at + 4) == 1) {
                int value = data.getShort(at + 8);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    static AffineTransform transform(int tag, int width, int height) {
        return switch (tag) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }
}
