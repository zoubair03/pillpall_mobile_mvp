package com.example.ui.components

import com.example.data.remote.dto.DoseType

fun doseLabel(dose: DoseType): String = when (dose) {
    DoseType.MORNING -> "Matin"
    DoseType.MIDDAY -> "Midi"
    DoseType.NIGHT -> "Soir"
}
