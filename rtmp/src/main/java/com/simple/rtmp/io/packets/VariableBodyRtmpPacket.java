package com.simple.rtmp.io.packets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.simple.rtmp.KLog;
import com.simple.rtmp.amf.AmfBoolean;
import com.simple.rtmp.amf.AmfData;
import com.simple.rtmp.amf.AmfDecoder;
import com.simple.rtmp.amf.AmfNull;
import com.simple.rtmp.amf.AmfNumber;
import com.simple.rtmp.amf.AmfString;

/**
 * RTMP packet with a "variable" body structure (i.e. the structure of the
 * body depends on some other state/parameter in the packet.
 * 
 * Examples of this type of packet are Command and Data; this abstract class
 * exists mostly for code re-use.
 * 
 * @author francois
 */
public abstract class VariableBodyRtmpPacket extends RtmpPacket {

    protected List<AmfData> data;

    public VariableBodyRtmpPacket(RtmpHeader header) {
        super(header);
    }

    public List<AmfData> getData() {
        return data;
    }

    public void addData(String string) {
        addData(new AmfString(string));
    }

    public void addData(double number) {
        addData(new AmfNumber(number));
    }
    
    public void addData(boolean bool) {
        addData(new AmfBoolean(bool));
    }

    public void addData(AmfData dataItem) {
        if (data == null) {
            this.data = new ArrayList<AmfData>();
        }
        if (dataItem == null) {
            dataItem = new AmfNull();
        }
        this.data.add(dataItem);
    }

    protected void readVariableData(final InputStream in, int bytesAlreadyRead) throws IOException {
        // ...now read in arguments (if any)
        KLog.d("VBRP.readVariableData(): about to read data. Bytes read: " + bytesAlreadyRead + ", total: " + header.getPacketLength());
        do {
            AmfData dataItem = AmfDecoder.readFrom(in);
            addData(dataItem);
            bytesAlreadyRead += dataItem.getSize();
        } while (bytesAlreadyRead < header.getPacketLength());
        KLog.d("VBRP.readVariableData(): data is: " + data);
    }

    protected void writeVariableData(final OutputStream out) throws IOException {
        if (data != null) {
            for (AmfData dataItem : data) {
                dataItem.writeTo(out);
            }
        } else {
            // Write a null
            AmfNull.writeNullTo(out);
        }
    }
}
