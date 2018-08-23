package io.kungfury.coworker.consul;

import com.jsoniter.annotation.JsonProperty;

import java.util.Map;

/**
 * Describes a ConsulServiceResponse.
 */
public class ConsulServiceResponse {
    @JsonProperty(required = true, value = "ID")
    public String Id;

    @JsonProperty(required = false, value = "Node")
    public String Node;

    @JsonProperty(required = true, value = "Address")
    public String Address;

    @JsonProperty(required = false, value = "Datacenter")
    public String Datacenter;

    @JsonProperty(required = false, value = "TaggedAddresses")
    public Map<String, String> TaggedAddresses;

    @JsonProperty(required = false, value = "NodeMeta")
    public Map<String, String> NodeMeta;

    @JsonProperty(required = false, value = "CreateIndex")
    public long CreateIndex;

    @JsonProperty(required = false, value = "ModifyIndex")
    public long ModifyIndex;

    @JsonProperty(required = true, value = "ServiceAddress")
    public String ServiceAddress;

    @JsonProperty(required = false, value = "ServiceEnableTagOverride")
    public boolean ServiceEnableTagOverride;

    @JsonProperty(required = false, value = "ServiceID")
    public String ServiceId;

    @JsonProperty(required = true, value = "ServiceName")
    public String ServiceName;

    @JsonProperty(required = false, value = "ServicePort")
    public int ServicePort;

    @JsonProperty(required = false, value = "ServiceMeta")
    public Map<String, String> ServiceMeta;

    @JsonProperty(required = false, value = "ServiceTags")
    public String[] ServiceTags;
}