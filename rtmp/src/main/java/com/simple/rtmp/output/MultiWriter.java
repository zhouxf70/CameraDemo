package com.simple.rtmp.output;

import java.io.FileOutputStream;
import java.io.IOException;
import com.simple.rtmp.io.packets.ContentData;
import com.simple.rtmp.io.packets.Data;

/**
 * Simple example of writing two output streams simultaneously; AAC audio stream (with ADTS headers) and an FLV-encapsulated audio stream
 * @author francois
 */
public class MultiWriter extends RtmpStreamWriter {

    AacWriter aacWriter;
    FlvWriter flvWriter;

    public MultiWriter(String baseFilename) {
        try {
            flvWriter = new FlvWriter(new FileOutputStream(baseFilename + ".flv"));
            aacWriter = new AacWriter(new FileOutputStream(baseFilename + ".aac"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void write(Data dataPacket) throws IOException {
        flvWriter.write(dataPacket);
        aacWriter.write(dataPacket);
    }

    @Override
    public void write(ContentData packet) throws IOException {
        flvWriter.write(packet);
        aacWriter.write(packet);
    }

    @Override
    public void close() {
        flvWriter.close();
        aacWriter.close();
        super.close();
    }
}
