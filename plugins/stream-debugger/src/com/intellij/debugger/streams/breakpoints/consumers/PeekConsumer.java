// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PeekConsumer {

  public static Object[] info;
  public static AtomicInteger time;
  public static Map<Integer, Object>[] peekArray;
  public static Object streamResult;
  public static Consumer<Object>[] consumersArray;
  private static long startTime;

  public static void init(int n) {
    System.out.println("init");
    time = new AtomicInteger();
    streamResult = null;
    info = new Object[n];
    peekArray = new LinkedHashMap[n];
    for (int i = 0; i < n; i++) {
      peekArray[i] = new LinkedHashMap<>();
    }
    consumersArray = new Consumer[n];
    for (int i = 0; i < n; i++) {
      int finalI = i;
      consumersArray[i] = o -> {
        insertByIndex(finalI, o);
        time.incrementAndGet();
      };
    }
    startTime = System.nanoTime();
  }

  public static void insertByIndex(int index, Object value) {
    System.out.println("index ins");
    if (index >= peekArray.length) {
      return;
    }
    else {
      peekArray[index].put(time.get(), value);
    }
  }

  public static void setReturnValue(Object value) {
    streamResult = value;
  }

  public static Object getResult() {
    System.out.println("get res");
    Object myRes;
    final long[] elapsedTime = new long[]{System.nanoTime() - startTime};
    System.out.println("got time");
    System.out.println("info " + info);
    System.out.println("s res " + streamResult);
    for (Object o : info) {
      if (o instanceof Object[]) {
        for (Object oo : (Object[])o) {
          System.out.println(oo);
        }
      } else System.out.println(o);
    }
    myRes = new Object[]{info, streamResult, elapsedTime};
    System.out.println("fin");
    return myRes;
  }
}
