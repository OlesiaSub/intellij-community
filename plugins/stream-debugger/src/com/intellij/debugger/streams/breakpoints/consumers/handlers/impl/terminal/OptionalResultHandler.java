// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers.impl.terminal;

import com.intellij.debugger.streams.breakpoints.consumers.PeekConsumer;
import com.intellij.debugger.streams.breakpoints.consumers.handlers.StreamOperationHandlerBase;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public class OptionalResultHandler extends StreamOperationHandlerBase {
  public static void setOperationResult(int index) {
    Object[] processingResult = basicPeekResultProcessing(index);
    Object result = PeekConsumer.streamResult;
    assert result instanceof Object[];
    Object[] arrayResult = (Object[])result;
    boolean[] isPresent = new boolean[0];
    Object[] finalResult = new Object[0];
    System.out.println(arrayResult[0]);
    if (arrayResult[0] instanceof Optional<?>) {
      System.out.println("here1");
      isPresent = new boolean[]{((Optional<?>)arrayResult[0]).isPresent()};
      finalResult = new Object[]{((Optional)arrayResult[0]).orElse(new Object())};
    }
    else if (arrayResult[0] instanceof OptionalInt) {
      System.out.println("here2");
      isPresent = new boolean[]{((OptionalInt)arrayResult[0]).isPresent()};
      finalResult = new Object[]{((OptionalInt)arrayResult[0]).orElse(0)};
    }
    else if (arrayResult[0] instanceof OptionalLong) {
      System.out.println("here3");
      isPresent = new boolean[]{((OptionalLong)arrayResult[0]).isPresent()};
      finalResult = new Object[]{((OptionalLong)arrayResult[0]).orElse(0)};
    }
    else if (arrayResult[0] instanceof OptionalDouble) {
      System.out.println("here4");
      isPresent = new boolean[]{((OptionalDouble)arrayResult[0]).isPresent()};
      finalResult = new Object[]{((OptionalDouble)arrayResult[0]).orElse(0)};
    }
    PeekConsumer.info[index - 1] = new Object[]{processingResult, new Object[]{isPresent, finalResult}};
  }
}
