package com.pty4j.util;

import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

public final class LazyValue<T> {
  private final Callable<T> myProvider;
  private final Object myLock = new Object();
  private volatile Pair<T, Throwable> myResult;

  public LazyValue(@NotNull Callable<T> provider) {
    myProvider = provider;
  }

  public T getValue() throws Exception {
    Pair<T, Throwable> result = myResult;
    if (result != null) {
      return unpack(result);
    }
    synchronized (myLock) {
      result = myResult;
      if (result == null) {
        try {
          T value = myProvider.call();
          result = new Pair<>(value, null);
        }
        catch (Throwable t) {
          result = new Pair<>(null, t);
        }
        myResult = result;
      }
    }
    return unpack(result);
  }

  private T unpack(@NotNull Pair<T, Throwable> result) throws Exception {
    if (result.getSecond() != null) {
      if (result.getSecond() instanceof Exception) {
        throw (Exception)result.getSecond();
      }
      if (result.getSecond() instanceof Error) {
        throw (Error)result.getSecond();
      }
      throw new RuntimeException("Rethrowing unknown Throwable", result.getSecond());
    }
    return result.getFirst();
  }
}
