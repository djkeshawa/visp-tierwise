package io.github.tierwise.spring;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** An observer that keeps what it was told. */
final class RecordingObserver implements TierwiseObserver {

  final List<String> activated = new CopyOnWriteArrayList<>();
  final List<String> providerFailures = new CopyOnWriteArrayList<>();
  final List<String> decisions = new CopyOnWriteArrayList<>();
  final AtomicInteger refreshFailures = new AtomicInteger();
  volatile TierwiseEvaluators started;

  @Override
  public void onStarted(TierwiseEvaluators evaluators) {
    started = evaluators;
  }

  @Override
  public void onBundleActivated(String bundleVersion) {
    activated.add(bundleVersion);
  }

  @Override
  public void onRefreshFailure() {
    refreshFailures.incrementAndGet();
  }

  @Override
  public void onProviderFailure(String level, String outcome) {
    providerFailures.add(level + "/" + outcome);
  }

  @Override
  public void onDecision(String feature, String reason) {
    decisions.add(feature + "/" + reason);
  }
}
