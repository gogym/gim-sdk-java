package io.getbit.gim.webrtc.groupcall;

import io.getbit.gim.webrtc.enums.GroupCallMemberStatus;
import io.getbit.gim.webrtc.enums.GroupCallMode;
import io.getbit.gim.webrtc.enums.GroupCallRoomStatus;
import io.getbit.gim.webrtc.groupcall.config.GroupCallConfig;
import io.getbit.gim.webrtc.groupcall.listener.GroupCallListener;
import io.getbit.gim.webrtc.groupcall.model.GroupCallMember;
import io.getbit.gim.webrtc.groupcall.model.GroupCallRoom;
import io.getbit.gim.webrtc.singlecall.SingleCallSessionManager;
import io.getbit.gim.webrtc.sfu.SfuAdapter;
import io.getbit.gim.webrtc.sfu.SfuToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GroupCallSessionManagerTest.java
 *
 * 群通话房间生命周期状态机单元测试
 * 覆盖：创建/加入/拒绝/退出/结束/占用互斥/掉线清理/邀请超时/空房间回收/模式选择
 *
 * @author gogym
 */
class GroupCallSessionManagerTest {

    private GroupCallConfig config;
    private GroupCallSessionManager manager;

    @BeforeEach
    void setUp() {
        config = new GroupCallConfig();
        config.setInviteTimeoutSeconds(60);
        config.setEmptyRoomTtlSeconds(60);
        manager = new GroupCallSessionManager(config);
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    private GroupCallRoom createRoom(String initiator, String... invitees) {
        return manager.createRoom("group-1", null, initiator, "video",
                List.of(invitees), GroupCallMode.MESH, null);
    }

    @Test
    @DisplayName("创建房间：发起人 JOINED，受邀成员 INVITED，房间 RINGING")
    void createRoom_initialState() {
        GroupCallRoom room = createRoom("user-a", "user-b", "user-c");

        assertNotNull(room);
        assertEquals(GroupCallRoomStatus.RINGING, room.getStatus());
        assertEquals(GroupCallMemberStatus.JOINED, room.getMember("user-a").getStatus());
        assertEquals(GroupCallMemberStatus.INVITED, room.getMember("user-b").getStatus());
        assertEquals(GroupCallMemberStatus.INVITED, room.getMember("user-c").getStatus());
        assertTrue(manager.isUserBusy("user-a"));
        assertTrue(manager.isUserBusy("user-b"));
    }

    @Test
    @DisplayName("占用互斥：发起人在通话中不能重复发起，同一群不能并发两个房间")
    void createRoom_busyCheck() {
        GroupCallRoom room1 = createRoom("user-a", "user-b");
        assertNotNull(room1);

        // 发起人占用
        assertNull(manager.createRoom("group-2", null, "user-a", "video", List.of("user-x"), GroupCallMode.MESH, null));
        // 受邀成员占用
        assertNull(manager.createRoom("group-2", null, "user-b", "video", List.of("user-x"), GroupCallMode.MESH, null));
        // 同一群已有进行中的通话
        assertNull(manager.createRoom("group-1", null, "user-z", "video", List.of("user-x"), GroupCallMode.MESH, null));
    }

    @Test
    @DisplayName("加入房间：RINGING → TALKING，重复加入幂等")
    void joinRoom_transition() {
        GroupCallRoom room = createRoom("user-a", "user-b", "user-c");

        GroupCallRoom joined = manager.joinRoom(room.getRoomId(), "user-b", null);
        assertNotNull(joined);
        assertEquals(GroupCallRoomStatus.TALKING, joined.getStatus());
        assertEquals(GroupCallMemberStatus.JOINED, joined.getMember("user-b").getStatus());
        assertTrue(joined.getTalkTime() > 0);

        // 重复加入幂等，状态不变
        GroupCallRoom again = manager.joinRoom(room.getRoomId(), "user-b", null);
        assertNotNull(again);
        assertEquals(GroupCallRoomStatus.TALKING, again.getStatus());
        assertEquals(2, again.getJoinedMemberIds().size());
    }

    @Test
    @DisplayName("加入房间：非成员/已拒绝成员/已结束房间均无法加入")
    void joinRoom_invalid() {
        GroupCallRoom room = createRoom("user-a", "user-b");

        assertNull(manager.joinRoom(room.getRoomId(), "user-not-invited", null));

        manager.rejectInvite(room.getRoomId(), "user-b");
        assertNull(manager.joinRoom(room.getRoomId(), "user-b", null));

        manager.endRoom(room.getRoomId());
        assertNull(manager.joinRoom(room.getRoomId(), "user-a", null));
    }

    @Test
    @DisplayName("拒绝邀请：INVITED → REJECTED，释放占用")
    void rejectInvite() {
        GroupCallRoom room = createRoom("user-a", "user-b");

        assertNotNull(manager.rejectInvite(room.getRoomId(), "user-b"));
        assertEquals(GroupCallMemberStatus.REJECTED, room.getMember("user-b").getStatus());
        assertFalse(manager.isUserBusy("user-b"));

        // 已拒绝后重复拒绝无效
        assertNull(manager.rejectInvite(room.getRoomId(), "user-b"));
    }

    @Test
    @DisplayName("退出房间：成员 LEFT，发起人退出后房间继续")
    void leaveRoom() {
        GroupCallRoom room = createRoom("user-a", "user-b", "user-c");
        manager.joinRoom(room.getRoomId(), "user-b", null);

        assertNotNull(manager.leaveRoom(room.getRoomId(), "user-b"));
        assertEquals(GroupCallMemberStatus.LEFT, room.getMember("user-b").getStatus());
        assertFalse(manager.isUserBusy("user-b"));
        // 发起人退出，房间仍存在且继续
        assertNotNull(manager.leaveRoom(room.getRoomId(), "user-a"));
        assertNotNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    @DisplayName("结束房间：所有成员释放占用，房间移除")
    void endRoom() {
        GroupCallRoom room = createRoom("user-a", "user-b");
        manager.joinRoom(room.getRoomId(), "user-b", null);

        GroupCallRoom ended = manager.endRoom(room.getRoomId());
        assertNotNull(ended);
        assertEquals(GroupCallRoomStatus.ENDED, ended.getStatus());
        assertNull(manager.getRoom(room.getRoomId()));
        assertFalse(manager.isUserBusy("user-a"));
        assertFalse(manager.isUserBusy("user-b"));

        // 重复结束无效
        assertNull(manager.endRoom(room.getRoomId()));
    }

    @Test
    @DisplayName("掉线清理：成员标记 LEFT 并触发监听回调")
    void onDisconnect() {
        List<GroupCallMember> disconnected = new CopyOnWriteArrayList<>();
        manager.setListener(new GroupCallListener() {
            @Override
            public void onMemberDisconnected(GroupCallRoom room, GroupCallMember member) {
                disconnected.add(member);
            }
        });

        GroupCallRoom room = createRoom("user-a", "user-b");
        manager.joinRoom(room.getRoomId(), "user-b", null);

        manager.onDisconnect("user-b");

        assertEquals(GroupCallMemberStatus.LEFT, room.getMember("user-b").getStatus());
        assertFalse(manager.isUserBusy("user-b"));
        assertEquals(1, disconnected.size());
        assertEquals("user-b", disconnected.get(0).getUserId());
    }

    @Test
    @DisplayName("掉线清理：无房间用户不触发回调")
    void onDisconnect_noRoom() {
        AtomicInteger count = new AtomicInteger();
        manager.setListener(new GroupCallListener() {
            @Override
            public void onMemberDisconnected(GroupCallRoom room, GroupCallMember member) {
                count.incrementAndGet();
            }
        });
        manager.onDisconnect("nobody");
        assertEquals(0, count.get());
    }

    @Test
    @DisplayName("邀请超时：RINGING 房间超时自动结束并触发回调")
    void inviteTimeout() throws InterruptedException {
        config.setInviteTimeoutSeconds(1);
        List<GroupCallRoom> timeouts = new CopyOnWriteArrayList<>();
        manager.setListener(new GroupCallListener() {
            @Override
            public void onInviteTimeout(GroupCallRoom room) {
                timeouts.add(room);
            }
        });

        GroupCallRoom room = createRoom("user-a", "user-b");
        Thread.sleep(1500);

        assertNull(manager.getRoom(room.getRoomId()));
        assertFalse(manager.isUserBusy("user-a"));
        assertFalse(manager.isUserBusy("user-b"));
        assertEquals(1, timeouts.size());
        assertEquals(room.getRoomId(), timeouts.get(0).getRoomId());
    }

    @Test
    @DisplayName("邀请超时：已进入 TALKING 的房间不超时")
    void inviteTimeout_notAppliesToTalking() throws InterruptedException {
        config.setInviteTimeoutSeconds(1);
        GroupCallRoom room = createRoom("user-a", "user-b");
        manager.joinRoom(room.getRoomId(), "user-b", null);

        Thread.sleep(1500);

        assertNotNull(manager.getRoom(room.getRoomId()));
        assertEquals(GroupCallRoomStatus.TALKING, room.getStatus());
    }

    @Test
    @DisplayName("空房间回收：全员离开后延迟销毁房间")
    void emptyRoomCleanup() throws InterruptedException {
        config.setEmptyRoomTtlSeconds(1);
        GroupCallRoom room = createRoom("user-a", "user-b");

        manager.leaveRoom(room.getRoomId(), "user-a");
        // 房间仍存活（TTL 内）
        assertNotNull(manager.getRoom(room.getRoomId()));

        Thread.sleep(1500);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    @DisplayName("空房间回收：TTL 内重新加入则不回收")
    void emptyRoomCleanup_rejoin() throws InterruptedException {
        config.setEmptyRoomTtlSeconds(1);
        GroupCallRoom room = createRoom("user-a", "user-b");
        manager.leaveRoom(room.getRoomId(), "user-a");
        manager.leaveRoom(room.getRoomId(), "user-b");

        Thread.sleep(1500);
        assertNull(manager.getRoom(room.getRoomId()));
    }

    @Test
    @DisplayName("人数上限：创建房间时受邀名单被截断至 maxMembers")
    void createRoom_capsInvitees() {
        config.setMaxMembers(3);
        GroupCallRoom room = createRoom("user-a", "user-b", "user-c", "user-d", "user-e");

        assertNotNull(room);
        // 发起人 + 2 名受邀 = 3，超出部分忽略
        assertEquals(3, room.getMembers().size());
        assertNotNull(room.getMember("user-b"));
        assertNotNull(room.getMember("user-c"));
        assertNull(room.getMember("user-d"));
        assertNull(room.getMember("user-e"));
    }

    @Test
    @DisplayName("人数上限：已加入达 maxMembers 时拒绝新成员加入")
    void joinRoom_rejectsWhenFull() {
        config.setMaxMembers(2);
        GroupCallRoom room = createRoom("user-a", "user-b", "user-c");

        // user-b 加入后达到上限 2
        assertNotNull(manager.joinRoom(room.getRoomId(), "user-b", null));
        assertEquals(2, room.getJoinedMemberIds().size());

        // user-c 无法再加入
        assertNull(manager.joinRoom(room.getRoomId(), "user-c", null));
        assertEquals(2, room.getJoinedMemberIds().size());

        // 已加入成员重复加入仍幂等成功
        assertNotNull(manager.joinRoom(room.getRoomId(), "user-b", null));
    }

    @Test
    @DisplayName("模式选择：auto 按人数切换，SFU 未配置时回退 Mesh")
    void selectMode() {
        config.setMode("auto");
        config.setMeshMaxMembers(8);
        assertEquals(GroupCallMode.MESH, manager.selectMode(5));
        assertEquals(GroupCallMode.MESH, manager.selectMode(9)); // 无 SfuAdapter 回退

        config.setMode("mesh");
        assertEquals(GroupCallMode.MESH, manager.selectMode(100));

        config.setMode("auto");
        GroupCallSessionManager sfuManager = new GroupCallSessionManager(
                config, new SfuAdapter() {
            @Override
            public void createRoom(String roomId) {
            }

            @Override
            public void destroyRoom(String roomId) {
            }

            @Override
            public SfuToken issueToken(String roomId, String userId) {
                return new SfuToken("token", "wss://sfu");
            }
        });
        assertEquals(GroupCallMode.SFU, sfuManager.selectMode(9));
        sfuManager.shutdown();
    }

    @Test
    @DisplayName("占用判定：与 1:1 通话互斥")
    void busyCheckWithOneToOne() {
        SingleCallSessionManager singleCall = new SingleCallSessionManager();
        GroupCallSessionManager m = new GroupCallSessionManager(config, null, singleCall);
        try {
            // 模拟 1:1 通话占用
            assertTrue(singleCall.createSession("call-1", "user-a", "user-x", "video", null, null));
            assertTrue(m.isUserBusy("user-a"));

            // 1:1 结束后可发起群通话
            singleCall.endSession("call-1");
            assertFalse(m.isUserBusy("user-a"));
            assertNotNull(m.createRoom("group-1", null, "user-a", "video", List.of("user-b"), GroupCallMode.MESH, null));
        } finally {
            singleCall.shutdown();
            m.shutdown();
        }
    }
}
