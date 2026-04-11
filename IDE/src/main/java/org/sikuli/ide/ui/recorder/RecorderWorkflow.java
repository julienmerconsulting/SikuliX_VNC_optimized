/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * State machine driving the Modern Recorder step-by-step workflow.
 *
 * States: IDLE -> CAPTURING_REGION -> WAITING_OCR / WAITING_PATTERN_VALIDATION -> IDLE
 *         IDLE -> WAITING_KEY_COMBO -> IDLE
 *         IDLE -> WAITING_USER_INPUT -> IDLE
 *
 * All state transitions are validated against VALID_TRANSITIONS.
 * UI callbacks are dispatched on the EDT via SwingUtilities.invokeLater.
 * Watchdog timers auto-return to IDLE on timeout.
 */
public class RecorderWorkflow {

  // ── States ──

  public enum RecorderState {
    IDLE,
    CAPTURING_REGION,
    WAITING_OCR,
    WAITING_KEY_COMBO,
    WAITING_PATTERN_VALIDATION,
    WAITING_USER_INPUT
  }

  // ── DragDrop sub-state ──

  public enum DragDropStep {
    SOURCE,
    DESTINATION
  }

  // ── Transition graph ──

  private static final EnumMap<RecorderState, Set<RecorderState>> VALID_TRANSITIONS;

  static {
    VALID_TRANSITIONS = new EnumMap<>(RecorderState.class);

    VALID_TRANSITIONS.put(RecorderState.IDLE, EnumSet.of(
        RecorderState.CAPTURING_REGION,
        RecorderState.WAITING_KEY_COMBO,
        RecorderState.WAITING_USER_INPUT
    ));

    VALID_TRANSITIONS.put(RecorderState.CAPTURING_REGION, EnumSet.of(
        RecorderState.IDLE,
        RecorderState.WAITING_OCR,
        RecorderState.WAITING_PATTERN_VALIDATION
    ));

    VALID_TRANSITIONS.put(RecorderState.WAITING_OCR, EnumSet.of(
        RecorderState.IDLE,
        RecorderState.WAITING_USER_INPUT
    ));

    VALID_TRANSITIONS.put(RecorderState.WAITING_KEY_COMBO, EnumSet.of(
        RecorderState.IDLE
    ));

    VALID_TRANSITIONS.put(RecorderState.WAITING_PATTERN_VALIDATION, EnumSet.of(
        RecorderState.IDLE
    ));

    VALID_TRANSITIONS.put(RecorderState.WAITING_USER_INPUT, EnumSet.of(
        RecorderState.IDLE
    ));
  }

  // ── Listener ──

  public interface StateListener {
    void onStateChanged(RecorderState oldState, RecorderState newState);
  }

  // ── Fields ──

  private volatile RecorderState state = RecorderState.IDLE;
  private DragDropStep dragDropStep = null;
  private String pendingActionType = null;

