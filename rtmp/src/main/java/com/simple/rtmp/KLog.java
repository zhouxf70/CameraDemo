package com.simple.rtmp;

import android.util.Log;

public class KLog {

    private static String TAG = "KLog-rtmp";

    public static boolean isDebug = true;

    private final static int D = 1;
    private final static int I = 2;
    private final static int W = 3;
    private final static int E = 4;


    public static void f(String format, Object... args) {
        d(String.format(format, args));
    }

    public static void d(Object msg) {
        print(D, "", msg);
    }

    public static void i(Object msg) {
        print(I, "", msg);
    }

    public static void w(Object msg) {
        print(W, "", msg);
    }

    public static void e(Object msg) {
        print(E, "", msg);
    }

    public static void d(String tag, Object msg) {
        print(D, tag, msg);
    }

    public static void i(String tag, Object msg) {
        print(I, tag, msg);
    }

    public static void w(String tag, Object msg) {
        print(W, tag, msg);
    }

    public static void e(String tag, Object msg) {
        print(E, tag, msg);
    }

    public static void t(Object msg) {
        print(D, "", msg);
    }

    private static void print(int type, String tag, Object msg) {
        if (!isDebug)
            return;

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int index = 4;
        String className = stackTrace[index].getFileName();
        String methodName = stackTrace[index].getMethodName();
        int lineNumber = stackTrace[index].getLineNumber();
        if (!methodName.isEmpty() && methodName.charAt(0) >= 'a' && methodName.charAt(0) <= 'z')
            methodName.toCharArray()[0] -= 32;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[ (").append(className).append(":").append(lineNumber).append(")#").append(methodName).append(" ] ");

        if (msg != null) {
            stringBuilder.append(msg);
        }

        String allTag = TAG;
        if (tag != null) {
            allTag = TAG + "-" + tag;
        }
        String logStr = stringBuilder.toString();
        switch (type) {
            case D:
                Log.d(allTag, logStr);
                break;
            case I:
                Log.i(allTag, logStr);
                break;
            case W:
                Log.w(allTag, logStr);
                break;
            case E:
                Log.e(allTag, logStr);
                break;
        }
    }
}
