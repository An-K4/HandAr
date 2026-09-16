package com.example.handar.effect

data class EffectState(
    val id: String,
    val gesture: GestureRecognizer,
    val asset: EffectAsset?,
    val soundRes: Int?,
    val background: EffectBackground? = null,
    val sizeSource: SizeSource = SizeSource.PalmRadius,
    val anchorSource: AnchorSource = AnchorSource.PalmCenter
) {
    init {
        require(asset != null || soundRes != null) {
            "EffectState không có cả hình lẫn tiếng — trạng thái này vô nghĩa: $id"
        }
    }
}
