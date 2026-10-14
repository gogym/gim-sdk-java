/*
 * Copyright 2026 gogym
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getbit.gim.webrtc.dto;

import lombok.Data;

/**
 * TurnCredentialsDto.java
 *
 * TURN/STUN 临时凭证 DTO
 * 由 TurnCredentialService 按 RESTTURN 协议（HMAC-SHA1）生成，
 * 注入信令 payload（key=turn）或随 roomState 下发，客户端据此构建 ICE Server
 *
 * @author gogym
 */
@Data
public class TurnCredentialsDto {

    /**
     * STUN 服务器地址
     */
    private String stunUrl;

    /**
     * TURN 服务器地址
     */
    private String turnUrl;

    /**
     * 临时用户名（过期时间戳，秒）
     */
    private String username;

    /**
     * HMAC-SHA1 签名凭证
     */
    private String credential;
}
