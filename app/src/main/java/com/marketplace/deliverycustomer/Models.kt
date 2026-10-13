
package com.marketplace.deliverycustomer

data class Merchant(
    val id: String,
    val name: String,
    val fee: Double,
    val isOpen: Boolean = true,
    val category: String = "food",
    val eta: String = "25-35 min",
    val rating: Double = 0.0,
    val imageUrl: String = "",
    val description: String = "",
    val phone: String = ""
)

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val description: String,
    val imageUrl: String
)

data class CartLine(
    val id: String,
    val name: String,
    val price: Double,
    var qty: Int
)
