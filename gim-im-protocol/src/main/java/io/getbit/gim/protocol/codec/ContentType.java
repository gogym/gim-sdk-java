package io.getbit.gim.protocol.codec;

import lombok.Getter;

import java.util.Arrays;

/**
 * ContentType.java
 * <p>
 * 消息内容类型枚举
 * 对应 ChatMessage.contentType 字段
 */
public enum ContentType {

    /**
     * 文字消息
     */
    TEXT(1, "文字", "[消息]"),

    /**
     * 图片消息
     */
    IMAGE(2, "图片", "[图片]"),

    /**
     * 音频消息
     */
    AUDIO(3, "语音", "[语音]"),

    /**
     * 视频消息
     */
    VIDEO(4, "视频", "[视频]"),

    /**
     * 文件消息
     */
    FILE(5, "文件", "[文件]"),

    /**
     * 位置消息
     */
    LOCATION(6, "位置", "[位置]"),

    /**
     * 自定义消息
     */
    CUSTOM(7, "自定义", "[自定义消息]"),

    /**
     * 通话记录
     */
    CALL_RECORD(8, "通话记录", "[通话记录]");

    @Getter
    private final int value;
    @Getter
    private final String label;
    @Getter
    private final String pushText;

    ContentType(int value, String label, String pushText) {
        this.value = value;
        this.label = label;
        this.pushText = pushText;
    }

    /**
     * 获取推送通知文案
     * 对于文字消息，如果传入的 content 非空则返回 content 本身，否则返回 pushText
     *
     * @param content 消息内容（仅对 TEXT 类型有效）
     * @return 推送展示文案
     */
    public String getPushText(String content) {
        if (this == TEXT && content != null && !content.isEmpty()) {
            return content;
        }
        return pushText;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 类型值
     * @return 枚举值，未找到返回 null
     */
    public static ContentType fromValue(int value) {
        return Arrays.stream(values())
                .filter(t -> t.value == value)
                .findFirst()
                .orElse(null);
    }

}
