/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.testkit.replay;

import java.util.Collections;
import java.util.List;

public final class ReplayResult {

    public enum Status {
        COMPLETED,
        TIMED_OUT
    }

    private final Status status;
    private final List<State3d> trace;

    public ReplayResult(Status status, List<State3d> trace) {
        this.status = status;
        this.trace = List.copyOf(trace);
    }

    public Status status() {
        return status;
    }

    public List<State3d> trace() {
        return Collections.unmodifiableList(trace);
    }

    public State3d finalState() {
        return trace.get(trace.size() - 1);
    }

    public int steps() {
        return trace.size() - 1;
    }
}
