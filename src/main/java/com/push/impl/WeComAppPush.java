package com.push.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.oldwu.constant.URLConstant;
import com.oldwu.util.HttpUtils;
import com.push.AbstractPush;
import com.push.model.PushMetaInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 企业微信应用PUSH
 *
 * @author itning
 * @since 2021/9/22 14:41
 */
@Slf4j
public class WeComAppPush extends AbstractPush {

    private static final int WE_COM_APP_MESSAGE_MAX_LENGTH = 1000;

    @Override
    protected String generatePushUrl(PushMetaInfo metaInfo) {

        Map<String, String> params = new HashMap<>();
        params.put("corpid", metaInfo.getToken());
        params.put("corpsecret", metaInfo.getSecret());
        JSONObject jsonObject = null;
        try {
            HttpResponse httpResponse = HttpUtils.doGet(URLConstant.PUSH_WECOM_APP_PUSH_GET_TOKEN, null, HttpUtils.getHeaders(), params);
            jsonObject = HttpUtils.getJson(httpResponse);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (null == jsonObject) {
            throw new RuntimeException("获取企业微信凭证失败，JsonObject Is Null");
        }
        String accessToken = jsonObject.getString("access_token");
        if (null == accessToken) {
            throw new RuntimeException("获取企业微信凭证失败，access_token Is Null");
        }
        if (StringUtils.isBlank(accessToken)) {
            throw new RuntimeException("获取企业微信凭证失败，access_token Is Blank");
        }

        return URLConstant.PUSH_WECOM_APP_PUSH + "?access_token=" + accessToken;
    }

    @Override
    protected String generatePushBody(PushMetaInfo metaInfo, String content) {
        content = content.replaceAll("\r", "").replaceAll("\n\n", "\n");
        WeComMessageSendRequest request = new WeComMessageSendRequest();
        request.setToUser(metaInfo.getToUser());
        request.setAgentId(metaInfo.getAgentId());
        //System.out.println(StringUtils.isBlank(metaInfo.getMediaid()));
        if (StringUtils.isBlank(metaInfo.getMediaid())) {
            request.setMsgType("text");
            WeComMessageSendRequest.Text text = new WeComMessageSendRequest.Text();
            text.setContent(content);
            request.setText(text);
        } else {
            request.setMsgType("mpnews");
            WeComMessageSendRequest.Articles Articles = new WeComMessageSendRequest.Articles();
            Articles.setAuthor("每日签到小助手");
            Articles.setTitle("任务简报");
            Articles.setDigest(content);
            Articles.setContent(content.replaceAll("\n", "<br>"));
            Articles.setThumb_media_id(metaInfo.getMediaid());
            WeComMessageSendRequest.Mpnews Mpnews = new WeComMessageSendRequest.Mpnews();
            Mpnews.setArticles(Collections.singletonList(Articles));
            request.setMpnews(Mpnews);
        }
        return JSON.toJSONString(request);
    }

    @Override
    protected List<String> segmentation(PushMetaInfo metaInfo, String pushBody) {
        if (StringUtils.isBlank(pushBody)) {
            return Collections.emptyList();
        }
        if (pushBody.length() > WE_COM_APP_MESSAGE_MAX_LENGTH && StringUtils.isBlank(metaInfo.getMediaid())) {
            log.info("推送内容长度[{}]大于最大长度[{}]进行分割处理", pushBody.length(), WE_COM_APP_MESSAGE_MAX_LENGTH);
            List<String> pushContent = Arrays.stream(splitStringByLength(pushBody, WE_COM_APP_MESSAGE_MAX_LENGTH)).collect(Collectors.toList());
            log.info("分割数量：{}", pushContent.size());
            return pushContent;
        }

        return Collections.singletonList(pushBody);
    }

    @Override
    protected boolean checkPushStatus(JSONObject jsonObject) {

        if (jsonObject == null) {
            return false;
        }

        Integer errcode = jsonObject.getInteger("errcode");
        String errmsg = jsonObject.getString("errmsg");
        if (null == errcode || null == errmsg) {
            return false;
        }

        return errcode == 0 && "ok".equals(errmsg);
    }

    @Data
    public static class WeComMessageSendRequest implements Serializable {
        /**
         * 指定接收消息的成员，成员ID列表（多个接收者用‘|’分隔，最多支持1000个）。
         * 特殊情况：指定为”@all”，则向该企业应用的全部成员发送
         */
        @JSONField(name = "touser")
        private String toUser;
        /**
         * 指定接收消息的部门，部门ID列表，多个接收者用‘|’分隔，最多支持100个。
         * 当touser为”@all”时忽略本参数
         */
        @JSONField(name = "toparty")
        private String toParty;
        /**
         * 指定接收消息的标签，标签ID列表，多个接收者用‘|’分隔，最多支持100个。
         * 当touser为”@all”时忽略本参数
         */
        @JSONField(name = "totag")
        private String toTag;
        /**
         * 消息类型
         */
        @JSONField(name = "msgtype")
        private String msgType;
        /**
         * 企业应用的id，整型。企业内部开发，可在应用的设置页面查看；第三方服务商，可通过接口 获取企业授权信息 获取该参数值
         */
        @JSONField(name = "agentid")
        private Integer agentId;
        /**
         * 文本内容
         */
        @JSONField(name = "text")
        private Text text;
        /**
         * 图文内容
         */
        @JSONField(name = "mpnews")
        private Mpnews mpnews;

        /**
         * 表示是否是保密消息，0表示否，1表示是，默认0
         */
        @JSONField(name = "safe")
        private Integer safe;
        /**
         * 表示是否开启id转译，0表示否，1表示是，默认0。仅第三方应用需要用到，企业自建应用可以忽略。
         */
        @JSONField(name = "enable_id_trans")
        private Integer enableIdTrans;
        /**
         * 表示是否开启重复消息检查，0表示否，1表示是，默认0
         */
        @JSONField(name = "enable_duplicate_check")
        private Integer enableDuplicateCheck;
        /**
         * 表示是否重复消息检查的时间间隔，默认1800s，最大不超过4小时
         */
        @JSONField(name = "duplicate_check_interval")
        private Integer duplicateCheckInterval;

        @Data
        public static class Text implements Serializable {
            /**
             * 消息内容，最长不超过2048个字节
             */
            @JSONField(name = "content")
            private String content;
        }

        @Data
        public static class Mpnews implements Serializable {
            @JSONField(name = "articles")
            private List<Articles> articles;
        }

        @Data
        public static class Articles implements Serializable {
            /**
             * 标题，不超过128个字节，超过会自动截断（支持id转译）
             */
            @JSONField(name = "title")
            private String title;
            /**
             * 图文消息缩略图的media_id, 可以通过素材管理接口获得。此处thumb_media_id即上传接口返回的media_id
             */
            @JSONField(name = "thumb_media_id")
            private String thumb_media_id;
            /**
             * 图文消息的作者，不超过64个字节
             */
            @JSONField(name = "author")
            private String author;

            @JSONField(name = "content_source_url")
            private String content_source_url;
            /**
             * 消息内容，支持html标签，不超过666 K个字节
             */
            @JSONField(name = "content")
            private String content;
            /**
             * 图文消息的描述，不超过512个字节，超过会自动截断（支持id转译）
             */
            @JSONField(name = "digest")
            private String digest;
        }
    }
}
