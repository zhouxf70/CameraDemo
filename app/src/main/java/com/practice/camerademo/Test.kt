package com.practice.camerademo

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

fun main() {
    testBuffer()
}

private fun testBuffer() {
    val src = ByteBuffer.allocate(15)
    repeat(15) {
        src.put(it.toByte())
    }
    val des = ByteBuffer.allocate(12)
    repeat(3) {
        src.position(it * 5)
        src.limit(it * 5 + 4)
        des.put(src)
    }
}

private fun rotate2() {
    val intArray = IntArray(90) { it }
    val originWidth = 10
    val originHeight = 6
    printArrWH(intArray, 10, 9)
    val size = intArray.size
    val ySize = originWidth * originHeight
    if (size != (ySize * 3 / 2)) throw Exception("illegal w:$originWidth, h:$originHeight, size:$size")
    val newArr = IntArray(size)
    for (i in 0 until ySize) {
        val originRow = i / originWidth
        val originColumn = i % originWidth
        newArr[originColumn * originHeight + originRow] = intArray[i]
    }
    for (i in ySize until size step 2) {
        val uIndex = i - ySize
        val originRow = uIndex / originWidth
        val originColumn = uIndex % originWidth / 2
        println("uIndex = $uIndex, originRow = $originRow, originColumn = $originColumn")
        newArr[originColumn * originHeight + originRow * 2 + ySize] = intArray[i]
        newArr[originColumn * originHeight + originRow * 2 + ySize + 1] = intArray[i + 1]
    }
    printArrWH(newArr, 6, 15)
}

private fun rotate() {
    val intArray = IntArray(42) { it }
    val w = 6
    val h = 7
    printArrWH(intArray, w, h)
    val newArr = IntArray(42)
    for (i in intArray.indices) {
        val oriRow = i / w
        val oriColumn = i % w
        newArr[oriColumn * h + oriRow] = intArray[i]
    }
    printArrWH(newArr, h, w)
}

private fun printArrWH(array: IntArray, width: Int, height: Int) {
    for (i in array.indices) {
        print(array[i])
        print(" ")
        if ((i + 1) % width == 0) println()
    }
}

private fun byteArrToString() {
    val array = byteArrayOf(0x53, 0x65, 0x72, 0x76, 0x65, 0x72)
    array.forEach {
        print(it.toChar())
    }
    println()
}

private fun dtb(d: Double) {
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

