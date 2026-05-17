package cn.wildfirechat.app.dto.web;

public class LoginResponse {
    private String userId;
    private String displayName;
    private String portraitUrl;

    public LoginResponse() {
    }

    public LoginResponse(String userId, String displayName, String portraitUrl) {
        this.userId = userId;
        this.displayName = displayName;
        this.portraitUrl = portraitUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPortraitUrl() {
        return portraitUrl;
    }

    public void setPortraitUrl(String portraitUrl) {
        this.portraitUrl = portraitUrl;
    }
}
