package com.tamerlanchik.robocar.transport.binary_tools;

import com.tamerlanchik.robocar.transport.Package;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MessageBuilderV1 {
    public static class Message {
        public int label;
        public byte[] payload;
    }
    private interface IMessageBuildAlgorythm {
        void build(ByteBuffer buffer);
    }
//    Format:
//    <Label>: 1 byte
//    <DataSize>: 4 byte
//    <Data>: [DataSize] bytes
    public static Package build(int label, String data) throws Exception {
        byte[] binData = data.getBytes(StandardCharsets.UTF_8);
        return build(label, binData);
    }

    public static Package build(int label, byte[] data) throws Exception {
        Package pkg = new Package();

        int dataLength = data.length;

        ByteBuffer bf = ByteBuffer.allocate(1 + 4 + dataLength);
        if (label > 255 || label < 0) {
            throw new Exception("label > 1 byte or negative");
        }
        bf.put((byte)label);
        bf.putInt(dataLength);
        bf.put(data);

        pkg.setBinary(bf.array());
        return pkg;
    }

    public static ByteBuffer buildNoLength(int label, byte[] data) throws RuntimeException {
        int dataLength = data.length;
        final int LABEL_SIZE = 1;
        ByteBuffer bf = ByteBuffer.allocate(LABEL_SIZE + dataLength);
        if (label > 255 || label < 0) {
            throw new RuntimeException("label > 1 byte or negative");
        }
        bf.put((byte)label);
        bf.put(data);
        return bf;
    }

    public static Message unpack(Package pkg) {
        Message msg = new Message();
        byte[] array = pkg.getBinary();
        msg.label = array[0];
        msg.payload = Arrays.copyOfRange(array, 1, pkg.getSize());
        return msg;
    }

    public static byte[] int2bytes(int value, int bytesCount) {
        byte bytes[] = new byte[bytesCount];
        for (int i = 0; i < bytesCount; ++i) {
            bytes[bytesCount - i - 1] = (byte) (value >> 8*i);  // Big Endian
        }
        return bytes;
    }
}
