package io.getbit.gim.webrtc.groupcall;

import io.getbit.gim.core.bootstrap.IMServerFacade;
import io.getbit.gim.core.config.properties.GimProperties;
import io.getbit.gim.core.connection.channel.ChannelManager;
import io.getbit.gim.core.routing.UserRouteService;
import io.getbit.gim.core.spi.ImRedisAdapter;
import io.getbit.gim.protocol.codec.ImProto;
import io.getbit.gim.webrtc.enums.GroupCallMode;
import io.getbit.gim.webrtc.enums.GroupCallRoomStatus;
import io.getbit.gim.webrtc.groupcall.config.GroupCallConfig;
import io.getbit.gim.webrtc.enums.GroupSignalType;
import io.getbit.gim.webrtc.groupcall.listener.ImGroupCallListener;
import io.getbit.gim.webrtc.groupcall.model.GroupCallMember;
import io.getbit.gim.webrtc.groupcall.model.GroupCallRoom;
import io.getbit.gim.webrtc.util.RtcSignalValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GroupCallServiceTest.java
 * 覆盖群通话媒体开关同步（mediaState）、业务事件回调（话单）与房间时长定格
 *
 * @author gogym
 */
class GroupCallServiceTest {

    private GroupCallSessionManager manager;
    private GroupCallService service;
    private List<String> events;

