package com.practice.camerademo.publish

import com.practice.camerademo.KLog
import com.simple.rtmp.amf.*
import com.simple.rtmp.io.ChunkStreamInfo
import com.simple.rtmp.io.RtmpDecoder
import com.simple.rtmp.io.RtmpSessionInfo
import com.simple.rtmp.io.packets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by zxf on 2021/5/28
 */
class PublisherImpl(
    private val host: String,
    private val port: Int,
    private val appName: String,
    private val streamName: String
) : RtmpPublisher {

    private var active = false
    private var connected = false
    private var currentStreamId = 0
    private lateinit var mReadSessionInfo: RtmpSessionInfo
    private lateinit var mWriteSessionInfo: RtmpSessionInfo
    private lateinit var mDecoder: RtmpDecoder
    private lateinit var mSocket: Socket
    private lateinit var mInputStream: InputStream
    private lateinit var mOutputStream: OutputStream
    private var readJob: Job? = null

    private val connectingLock = Object()

    /**
     * handshake -> connect ->publish
     */
    override fun connect(): Boolean {
        if (active) return false
        active = true
        mReadSessionInfo = RtmpSessionInfo()
        mWriteSessionInfo = RtmpSessionInfo()
        mDecoder = RtmpDecoder(mReadSessionInfo)
        mSocket = Socket()
        val socketAddress: SocketAddress = InetSocketAddress(host, port)
        try {
            mSocket.connect(socketAddress, 3000)
            mInputStream = BufferedInputStream(mSocket.getInputStream())
            mOutputStream = BufferedOutputStream(mSocket.getOutputStream())
            KLog.i("RtmpConnection.connect(): socket connection established, doing handhake...")
            handshake(mInputStream, mOutputStream)
            KLog.i("RtmpConnection.connect(): handshake done")
        } catch (e: IOException) {
            KLog.e(e)
            return false
        }

        readJob = GlobalScope.launch(Dispatchers.IO) {
            readRtmpPacket()
        }
        realConnect()
        synchronized(connectingLock) {
            while (!connected) connectingLock.wait()
        }
        return true
    }

    override fun close() {
        if (!active || !connected) return
        closeStream()
        active = false
        connected = false
        readJob?.cancel().also { KLog.i("read job canceled") }
        readJob = null
    }

    override fun setMetaData(map: AmfMap) {
        if (!connected) return
        KLog.d("setMetaData")
        val metadata = Data("@setDataFrame")
        metadata.header.messageStreamId = currentStreamId
        metadata.addData("onMetaData")
        metadata.addData(map)
        sendRtmpPacket(metadata)
    }

    override fun publishVideoData(data: ByteArray, size: Int, dts: Int) {
        if (!connected) return
        KLog.d("publishVideoData, size = $size, dts = $dts")
        val video = Video()
        video.data = data
        video.header.absoluteTimestamp = dts
        video.header.messageStreamId = currentStreamId
        sendRtmpPacket(video)
    }

    override fun publishAudioData(data: ByteArray, size: Int, dts: Int) {
        if (!connected) return
        KLog.d("publishAudioData, dts = $dts")
        val audio = Audio()
        audio.data = data
        audio.header.absoluteTimestamp = dts
        audio.header.messageStreamId = currentStreamId
        sendRtmpPacket(audio)
    }

    private fun sendRtmpPacket(rtmpPacket: RtmpPacket) {
        KLog.t("send packet start: $rtmpPacket")
        try {
            val chunkStreamInfo: ChunkStreamInfo = mWriteSessionInfo.getChunkStreamInfo(rtmpPacket.header.chunkStreamId)
            chunkStreamInfo.prevHeaderTx = rtmpPacket.header
            if (!(rtmpPacket is Video || rtmpPacket is Audio)) {
                rtmpPacket.header.absoluteTimestamp = 0
            }
            rtmpPacket.writeTo(mOutputStream, mWriteSessionInfo.chunkSize, chunkStreamInfo)
            if (rtmpPacket is Command) {
                mWriteSessionInfo.addInvokedCommand(rtmpPacket.transactionId, rtmpPacket.commandName)
            }
            mOutputStream.flush()
        } catch (se: SocketException) {
            // Since there are still remaining AV frame in the cache, we set a flag to guarantee the
            // socket exception only issue one time.
            KLog.e("Caught SocketException during write loop, shutting down : $se")
        } catch (ioe: IOException) {
            KLog.e("Caught IOException during write loop, shutting down : $ioe")
        }
        KLog.t("send packet end")
    }

    /**
     * 处理服务端发过来的消息
     * Protocol Control Message / Command Message
     */
    private fun readRtmpPacket() {
        KLog.i("readRtmpPacket")
        // It will be blocked when no data in input stream buffer
        while (active) {
            val packet: RtmpPacket? = mDecoder.readPacket(mInputStream)
            if (packet != null) {
                when (packet.header.messageType) {
                    RtmpHeader.MessageType.ABORT -> mReadSessionInfo.getChunkStreamInfo((packet as Abort).chunkStreamId).clearStoredChunks()
                    RtmpHeader.MessageType.USER_CONTROL_MESSAGE -> {
                        val user = packet as UserControl
                        if (user.type == UserControl.Type.PING_REQUEST) {
                            val channelInfo: ChunkStreamInfo = mWriteSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_CONTROL_CHANNEL.toInt())
                            val pong = UserControl(user, channelInfo)
                            sendRtmpPacket(pong)
                        } else if (user.type == UserControl.Type.STREAM_BEGIN) {
                            if (currentStreamId != user.firstEventData) KLog.e("Current stream ID error!")
                        }
                        KLog.i("USER_CONTROL_MESSAGE : ${user.type}")
                    }
                    RtmpHeader.MessageType.WINDOW_ACKNOWLEDGEMENT_SIZE -> {
                        val windowAckSize = packet as WindowAckSize
                        KLog.i("Setting ack window size : " + windowAckSize.acknowledgementWindowSize)
                        mWriteSessionInfo.setAcknowledgmentWindowSize(windowAckSize.acknowledgementWindowSize)
                    }
                    RtmpHeader.MessageType.SET_PEER_BANDWIDTH -> {
                        val bw = packet as SetPeerBandwidth
                        mWriteSessionInfo.setAcknowledgmentWindowSize(bw.acknowledgementWindowSize)
                        val ackWindowSize: Int = mWriteSessionInfo.acknowledgementWindowSize
                        val chunkStreamInfo: ChunkStreamInfo = mWriteSessionInfo.getChunkStreamInfo(ChunkStreamInfo.RTMP_CONTROL_CHANNEL.toInt())
                        KLog.i("Send acknowledgement window size: $ackWindowSize")
                        sendRtmpPacket(WindowAckSize(ackWindowSize, chunkStreamInfo))
                        mSocket.sendBufferSize = ackWindowSize
                    }
                    RtmpHeader.MessageType.COMMAND_AMF0 -> handleCommandMsg(packet as Command)
                    else -> KLog.w("Not handling unimplemented/unknown packet of type: " + packet.header.messageType)
                }
            }
        }

        try {
            mSocket.close()
            KLog.i("close socket")
        } catch (e: Exception) {
            KLog.e(e)
        }
    }

    private fun handleCommandMsg(command: Command) {
        when (command.commandName) {
            "_result" -> {
                val method: String = mWriteSessionInfo.takeInvokedCommand(command.transactionId)
                KLog.t("Got result for invoked method: $method")
                when (method) {
                    "connect" -> {
                        logSeverInfo(command)
                        createStream()
                    }
                    "createStream" -> {
                        currentStreamId = (command.data[1] as AmfNumber).value.toInt()
                        KLog.i("Get Stream ID to publish: $currentStreamId")
                        realPublish()
                    }
                    "releaseStream" -> KLog.i("Release Stream")
                    "FCPublish" -> KLog.i("FCPublish")
                    else -> KLog.e("'_result' message received for unknown method: $method")
                }
            }
            "onBWDone" -> KLog.i("onBWDone")
            "onFCPublish" -> KLog.i("onFCPublish")
            "onStatus" -> {
                val code: String = ((command.data[1] as AmfObject).getProperty("code") as AmfString).value
                KLog.i("onStatus $code")
                if (code == "NetStream.Publish.Start") {
                    synchronized(connectingLock) {
                        connected = true
                        connectingLock.notify()
                    }
                }
            }
            else -> KLog.e("Unknown/unhandled server invoke: $command")
        }

    }

    private fun realConnect() {
        KLog.i("realConnect")
        val command = Command("connect", 1)
        command.header.messageStreamId = 0
        val args = AmfObject()
        args.setProperty("app", appName)
        args.setProperty("flashVer", "LNX 11,2,202,233")
        args.setProperty("swfUrl", "")
        args.setProperty("tcUrl", "rtmp://$host:$port/$appName")
        args.setProperty("fpad", false)
        args.setProperty("capabilities", 239)
        args.setProperty("audioCodecs", 3575)
        args.setProperty("videoCodecs", 252)
        args.setProperty("videoFunction", 1)
        args.setProperty("pageUrl", "")
        args.setProperty("objectEncoding", 0)
        command.addData(args)
        sendRtmpPacket(command)
    }

    private fun createStream() {
        val setChunkSize = SetChunkSize(4 * 1024)
        sendRtmpPacket(setChunkSize)
        mWriteSessionInfo.chunkSize = 4 * 1024

        KLog.i("createStream")
        val streamId = ChunkStreamInfo.RTMP_STREAM_CHANNEL.toInt()
        val releaseStream = Command("releaseStream", 2)
        releaseStream.header.chunkStreamId = streamId
        releaseStream.addData(AmfNull())
        releaseStream.addData(streamName)
        sendRtmpPacket(releaseStream)

        val FCPublish = Command("FCPublish", 3)
        FCPublish.header.chunkStreamId = streamId
        FCPublish.addData(AmfNull())
        FCPublish.addData(streamName)
        sendRtmpPacket(FCPublish)

        val createStream = Command("createStream", 4)
        createStream.addData(AmfNull())
        sendRtmpPacket(createStream)
    }

    private fun realPublish() {
        KLog.i("realPublish")
        val publish = Command("publish", 0)
        publish.header.chunkStreamId = ChunkStreamInfo.RTMP_STREAM_CHANNEL.toInt()
        publish.header.messageStreamId = currentStreamId
        publish.addData(AmfNull())
        publish.addData(streamName)
        publish.addData(appName)
        sendRtmpPacket(publish)
    }

    private fun closeStream() {
        KLog.i("closeStream")
        val closeStream = Command("closeStream", 0)
        closeStream.header.chunkStreamId = ChunkStreamInfo.RTMP_STREAM_CHANNEL.toInt()
        closeStream.header.messageStreamId = currentStreamId
        closeStream.addData(AmfNull())
        sendRtmpPacket(closeStream)
    }

    private fun logSeverInfo(command: Command) {
        var objData: AmfObject = command.data[1] as AmfObject
        if (objData.getProperty("data") is AmfObject) {
            objData = objData.getProperty("data") as AmfObject
            val serverIp = objData.getProperty("srs_server_ip") as AmfString
            val serverPid = objData.getProperty("srs_pid") as AmfNumber
            val serverId = objData.getProperty("srs_id") as AmfNumber
            KLog.i("ip : $serverIp, pid : $serverPid, id : $serverId")
        }
    }

    private fun handshake(input: InputStream, output: OutputStream) {
        val handshake = Handshake()
        handshake.writeC0(output)
        handshake.writeC1(output) // Write C1 without waiting for S0
        output.flush()
        handshake.readS0(input)
        handshake.readS1(input)
        handshake.writeC2(output)
        handshake.readS2(input)
    }

    override fun getVideoFrameCacheNumber(): AtomicInteger {
        TODO("Not yet implemented")
    }

    override fun getServerIpAddr(): String {
        TODO("Not yet implemented")
    }

    override fun getServerPid(): Int {
        TODO("Not yet implemented")
    }

    override fun getServerId(): Int {
        TODO("Not yet implemented")
    }

}