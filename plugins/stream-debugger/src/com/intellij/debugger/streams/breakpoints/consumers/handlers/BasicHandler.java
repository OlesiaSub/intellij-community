// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;

import java.util.HashMap;
import java.util.Map;

public class BasicHandler {
  public static void setOperationResult(int index) {
      Object[] processingResult = basicPeekResultProcessing(index);
      PeekConsumer.info[index - 1] = processingResult;
  }

  public static Object[] basicPeekResultProcessing(int index) {
    Object[] beforeArray;
    Object[] afterArray;
    Map<Integer, Object> prev = PeekConsumer.peekArray[index - 1];
    Map<Integer, Object> cur;
    if (index == PeekConsumer.peekArray.length) {
      cur = new HashMap<>(prev.size());
    }
    else {
      cur = PeekConsumer.peekArray[index];
    }
    {
      final int size = prev.size();
      final int[] keys = new int[size];
      final Object[] values = new Object[size];
      int i = 0;
      for (int key : prev.keySet()) {
        keys[i] = key;
        values[i] = prev.get(key);
        i++;
      }
      beforeArray = new Object[]{keys, values};
    }
    {
      final int size = cur.size();
      final int[] keys = new int[size];
      final Object[] values = new Object[size];
      int i = 0;
      for (int key : cur.keySet()) {
        keys[i] = key;
        values[i] = cur.get(key);
        i++;
      }
      afterArray = new Object[]{keys, values};
    }
    Object[] ret = new Object[]{beforeArray, afterArray};
    return ret;
  }
}
