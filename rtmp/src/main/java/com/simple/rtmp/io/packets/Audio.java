package com.simple.rtmp.io.packets;

import com.simple.rtmp.io.ChunkStreamInfo;

/**
 * Audio data packet
 *
 * @author francois
 */
public class Audio extends ContentData {

    public Audio(RtmpHeader header) {
        super(header);
    }

    public Audio() {
        super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_0_FULL,
                ChunkStreamInfo.RTMP_AUDIO_CHANNEL,
                RtmpHeader.MessageType.AUDIO));
    }
}
