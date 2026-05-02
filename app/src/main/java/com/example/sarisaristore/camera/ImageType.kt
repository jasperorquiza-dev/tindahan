package com.example.sarisaristore.camera

enum class ImageType(
    val directoryName: String,
    val filePrefix: String,
    val resultKey: String,
) {
    PRODUCT(
        directoryName = "products",
        filePrefix = "product",
        resultKey = "product_image_result",
    ),
    RECEIPT(
        directoryName = "receipts",
        filePrefix = "receipt",
        resultKey = "receipt_image_result",
    ),
    SIGNATURE(
        directoryName = "signatures",
        filePrefix = "signature",
        resultKey = "signature_image_result",
    ),
}
