package io.github.chrisruffalo.quarkus.streaming.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class StreamResponse {

    long receivedSize;

    public long getReceivedSize() {
        return receivedSize;
    }

    public void setReceivedSize(long receivedSize) {
        this.receivedSize = receivedSize;
    }
}