  private final List<StateListener> listeners = new ArrayList<>();
  private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "RecorderWatchdog");
    t.setDaemon(true);
    return t;
  });
  private ScheduledFuture<?> currentWatchdog = null;

  private static final long OCR_TIMEOUT_SEC = 40;
  private static final long PATTERN_VALIDATION_TIMEOUT_SEC = 10;

  // ── Public API ──

  public RecorderState getState() {
    return state;
  }

  public DragDropStep getDragDropStep() {
    return dragDropStep;
  }

  public String getPendingActionType() {
    return pendingActionType;
  }

  public void addStateListener(StateListener listener) {
    listeners.add(listener);
  }

  /**
   * Transition to a new state. Validates the transition, cancels any
   * active watchdog, starts a new one if needed, and notifies listeners
   * on the EDT.
   *
   * @return true if the transition was valid and executed
   */
  public boolean transitionTo(RecorderState newState) {
    RecorderState oldState = this.state;

    if (oldState == newState) {
      return false;
    }

    Set<RecorderState> allowed = VALID_TRANSITIONS.get(oldState);
    if (allowed == null || !allowed.contains(newState)) {
      System.err.println("[RecorderWorkflow] Invalid transition: " + oldState + " -> " + newState);
      return false;
    }

    // Cancel any active watchdog
    cancelWatchdog();

    this.state = newState;

    // Reset drag-drop sub-state when returning to IDLE
    if (newState == RecorderState.IDLE) {
      dragDropStep = null;
      pendingActionType = null;
    }

    // Start watchdog for timed states
    if (newState == RecorderState.WAITING_OCR) {
      startWatchdog(OCR_TIMEOUT_SEC);
    } else if (newState == RecorderState.WAITING_PATTERN_VALIDATION) {
      startWatchdog(PATTERN_VALIDATION_TIMEOUT_SEC);
    }

    // Notify listeners on EDT
    notifyListeners(oldState, newState);

    return true;
  }

  /**
   * Force return to IDLE from any state. Used for cancel/reset.
   */
  public void reset() {
    RecorderState oldState = this.state;
    cancelWatchdog();
    this.state = RecorderState.IDLE;
    this.dragDropStep = null;
    this.pendingActionType = null;
    if (oldState != RecorderState.IDLE) {
      notifyListeners(oldState, RecorderState.IDLE);
    }
  }

  // ── Action initiators ──

  /**
   * Start a region capture workflow for a given action type.
   * (click, dblClick, rClick, dragDrop, wheel, wait, textClick, textWait, textExists)
   */
  public boolean startCapture(String actionType) {
    if (state != RecorderState.IDLE) {
      return false;
    }
    this.pendingActionType = actionType;
    return transitionTo(RecorderState.CAPTURING_REGION);
  }

  /**
   * Start the drag-drop workflow (two-step capture: source then destination).
   */
  public boolean startDragDrop() {
    if (state != RecorderState.IDLE) {
      return false;
    }
    this.pendingActionType = "dragDrop";
    this.dragDropStep = DragDropStep.SOURCE;
    return transitionTo(RecorderState.CAPTURING_REGION);
  }

  /**
   * Advance drag-drop from SOURCE to DESTINATION capture.
   */
  public boolean advanceDragDrop() {
    if (state != RecorderState.CAPTURING_REGION || dragDropStep != DragDropStep.SOURCE) {
      return false;
    }
    this.dragDropStep = DragDropStep.DESTINATION;
    // Stay in CAPTURING_REGION, notify listeners of sub-state change
    notifyListeners(RecorderState.CAPTURING_REGION, RecorderState.CAPTURING_REGION);
    return true;
  }

  /**
   * Start a key combo capture (one-shot keyboard listener).
   */
  public boolean startKeyComboCApture() {
    if (state != RecorderState.IDLE) {
      return false;
    }
    this.pendingActionType = "keyCombo";
    return transitionTo(RecorderState.WAITING_KEY_COMBO);
  }

  /**
   * Start a text input dialog (type text action).
   */
  public boolean startTextInput() {
    if (state != RecorderState.IDLE) {
      return false;
    }
    this.pendingActionType = "typeText";
    return transitionTo(RecorderState.WAITING_USER_INPUT);
  }

  /**
   * Start a pause/sleep input dialog.
   */
  public boolean startPauseInput() {
    if (state != RecorderState.IDLE) {
      return false;
    }
    this.pendingActionType = "sleep";
    return transitionTo(RecorderState.WAITING_USER_INPUT);
  }

  /**
   * Called after region capture completes. Transitions to the appropriate
   * waiting state based on the pending action type.
   */
  public boolean onCaptureComplete() {
    if (state != RecorderState.CAPTURING_REGION) {
      return false;
    }

    if (pendingActionType != null &&
        (pendingActionType.equals("textClick") ||
         pendingActionType.equals("textWait") ||
         pendingActionType.equals("textExists"))) {
      return transitionTo(RecorderState.WAITING_OCR);
    }

    return transitionTo(RecorderState.WAITING_PATTERN_VALIDATION);
  }

  /**
   * Called when OCR completes and user needs to choose from results.
   */
  public boolean onOcrComplete() {
    if (state != RecorderState.WAITING_OCR) {
      return false;
    }
    return transitionTo(RecorderState.WAITING_USER_INPUT);
  }

  /**
   * Called when an action workflow completes. Returns to IDLE.
   */
  public boolean onActionComplete() {
    if (state == RecorderState.IDLE) {
      return false;
    }
    return transitionTo(RecorderState.IDLE);
  }

  /**
   * Check if the workflow is idle and ready for a new action.
   */
  public boolean isIdle() {
    return state == RecorderState.IDLE;
  }

  /**
   * Shutdown the watchdog executor. Call when disposing RecorderAssistant.
   */
  public void dispose() {
    cancelWatchdog();
    watchdog.shutdownNow();
  }

  // ── Internal ──

  private void startWatchdog(long timeoutSec) {
    currentWatchdog = watchdog.schedule(() -> {
      if (state != RecorderState.IDLE) {
        System.err.println("[RecorderWorkflow] Watchdog timeout in state " + state + " after " + timeoutSec + "s");
        reset();
        SwingUtilities.invokeLater(() ->
            RecorderNotifications.warning("Operation timed out. Ready for next action."));
      }
    }, timeoutSec, TimeUnit.SECONDS);
  }

  private void cancelWatchdog() {
    if (currentWatchdog != null && !currentWatchdog.isDone()) {
      currentWatchdog.cancel(false);
      currentWatchdog = null;
    }
  }

  private void notifyListeners(RecorderState oldState, RecorderState newState) {
    SwingUtilities.invokeLater(() -> {
      for (StateListener listener : listeners) {
        listener.onStateChanged(oldState, newState);
      }
    });
  }
}
