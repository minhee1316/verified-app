package com.verified.incar.vehicle

import com.verified.incar.core.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** VehicleGear 값(구글 CarGearViewer 예제의 표): 모름 0, N 1, R 2, P 4, D 8, 수동 1단 16. */
class GearMappingTest {
    @Test fun unknownGearKeepsPreviousInsteadOfDrive() {
        assertNull(gearOf(0x0000))
        assertEquals(Gear.N, gearOf(0x0001))
        assertEquals(Gear.R, gearOf(0x0002))
        assertEquals(Gear.P, gearOf(0x0004))
        assertEquals(Gear.D, gearOf(0x0008))
        assertEquals(Gear.D, gearOf(0x0010))
    }
}
