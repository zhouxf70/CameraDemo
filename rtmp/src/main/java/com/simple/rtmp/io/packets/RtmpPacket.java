package com.simple.rtmp.io.packets;

import com.simple.rtmp.KLog;
import com.simple.rtmp.io.ChunkStreamInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author francois
 */
public abstract class RtmpPacket {

    protected RtmpHeader header;

    public RtmpPacket(RtmpHeader header) {
        this.header = header;
    }

    public RtmpHeader getHeader() {
        return header;
    }

    public abstract void readBody(InputStream in) throws IOException;

    protected abstract void writeBody(OutputStream out) throws IOException;

    public void writeTo(OutputStream out, final int chunkSize, final ChunkStreamInfo chunkStreamInfo) throws IOException {
        byte[] body;
        if (this instanceof ContentData) {
            body = ((ContentData) this).data;
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeBody(baos);
            body = baos.toByteArray();
        }

        KLog.d("len = " + body.length + ", chunk size = " + chunkSize);
        header.setPacketLength(body.length);
        // Write header for first chunk
        header.writeTo(out, RtmpHeader.ChunkType.TYPE_0_FULL, chunkStreamInfo);
        int remainingBytes = body.length;
        int pos = 0;
        while (remainingBytes > chunkSize) {
            KLog.d("remainingBytes = " + remainingBytes);
            out.write(body, pos, chunkSize);
            remainingBytes -= chunkSize;
            pos += chunkSize;
            header.writeTo(out, RtmpHeader.ChunkType.TYPE_0_FULL, chunkStreamInfo);
        }
        out.write(body, pos, remainingBytes);
    }
}
