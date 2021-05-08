package com.simple.rtmp.io;

import java.io.InputStream;

import com.simple.rtmp.KLog;
import com.simple.rtmp.io.packets.RtmpPacket;

/**
 * RTMPConnection's read thread
 * 
 * @author francois
 */
public class ReadThread extends Thread {

    private RtmpDecoder rtmpDecoder;
    private InputStream in;
    private PacketRxHandler packetRxHandler;
    private ThreadController threadController;

    public ReadThread(RtmpSessionInfo rtmpSessionInfo, InputStream in, PacketRxHandler packetRxHandler, ThreadController threadController) {
        super("RtmpReadThread");
        this.in = in;
        this.packetRxHandler = packetRxHandler;
        this.rtmpDecoder = new RtmpDecoder(rtmpSessionInfo);
        this.threadController = threadController;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                RtmpPacket rtmpPacket = rtmpDecoder.readPacket(in);
                if (rtmpPacket != null) {
                    // Pass to handler
                    packetRxHandler.handleRxPacket(rtmpPacket);
                }
//            } catch (WindowAckRequired war) {
//                KLog.i("ReadThread: Window Acknowledgment required, notifying packet handler...");
//                packetRxHandler.notifyWindowAckRequired(war.getBytesRead());
//                if (war.getRtmpPacket() != null) {
//                    // Pass to handler
//                    packetRxHandler.handleRxPacket(war.getRtmpPacket());
//                }
            } catch (Exception ex) {
                if (!this.isInterrupted()) {
                    KLog.e("ReadThread: Caught exception while reading/decoding packet, shutting down...", ex);
                    ex.printStackTrace();
                    this.interrupt();
                }
            }
        }
        // Close inputstream
        try {
            in.close();
        } catch (Exception ex) {
            KLog.w("ReadThread: Failed to close inputstream", ex);
        }
        KLog.i("ReadThread: exiting");
        if (threadController != null) {
            threadController.threadHasExited(this);
        }
    }

    public void shutdown() {
        KLog.d("ReadThread: Stopping read thread...");
        this.interrupt();
    }
}
