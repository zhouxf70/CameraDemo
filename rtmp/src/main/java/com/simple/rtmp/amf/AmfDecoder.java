package com.simple.rtmp.amf;

import com.simple.rtmp.KLog;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author francois
 */
public class AmfDecoder {

    public static AmfData readFrom(InputStream in) throws IOException {

        byte amfTypeByte = (byte) in.read();
        AmfType amfType = AmfType.valueOf(amfTypeByte);
        if (amfType == null) {
            amfType = AmfType.UNDEFINED;
            KLog.e("Unknown/unimplemented AMF data type: " + amfTypeByte);
        }else {
            KLog.e("AMF data type: " + amfType);
        }
        AmfData amfData;
        switch (amfType) {
            case NUMBER:
                amfData = new AmfNumber();
                break;
            case BOOLEAN:
                amfData = new AmfBoolean();
                break;
            case STRING:
                amfData = new AmfString();
                break;
            case OBJECT:
                amfData = new AmfObject();
                break;
            case NULL:
                return new AmfNull();
            case UNDEFINED:
                return new AmfUndefined();
            case MAP:
                amfData = new AmfMap();
                break;
            case ARRAY:
                amfData = new AmfArray();
                break;
            default:
                throw new IOException("Unknown/unimplemented AMF data type: " + amfType);
        }
        amfData.readFrom(in);
        return amfData;

    }
}
