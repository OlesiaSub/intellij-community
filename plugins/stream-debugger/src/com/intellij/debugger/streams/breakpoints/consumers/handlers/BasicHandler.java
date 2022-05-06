// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BasicHandler {
  public static void setOperationResult(int index) {
    try {
      Object[] processingResult = basicPeekResultProcessing(index);
      System.out.println("in basic snd");
      PeekConsumer.info[index - 1] = processingResult;
      System.out.println("in basic thd" + PeekConsumer.info[index - 1]);
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  public static Object[] basicPeekResultProcessing(int index) {
    System.out.println("basic index " + index);
    System.out.println("in peek 1");
    Object[] beforeArray;
    System.out.println("in peek 1 1");
    Object[] afterArray;
    System.out.println("in peek 1 2 arr " + Arrays.toString(PeekConsumer.peekArray));
    Map<Integer, Object> prev = PeekConsumer.peekArray[index - 1];
    System.out.println("in peek 1 3");
    Map<Integer, Object> cur;
    if (index == PeekConsumer.peekArray.length) {
      System.out.println("in peek if");
      cur = new HashMap<>(prev.size());
    }
    else {
      System.out.println("in peek else");
      cur = PeekConsumer.peekArray[index];
    }
    {
      System.out.println("in peek 3");
      final int size = prev.size();
      final int[] keys = new int[size];
      final Object[] values = new Object[size];
      int i = 0;
      System.out.println("in peek 4");
      for (int key : prev.keySet()) {
        keys[i] = key;
        values[i] = prev.get(key);
        i++;
      }
      System.out.println("in peek 5");
      beforeArray = new Object[]{keys, values};
    }
    {
      System.out.println("in peek 6");
      final int size = cur.size();
      final int[] keys = new int[size];
      final Object[] values = new Object[size];
      int i = 0;
      for (int key : cur.keySet()) {
        keys[i] = key;
        values[i] = cur.get(key);
        i++;
      }
      System.out.println("in peek 8");
      afterArray = new Object[]{keys, values};
    }
    System.out.println("in peek 10");
    Object[] ret = new Object[]{beforeArray, afterArray};
    return ret;
  }
}
