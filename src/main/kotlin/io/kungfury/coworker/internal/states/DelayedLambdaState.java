package io.kungfury.coworker.internal.states;

import com.jsoniter.annotation.JsonProperty;

/**
 * Define the state of a delayed lambda.
 */
public class DelayedLambdaState {
    @JsonProperty
    public byte[] serializedClosure;
}
