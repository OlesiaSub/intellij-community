// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.breakpoints.consumers.handlers;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class ConsumerStruct {
  public Consumer<Object>[] consumer;
  public IntConsumer[] intConsumer;
  public char toGet;

  public ConsumerStruct(Consumer<Object> consumer, IntConsumer intConsumer, char toGet) {
    this.consumer = new Consumer[1];
    this.consumer[0] = consumer;
    this.intConsumer = new IntConsumer[1];
    this.intConsumer[0] = intConsumer;
    this.toGet = toGet;
  }
}
