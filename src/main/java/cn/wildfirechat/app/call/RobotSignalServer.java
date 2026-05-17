package cn.wildfirechat.app.call;

import cn.wildfirechat.ConferenceResponseCallback;
import cn.wildfirechat.SignalServer;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.pojos.SendMessageResult;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;

/**
 * 直连模式的 SignalServer 实现，委托给 RobotService。
 */
public class RobotSignalServer implements SignalServer {

    private final RobotService robotService;

    public RobotSignalServer(RobotService robotService) {
        this.robotService = robotService;
    }

    @Override
    public long sendMessage(String robotId, Conversation conversation, MessagePayload payload) {
        try {
            IMResult<SendMessageResult> imResult = robotService.sendMessage(robotId, conversation, payload);
            if (imResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                return imResult.getResult().getMessageUid();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    @Override
    public void sendConference(String robotId, String clientId, String request, long sessionId, String roomId, String data, boolean advance, ConferenceResponseCallback callback) {
        try {
            IMResult<String> imResult = robotService.sendConferenceRequest(robotId, clientId, request, sessionId, roomId, data, advance);
            if (imResult != null) {
                if (imResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    callback.onResponse(imResult.getResult());
                } else {
                    callback.onError(imResult.getCode());
                }
            } else {
                callback.onError(-1);
            }
        } catch (Exception e) {
            callback.onError(-2);
            e.printStackTrace();
        }
    }
}
