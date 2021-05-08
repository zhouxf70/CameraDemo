package com.practice.camerademo

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream

@InternalCoroutinesApi
fun main() {
    dtb(1920.0)
    dtb(1080.0)
}

private fun dtb(d:Double){
//    println(Double.doubleToLongBits(d))
}

fun testStringToByte() {
    val toByte = "FLV".toByteArray()
    println(toByte)
    val file = File("byte").also {
        if (!it.exists()) it.createNewFile().also { print("create") }
        println(it.absolutePath)
    }
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(toByte)
    fileOutputStream.close()
}

private fun testIntToByte() {
    val toByte = 33295.toByteArray()
    println(toByte)
    val file = File("byte").also {
        if (!it.exists()) it.createNewFile().also { print("create") }
        println(it.absolutePath)
    }
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(toByte)
    fileOutputStream.close()
}

private fun Int.toByteArray(): ByteArray {
    val byteArray = ByteArray(4)
    byteArray[0] = (this shr 24 and 0xff).toByte()
    byteArray[1] = (this shr 16 and 0xff).toByte()
    byteArray[2] = (this shr 8 and 0xff).toByte()
    byteArray[3] = (this and 0xff).toByte()
    return byteArray
}

@InternalCoroutinesApi
private fun testFlow() {
    println("testFlow")
    var emitter: FlowCollector<String>? = null
    val flow: Flow<String> = flow {
//        emit("hh1")
        emitter = this
    }
    GlobalScope.launch(Dispatchers.IO) {
        flow.collect {
            println(it)
        }
    }
    println(emitter)
    GlobalScope.launch {
        emitter?.emit("hha1")
        delay(100)
        emitter?.emit("hha2")
    }
    Thread.sleep(2000)
}


private fun testByteFile() {
    val file = File("byte").also {
        if (!it.exists()) it.createNewFile().also { print("create") }
        println(it.absolutePath)
    }
    val fileOutputStream = FileOutputStream(file)
    val byteArrayOf = byteArrayOf(1, 127, 2, 65)
    fileOutputStream.write(byteArrayOf)
    fileOutputStream.close()
}

