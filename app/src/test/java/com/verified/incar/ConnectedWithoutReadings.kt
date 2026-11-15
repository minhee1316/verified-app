package com.verified.incar

import com.verified.incar.core.Capabilities
import com.verified.incar.vehicle.VehicleSignalSource

/** 차량 서비스에는 연결됐지만 읽기·제어 권한이 없는 상태. 예시 신호와 실제 미수신을 구분한다. */
internal class ConnectedWithoutReadings : VehicleSignalSource {
    override var connected = false
    override val caps = Capabilities(row2Occupancy = false, row2Recline = false, seatPosture = false,
        ambientControl = false, seatControl = false, climateControl = false,
        seatHeat = false, seatVent = false, steeringHeat = false, speed = false)
    override val live = emptySet<String>()
    override var paused = 0
    override fun connect(): Boolean { connected = true; return true }
    override fun resubscribeIfGranted() = false
    override fun refresh(name: String) = Unit
    override fun disconnect() { connected = false }
}
