package io.github.tierwise.core.internal.eval;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bounded pool that runs provider calls, so that a call can be abandoned when its timeout expires
 * (SVC-39).
 *
 * <p>The pool has daemon threads and no queue: a call starts at once on an idle or new thread, or is
 * rejected when {@value #MAX_THREADS} calls are already running. A rejected call is treated like an
 * unavailable provider, so a flood of hung providers cannot grow memory without bound. Idle threads end
 * after a short time. The pool is shared by all evaluators of the process and holds no evaluator state.
 */
final class ProviderExecutor {

  /** The largest number of provider calls that run at the same time. */
  static final int MAX_THREADS = 256;

  private static final AtomicInteger COUNTER = new AtomicInteger();

  private static final ThreadFactory THREADS = task -> {
    Thread thread = new Thread(task, "tierwise-provider-" + COUNTER.incrementAndGet());
    thread.setDaemon(true);
    return thread;
  };

  private static final ThreadPoolExecutor POOL =
      new ThreadPoolExecutor(0, MAX_THREADS, 20, TimeUnit.SECONDS, new SynchronousQueue<>(), THREADS);

  private ProviderExecutor() {
  }

  /**
   * Starts a provider call.
   *
   * @param call the call
   * @return the pending result
   * @throws RejectedExecutionException when {@value #MAX_THREADS} calls are already running
   */
  static <T> Future<T> submit(Callable<T> call) {
    return POOL.submit(call);
  }
}
