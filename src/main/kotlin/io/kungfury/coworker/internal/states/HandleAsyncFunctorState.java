package io.kungfury.coworker.internal.states;

import com.jsoniter.annotation.JsonProperty;
import com.jsoniter.any.Any;

public class HandleAsyncFunctorState {
    @JsonProperty
    public Any[] args;

    @JsonProperty
    public DelayedLambdaState methodState;

    @JsonProperty
    public boolean isJava;

    @JsonProperty
    public long version;
}
