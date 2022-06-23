// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.match;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;
import com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchHandler extends StreamOperationHandlerBase {

  protected static Map<Integer, Object> beforeMap;
  protected static Map<Integer, Object> reducedBeforeMap;
  protected static Map<Integer, Object> afterMap;
  protected static Map<Integer, Object> reducedAfterMap;

  protected static void initializeMaps(int index) {
    beforeMap = PeekConsumer.peekArray[index - 1];
    reducedBeforeMap = new HashMap<>();
    afterMap = (index == PeekConsumer.peekArray.length ? new HashMap<>(beforeMap.size()) : PeekConsumer.peekArray[index]);
    reducedAfterMap = new HashMap<>();
    List<Map.Entry<Integer, Object>> entryList = new ArrayList<>(beforeMap.entrySet());
    Map.Entry<Integer, Object> lastEntry = entryList.get(entryList.size() - 1);
    reducedBeforeMap.put(lastEntry.getKey(), lastEntry.getValue());
    if (afterMap.size() > 0) {
      entryList = new ArrayList<>(beforeMap.entrySet());
      lastEntry = entryList.get(entryList.size() - 1);
      reducedAfterMap.put(lastEntry.getKey(), lastEntry.getValue());
    }
  }
}
