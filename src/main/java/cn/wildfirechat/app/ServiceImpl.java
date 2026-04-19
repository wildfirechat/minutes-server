package cn.wildfirechat.app;

import cn.wildfirechat.app.call.CallService;
import cn.wildfirechat.pojos.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Autowired
    private CallService callService;


//    int ConversationType_Private = 0;
//    int ConversationType_Group = 1;
//    int ConversationType_ChatRoom = 2;
//    int ConversationType_Channel = 3;
//    int ConversationType_Thing = 4;
    @Override
    @Async("asyncExecutor")
    public void onReceiveMessage(OutputMessageData messageData) {
        LOG.info("on receive message {}", messageData.getMessageId());

        if(messageData.getPayload().getType() == 408) {
            try {
                String conferenceId = messageData.getPayload().getContent();
                String jsonStr = new String(Base64.getDecoder().decode(messageData.getPayload().getBase64edData()), StandardCharsets.UTF_8);
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(jsonStr);
                String pin = (String) json.get("p");
                Long longAdvance = (Long) json.get("advanced");
                boolean advance = longAdvance != null && longAdvance > 0;
                callService.joinConference(conferenceId, pin, advance);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onReceiveConferenceEvent(String event) {
        callService.onConferenceEvent(event);
    }
}
