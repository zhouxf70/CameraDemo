package com.simple.rtmp.output;

import java.io.IOException;
import com.simple.rtmp.io.packets.ContentData;
import com.simple.rtmp.io.packets.Data;

/**
 * Interface for writing RTMP content streams (audio/video)
 * 
 * @author francois
 */
public abstract class RtmpStreamWriter {

    public abstract void write(Data dataPacket) throws IOException;

    public abstract void write(ContentData packet) throws IOException;

    public void close() {
        synchronized (this) {
            this.notifyAll();
        }
    }
}
