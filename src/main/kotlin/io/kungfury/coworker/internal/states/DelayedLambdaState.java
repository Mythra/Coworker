package io.kungfury.coworker.internal.states;

import com.jsoniter.annotation.JsonProperty;

public class DelayedLambdaState {
    @JsonProperty
    public byte[] serializedClosure;
}
