package com.example.doswriter.editor

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class KeyDef(
    @SerializedName("label") val label: String,
    @SerializedName("weight") val weight: Float = 1f,
    @SerializedName("type") val type: String? = "CHAR", // CHAR, CMD, MODIFIER, SPACER
    @SerializedName("value") val value: String? = null,
    @SerializedName("cmd") val cmd: String? = null,
    @SerializedName("shift_val") val shift_val: String? = null,
    @SerializedName("shift_cmd") val shift_cmd: String? = null,
    @SerializedName("ctrl_val") val ctrl_val: String? = null,
    @SerializedName("ctrl_cmd") val ctrl_cmd: String? = null,
    @SerializedName("alt_val") val alt_val: String? = null,
    @SerializedName("alt_cmd") val alt_cmd: String? = null,
    @SerializedName("row_span") val row_span: Int = 1,
    @SerializedName("icon_res") val icon_res: String? = null,
    @SerializedName("font_scale") val font_scale: Float? = null
)

@Keep
data class RowDef(
    @SerializedName("keys") val keys: List<KeyDef>
)

@Keep
data class LayoutDef(
    @SerializedName("layout_name") val layout_name: String,
    @SerializedName("rows") val rows: List<RowDef>
)
