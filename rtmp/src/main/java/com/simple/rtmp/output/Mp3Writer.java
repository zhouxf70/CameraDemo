package com.simple.rtmp.output;

import java.io.IOException;
import java.io.OutputStream;
import com.simple.rtmp.io.packets.ContentData;
import com.simple.rtmp.io.packets.Data;
import com.simple.rtmp.io.packets.RtmpHeader;

/**
 * Simple writer class for writing an MP3 audio stream to an OutputStream
 * 
 * @author francois
 */
public class Mp3Writer extends RtmpStreamWriter {

    protected OutputStream out;

    protected Mp3Writer() {
    }

    public Mp3Writer(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(Data dataPacket) throws IOException {
    }

    @Override
    public void write(ContentData packet) throws IOException {
        if (packet.getHeader().getMessageType() == RtmpHeader.MessageType.AUDIO) {
            byte[] data = packet.getData();
            out.write(data, 1, data.length - 1); // skip past 0x2F FLV MP3 audio tag sub-header
        }
    }
}
