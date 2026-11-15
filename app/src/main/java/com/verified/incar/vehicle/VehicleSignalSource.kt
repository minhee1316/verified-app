package com.verified.incar.vehicle

import com.verified.incar.core.Capabilities

/** 읽기 전용 차 신호 연결. 시연과 실제 연결의 전환을 같은 경계에서 검증한다. */
interface VehicleSignalSource {
    val connected: Boolean
    val caps: Capabilities
    val live: Set<String>
    var paused: Int
    /** 비동기 차량 서비스 연결·재연결. 기존 동기 신호 소스는 구현하지 않아도 된다. */
    fun setConnectionListener(listener: (Boolean) -> Unit) = Unit
    fun connect(): Boolean
    fun resubscribeIfGranted(): Boolean
    fun refresh(name: String)
    fun disconnect()
}
