package com.simple.rtmp.io;

import com.simple.rtmp.KLog;
import com.simple.rtmp.io.packets.Abort;
import com.simple.rtmp.io.packets.Acknowledgement;
import com.simple.rtmp.io.packets.Audio;
import com.simple.rtmp.io.packets.Command;
import com.simple.rtmp.io.packets.Data;
import com.simple.rtmp.io.packets.RtmpHeader;
import com.simple.rtmp.io.packets.RtmpPacket;
import com.simple.rtmp.io.packets.SetChunkSize;
import com.simple.rtmp.io.packets.SetPeerBandwidth;
import com.simple.rtmp.io.packets.UserControl;
import com.simple.rtmp.io.packets.Video;
import com.simple.rtmp.io.packets.WindowAckSize;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author francois
 */
public class RtmpDecoder {

    private final static boolean debug = false;
    private RtmpSessionInfo rtmpSessionInfo;

    public RtmpDecoder(RtmpSessionInfo rtmpSessionInfo) {
        this.rtmpSessionInfo = rtmpSessionInfo;
    }

    public RtmpPacket readPacket(InputStream in) throws IOException {

        if (debug) KLog.d("====  readPacket(): called =====");
        RtmpHeader header = RtmpHeader.readHeader(in, rtmpSessionInfo);
        RtmpPacket rtmpPacket;
        KLog.d("readPacket(): header.messageType: " + header.getMessageType());

        ChunkStreamInfo chunkStreamInfo = rtmpSessionInfo.getChunkStreamInfo(header.getChunkStreamId());

        chunkStreamInfo.setPrevHeaderRx(header);

        if (header.getPacketLength() > rtmpSessionInfo.getChunkSize()) {
            if (debug) KLog.d("readPacket(): packet size (" + header.getPacketLength() + ") is bigger than chunk size (" + rtmpSessionInfo.getChunkSize() + "); storing chunk data");
            // This packet consists of more than one chunk; store the chunks in the chunk stream until everything is read
            if (!chunkStreamInfo.storePacketChunk(in, rtmpSessionInfo.getChunkSize())) {
                if (debug) KLog.d(" readPacket(): returning null because of incomplete packet");
                return null; // packet is not yet complete
            } else {
                if (debug) KLog.d(" readPacket(): stored chunks complete packet; reading packet");
                in = chunkStreamInfo.getStoredPacketInputStream();
            }
        } else {
            if (debug) KLog.d("readPacket(): packet size (" + header.getPacketLength() + ") is LESS than chunk size (" + rtmpSessionInfo.getChunkSize() + "); reading packet fully");
        }

        switch (header.getMessageType()) {

            case SET_CHUNK_SIZE: {
                SetChunkSize setChunkSize = new SetChunkSize(header);
                setChunkSize.readBody(in);
                if (debug) KLog.d("readPacket(): Setting chunk size to: " + setChunkSize.getChunkSize());
                rtmpSessionInfo.setChunkSize(setChunkSize.getChunkSize());
                return null;
            }
            case ABORT:
                rtmpPacket = new Abort(header);
                break;
            case USER_CONTROL_MESSAGE:
                rtmpPacket = new UserControl(header);
                break;
            case WINDOW_ACKNOWLEDGEMENT_SIZE:
                rtmpPacket = new WindowAckSize(header);
                break;
            case SET_PEER_BANDWIDTH:
                rtmpPacket = new SetPeerBandwidth(header);
                break;
            case AUDIO:
                rtmpPacket = new Audio(header);
                break;
            case VIDEO:
                rtmpPacket = new Video(header);
                break;
            case COMMAND_AMF0:
                rtmpPacket = new Command(header);
                break;
            case DATA_AMF0:
                rtmpPacket = new Data(header);
                break;
            case ACKNOWLEDGEMENT:
                rtmpPacket = new Acknowledgement(header);
                break;
            default:
                throw new IOException("No packet body implementation for message type: " + header.getMessageType());
        }
        rtmpPacket.readBody(in);
        return rtmpPacket;
    }
}
