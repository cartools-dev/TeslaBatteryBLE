// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.vehicle

/** The Tesla models a VIN names in its fourth character. */
enum class TeslaModel(
    val displayName: String,
) {
    ModelS("Model S"),
    Model3("Model 3"),
    ModelX("Model X"),
    ModelY("Model Y"),
    Cybertruck("Cybertruck"),
    Roadster("Roadster"),
    Semi("Semi"),
}

/** What a VIN says about the car: its model and model year. Either is null when the VIN does not give one. */
data class VinInfo(
    val model: TeslaModel?,
    val modelYear: Int?,
) {
    /** "Model Y 2023", "Model Y" or "2023"; null with neither. */
    val label: String?
        get() = listOfNotNull(model?.displayName, modelYear).takeIf { it.isNotEmpty() }?.joinToString(" ")
}

private val EPA_MODELS = setOf(TeslaModel.ModelS, TeslaModel.Model3, TeslaModel.ModelX, TeslaModel.ModelY)

/**
 * The fueleconomy.gov page listing the EPA ratings for this car's model and year, where the new-car
 * range can be copied from; null when the VIN gives no year or the model is not one EPA lists here.
 */
fun epaSearchUrl(info: VinInfo): String? {
    val year = info.modelYear
    val model = info.model?.takeIf { it in EPA_MODELS }
    return if (year == null || model == null) {
        null
    } else {
        "https://www.fueleconomy.gov/feg/PowerSearch.do?action=noform&path=1&year1=$year&year2=$year" +
            "&make=Tesla&baseModel=${model.displayName.replace(" ", "%20")}&srchtyp=ymm"
    }
}

private const val VIN_LENGTH = 17
private const val MODEL_INDEX = 3
private const val YEAR_INDEX = 9
private const val FIRST_YEAR = 2010

/** The year letters in order from 2010: the VIN standard leaves out I, O, Q, U, Z and 0, then the digits 1-9 follow for 2031-2039. */
private const val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY123456789"

private val MODEL_CODES =
    mapOf(
        'S' to TeslaModel.ModelS,
        '3' to TeslaModel.Model3,
        'X' to TeslaModel.ModelX,
        'Y' to TeslaModel.ModelY,
        'C' to TeslaModel.Cybertruck,
        'R' to TeslaModel.Roadster,
        'T' to TeslaModel.Semi,
    )

/**
 * Reads the model and model year from a VIN: the fourth character is the model,
 * the tenth the model year (letters cycle every 30 years, so this reads 2010-2039).
 * Null when the text is not a 17-character VIN.
 */
fun decodeVin(vin: String): VinInfo? {
    val text = vin.trim().uppercase()
    if (text.length != VIN_LENGTH) return null
    val year = YEAR_CODES.indexOf(text[YEAR_INDEX]).takeIf { it >= 0 }?.let { FIRST_YEAR + it }
    return VinInfo(MODEL_CODES[text[MODEL_INDEX]], year)
}