    @BeforeEach
    void setUp() {
        manager = new GroupCallSessionManager(new GroupCallConfig());
        events = new CopyOnWriteArrayList<>();
        service = new GroupCallService(minimalFacade(), manager, null, null,
                List.of(new ImGroupCallListener() {
                    @Override
                    public void onCallStart(String callId, String groupId, String roomId, String initiatorId,
                                            String callType, GroupCallMode mode) {
                        events.add("start:" + callId);
                    }

                    @Override
                    public void onCallEnd(String callId, String groupId, String roomId, String initiatorId,
                                          String callType, GroupCallMode mode,
                                          long durationSeconds, String endReason) {
                        events.add("end:" + endReason + ":" + durationSeconds);
                    }

                    @Override
                    public void onMemberJoin(String callId, String roomId, String userId) {
                        events.add("join:" + userId);
                    }

                    @Override
                    public void onMemberLeave(String callId, String roomId, String userId, String reason) {
                        events.add("leave:" + userId + ":" + reason);
                    }
                }));
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    /**
     * 最小可用门面：仅装配路由/连接组件（信令下发在无集群时降级为离线，不影响状态断言）
     */
    private IMServerFacade minimalFacade() {
        GimProperties config = new GimProperties();
        return new IMServerFacade.Builder()
                .config(config)
                .channelManager(new ChannelManager(config))
                .userRouteService(new UserRouteService(config, noopRedisAdapter()))
                .build();
    }

    /**
     * 空实现 Redis 适配器：保证路由查询不异常，所有用户均视为离线
     */
    private ImRedisAdapter noopRedisAdapter() {
        return new ImRedisAdapter() {
            @Override
            public void setex(String key, int seconds, String value) {
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public void del(String key) {
            }

            @Override
            public void publish(String channel, String message) {
            }
        };
    }

    private ImProto.RtcGroup group(GroupSignalType type, String senderId, String groupId, String roomId, String payload) {
        ImProto.RtcGroup.Builder builder = ImProto.RtcGroup.newBuilder()
                .setSignalType(type.getCode())
                .setSenderId(senderId);
        if (groupId != null) {
            builder.setGroupId(groupId);
        }
        if (roomId != null) {
            builder.setRoomId(roomId);
        }
        if (payload != null) {
            builder.setPayload(payload);
        }
        return builder.build();
    }

    /**
     * 发起群通话（callId 固定为 call-1，受邀成员 u2/u3）
     *
     * @return 房间ID
     */
    private String startRoom(String groupId, String initiatorId) {
        ImProto.RtcGroup request = ImProto.RtcGroup.newBuilder()
                .setSignalType(GroupSignalType.GROUP_CALL_REQUEST.getCode())
                .setSenderId(initiatorId)
                .setGroupId(groupId)
                .setCallId("call-1")
                .setPayload("{\"callType\":\"video\",\"inviteeIds\":[\"u2\",\"u3\"]}")
                .build();
        service.handle(null, null, initiatorId, request);

        GroupCallRoom room = manager.getRoomByGroup(groupId);
        assertNotNull(room, "群通话房间应已创建");
        return room.getRoomId();
    }

    // ====================== 媒体开关同步 ======================

    @Test
    @DisplayName("mediaState(17)：更新成员摄像头/麦克风状态，未上报项保持 null")
    void mediaStateUpdatesMember() {
        String roomId = startRoom("g1", "u1");
        service.handle(null, null, "u2", group(GroupSignalType.GROUP_CALL_JOIN, "u2", "g1", roomId, null));

        service.handle(null, null, "u2", group(GroupSignalType.MEDIA_STATE, "u2", "g1", roomId, "{\"camera\":false}"));

        GroupCallMember member = manager.getRoom(roomId).getMember("u2");
        assertEquals(Boolean.FALSE, member.getCamera());
        assertNull(member.getMic(), "未上报项不应被改写");

        // 增量上报：仅 mic 变化，camera 保持原值
        service.handle(null, null, "u2", group(GroupSignalType.MEDIA_STATE, "u2", "g1", roomId, "{\"mic\":true}"));
        assertEquals(Boolean.FALSE, member.getCamera());
        assertEquals(Boolean.TRUE, member.getMic());
    }

    @Test
    @DisplayName("mediaState(17)：非成员/房间不存在/无效 payload 时忽略，不抛异常")
    void mediaStateIgnoresInvalid() {
        String roomId = startRoom("g2", "u1");

        // 非房间成员上报
        service.handle(null, null, "stranger", group(GroupSignalType.MEDIA_STATE, "stranger", "g2", roomId, "{\"mic\":false}"));
        assertNull(manager.getRoom(roomId).getMember("stranger"));

        // 房间不存在
        service.handle(null, null, "u1", group(GroupSignalType.MEDIA_STATE, "u1", "g2", "room-none", "{\"mic\":true}"));

        // payload 缺少 camera/mic（均为 null 视为无效）
        service.handle(null, null, "u1", group(GroupSignalType.MEDIA_STATE, "u1", "g2", roomId, "{}"));
        assertNull(manager.getRoom(roomId).getMember("u1").getMic());
    }

    @Test
    @DisplayName("校验：mediaState(17) 需携带 camera/mic 至少一项")
    void validatorMediaState() {
        assertTrue(RtcSignalValidator.validateGroupLifecyclePayload(17, "{\"camera\":true}", "u1"));
        assertTrue(RtcSignalValidator.validateGroupLifecyclePayload(17, "{\"mic\":false}", "u1"));
        assertFalse(RtcSignalValidator.validateGroupLifecyclePayload(17, "{}", "u1"));
        assertFalse(RtcSignalValidator.validateGroupLifecyclePayload(17, "", "u1"));
    }

    // ====================== 业务事件（话单） ======================

    @Test
    @DisplayName("业务事件：发起/加入/退出/结束全流程，成员加入幂等不重复触发")
    void businessEvents() {
        String roomId = startRoom("g3", "u1");
        assertTrue(events.contains("start:call-1"), "房间创建后应触发 onCallStart");

        service.handle(null, null, "u2", group(GroupSignalType.GROUP_CALL_JOIN, "u2", "g3", roomId, null));
        // 幂等重连：重复 join 不重复触发业务事件
        service.handle(null, null, "u2", group(GroupSignalType.GROUP_CALL_JOIN, "u2", "g3", roomId, null));
        assertEquals(1, events.stream().filter("join:u2"::equals).count());

        service.handle(null, null, "u2", group(GroupSignalType.GROUP_CALL_LEAVE, "u2", "g3", roomId, null));
        assertTrue(events.contains("leave:u2:leave"));

        service.handle(null, null, "u1", group(GroupSignalType.GROUP_CALL_END, "u1", "g3", roomId, null));
        assertTrue(events.stream().anyMatch(e -> e.startsWith("end:ended:")), "发起人结束应触发 onCallEnd(ended)");
        assertNull(manager.getRoom(roomId), "结束后房间应被移除");
    }

    @Test
    @DisplayName("掉线清理：触发成员离开事件，原因为 disconnect")
    void disconnectFiresLeaveEvent() {
        String roomId = startRoom("g4", "u1");
        service.handle(null, null, "u2", group(GroupSignalType.GROUP_CALL_JOIN, "u2", "g4", roomId, null));

        manager.onDisconnect("u2");

        assertTrue(events.contains("leave:u2:disconnect"));
    }

    @Test
    @DisplayName("房间时长：ENDED 后按 endTime 定格，不再返回 0")
    void durationFrozenOnEnd() throws InterruptedException {
        String roomId = startRoom("g5", "u1");
        service.handle(null, null, "u2", group(GroupSignalType.GROUP_CALL_JOIN, "u2", "g5", roomId, null));
        GroupCallRoom room = manager.getRoom(roomId);

        Thread.sleep(1100);
        service.handle(null, null, "u1", group(GroupSignalType.GROUP_CALL_END, "u1", "g5", roomId, null));

        assertEquals(GroupCallRoomStatus.ENDED, room.getStatus());
        assertTrue(room.getDurationSeconds() >= 1, "结束后时长应按 endTime 定格而非返回 0");
        assertTrue(events.stream().anyMatch(e -> e.startsWith("end:ended:") && !e.endsWith(":0")),
                "话单时长应大于 0");
    }
}
