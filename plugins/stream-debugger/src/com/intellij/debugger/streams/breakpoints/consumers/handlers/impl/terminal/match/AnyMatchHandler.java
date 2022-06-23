// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal.match;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;

public class AnyMatchHandler extends MatchHandler {
  public static void setOperationResult(int index) {
    final Object[] result = new Object[2];
    initializeMaps(index);
    if (PeekConsumer.streamResult instanceof boolean[]) {
      result[0] = (((boolean[])PeekConsumer.streamResult)[0])
                  ? getBeforeAndAfterArrays(reducedBeforeMap, reducedAfterMap)
                  : getBeforeAndAfterArrays(beforeMap, afterMap);
    }
    result[1] = PeekConsumer.streamResult;
    PeekConsumer.info[index - 1] = result;
  }
}
