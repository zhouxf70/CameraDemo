package com.simple.rtmp.io.packets;

import com.simple.rtmp.io.ChunkStreamInfo;

/**
 * Video data packet
 *
 * @author francois
 */
public class Video extends ContentData {

    public Video(RtmpHeader header) {
        super(header);
    }

    public Video() {
        super(new RtmpHeader(RtmpHeader.ChunkType.TYPE_0_FULL,
                ChunkStreamInfo.RTMP_VIDEO_CHANNEL,
                RtmpHeader.MessageType.VIDEO));
    }
}
