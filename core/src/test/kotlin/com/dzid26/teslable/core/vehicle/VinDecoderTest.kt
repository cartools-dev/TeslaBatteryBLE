// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VinDecoderTest {
    @Test
    fun `reads the model and year from a European Model Y`() {
        val info = decodeVin("XP7YGCEK0PB123456")!!
        assertEquals(TeslaModel.ModelY, info.model)
        assertEquals(2023, info.modelYear)
        assertEquals("Model Y 2023", info.label)
    }

    @Test
    fun `reads the model letters and digits`() {
        assertEquals(TeslaModel.Model3, decodeVin("5YJ3E1EA7KF123456")!!.model)
        assertEquals(TeslaModel.ModelS, decodeVin("5YJSA1E26HF123456")!!.model)
        assertEquals(TeslaModel.ModelX, decodeVin("5YJXCBE22GF123456")!!.model)
        assertEquals(TeslaModel.Cybertruck, decodeVin("7G2CEHED5RA123456")!!.model)
    }

    @Test
    fun `year letters run from 2010 and the digits follow`() {
        assertEquals(2010, decodeVin("5YJ3E1EA0AF123456")!!.modelYear)
        assertEquals(2026, decodeVin("5YJ3E1EA0TF123456")!!.modelYear)
        assertEquals(2031, decodeVin("5YJ3E1EA01F123456")!!.modelYear)
    }

    @Test
    fun `leaves out what the VIN does not say`() {
        val info = decodeVin("5YJ?E1EA0?F123456")!!
        assertNull(info.model)
        assertNull(info.modelYear)
        assertNull(info.label)
    }

    @Test
    fun `links the EPA search for the model and year`() {
        assertEquals(
            "https://www.fueleconomy.gov/feg/PowerSearch.do?action=noform&path=1&year1=2019&year2=2019" +
                "&make=Tesla&baseModel=Model%203&srchtyp=ymm",
            epaSearchUrl(decodeVin("5YJ3E1EA7KF123456")!!),
        )
        assertNull(epaSearchUrl(VinInfo(TeslaModel.ModelY, null)))
        assertNull(epaSearchUrl(VinInfo(null, 2023)))
        assertNull(epaSearchUrl(VinInfo(TeslaModel.Semi, 2023)))
    }

    @Test
    fun `needs seventeen characters`() {
        assertNull(decodeVin("XP7Y"))
    }
}
