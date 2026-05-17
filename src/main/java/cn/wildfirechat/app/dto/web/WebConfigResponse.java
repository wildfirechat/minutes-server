package cn.wildfirechat.app.dto.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebConfigResponse {
    private String appId;
    private int appType;
    private long timestamp;
    private String nonceStr;
    private String signature;

    public WebConfigResponse() {
    }

    public WebConfigResponse(String appId, int appType, long timestamp, String nonceStr, String signature) {
        this.appId = appId;
        this.appType = appType;
        this.timestamp = timestamp;
        this.nonceStr = nonceStr;
        this.signature = signature;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public int getAppType() {
        return appType;
    }

    public void setAppType(int appType) {
        this.appType = appType;
    }

    /**
     * 兼容部分客户端读取小写的 apptype
     */
    @JsonProperty("apptype")
    public int getApptype() {
        return appType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getNonceStr() {
        return nonceStr;
    }

    public void setNonceStr(String nonceStr) {
        this.nonceStr = nonceStr;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
