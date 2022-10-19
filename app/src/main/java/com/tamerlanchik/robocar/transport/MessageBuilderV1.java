package com.tamerlanchik.robocar.transport;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MessageBuilderV1 {
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
}
