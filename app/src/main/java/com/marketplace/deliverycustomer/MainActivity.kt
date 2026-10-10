
package com.marketplace.deliverycustomer

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : Activity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var content: LinearLayout
    private lateinit var merchantList: LinearLayout
    private lateinit var productList: LinearLayout
    private lateinit var customerName: EditText
    private lateinit var customerPhone: EditText
    private lateinit var deliveryAddress: EditText
    private lateinit var cartLabel: TextView

    private var merchantId = ""
    private var merchantName = ""
    private var deliveryFee = 40.0
    private var loadingMerchants = false
    private var loadingProducts = false
    private var placingOrder = false

    private data class CartLine(
        val id: String,
        val name: String,
        val price: Double,
        var quantity: Int = 1
    )

    private val cart = linkedMapOf<String, CartLine>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 32)
        }

        scroll.addView(content)
        setContentView(scroll)

        title("Delivery Marketplace")
        heading("Customer details", 22f)

        customerName = edit("Your full name")
        customerPhone = edit("Mobile number")
        deliveryAddress = edit("Complete delivery address")

        button("Load merchants") {
            loadMerchants()
        }

        heading("Available merchants")
        merchantList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(merchantList)

        heading("Menu")
        productList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(productList)

        heading("Your cart")
        cartLabel = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }
        content.addView(cartLabel)
        updateCart()

        button("Place cash-on-delivery order") {
            placeOrder()
        }

        auth.signInAnonymously()
            .addOnSuccessListener {
                loadMerchants()
            }
            .addOnFailureListener {
                message("Login failed: ${it.message}")
            }
    }

    private fun heading(text: String, size: Float = 20f) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.rgb(35, 65, 55))
            setPadding(0, 18, 0, 10)
        })
    }

    private fun edit(hintText: String): EditText {
        val field = EditText(this)
        field.hint = hintText
        field.textSize = 16f
        field.setSingleLine(
            hintText != "Complete delivery address"
        )
        content.addView(field)
        return field
    }

    private fun button(
        text: String,
        action: () -> Unit
    ) {
        content.addView(Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        })
    }

    private fun listButton(
        parent: LinearLayout,
        text: String,
        action: () -> Unit
    ) {
        parent.addView(Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        })
    }

    private fun message(text: String) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun loadMerchants() {
        if (loadingMerchants) return

        loadingMerchants = true
        merchantList.removeAllViews()
        merchantList.addView(TextView(this).apply {
            text = "Loading merchants..."
        })

        db.collection("merchants")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { results ->
                loadingMerchants = false
                merchantList.removeAllViews()

                if (results.isEmpty) {
                    merchantList.addView(TextView(this).apply {
                        text = "No active merchants found."
                    })
                    return@addOnSuccessListener
                }

                for (doc in results.documents) {
                    val id = doc.id
                    val name = doc.getString("name") ?: "Merchant"
                    val fee = (
                        doc.getDouble("deliveryFee")
                            ?: doc.getLong("deliveryFee")?.toDouble()
                            ?: 40.0
                        )

                    listButton(
                        merchantList,
                        "$name • Delivery ₱${fee.toInt()}"
                    ) {
                        merchantId = id
                        merchantName = name
                        deliveryFee = fee
                        cart.clear()
                        updateCart()
                        loadProducts(id, name)
                    }
                }
            }
            .addOnFailureListener {
                loadingMerchants = false
                merchantList.removeAllViews()
                message("Could not load merchants: ${it.message}")
            }
    }

    private fun loadProducts(id: String, name: String) {
        if (loadingProducts) return

        loadingProducts = true
        productList.removeAllViews()
        productList.addView(TextView(this).apply {
            text = "Loading menu for $name..."
        })

        db.collection("merchants")
            .document(id)
            .collection("products")
            .whereEqualTo("available", true)
            .get()
            .addOnSuccessListener { results ->
                loadingProducts = false
                productList.removeAllViews()

                if (results.isEmpty) {
                    productList.addView(TextView(this).apply {
                        text = "This merchant has no available products."
                    })
                    return@addOnSuccessListener
                }

                for (doc in results.documents) {
                    val productId = doc.id
                    val productName =
                        doc.getString("name") ?: "Product"
                    val price = (
                        doc.getDouble("price")
                            ?: doc.getLong("price")?.toDouble()
                            ?: 0.0
                        )

                    listButton(
                        productList,
                        "$productName — ₱${price.toInt()}  + Add"
                    ) {
                        val line = cart[productId]

                        if (line == null) {
                            cart[productId] = CartLine(
                                productId,
                                productName,
                                price
                            )
                        } else {
                            line.quantity++
                        }

                        updateCart()
                    }
                }
            }
            .addOnFailureListener {
                loadingProducts = false
                productList.removeAllViews()
                message("Could not load menu: ${it.message}")
            }
    }

    private fun updateCart() {
        if (!::cartLabel.isInitialized) return

        val subtotal = cart.values.sumOf {
            it.price * it.quantity
        }

        cartLabel.text = if (cart.isEmpty()) {
            "Your cart is empty."
        } else {
            cart.values.joinToString("\n") {
                "${it.name} × ${it.quantity} — ₱" +
                    "${(it.price * it.quantity).toInt()}"
            } + "\n\nSubtotal: ₱${subtotal.toInt()}" +
                "\nDelivery: ₱${deliveryFee.toInt()}" +
                "\nTotal: ₱${(subtotal + deliveryFee).toInt()}"
        }
    }

    private fun placeOrder() {
        if (placingOrder) return

        val user = auth.currentUser
        if (user == null) {
            message("Please wait for customer sign-in.")
            return
        }

        if (merchantId.isBlank() || cart.isEmpty()) {
            message("Choose a merchant and add products first.")
            return
        }

        val name = customerName.text.toString().trim()
        val phone = customerPhone.text.toString().trim()
        val address = deliveryAddress.text.toString().trim()

        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
            message("Enter your name, phone, and delivery address.")
            return
        }

        val subtotal = cart.values.sumOf {
            it.price * it.quantity
        }
        val total = subtotal + deliveryFee

        val items = cart.values.map {
            mapOf(
                "productId" to it.id,
                "name" to it.name,
                "quantity" to it.quantity,
                "unitPrice" to it.price,
                "lineTotal" to it.price * it.quantity
            )
        }

        val order = hashMapOf<String, Any?>(
            "customerId" to user.uid,
            "customerName" to name,
            "customerPhone" to phone,
            "deliveryAddress" to address,
            "merchantId" to merchantId,
            "merchantName" to merchantName,
            "items" to items,
            "subtotal" to subtotal,
            "deliveryFee" to deliveryFee,
            "total" to total,
            "paymentMethod" to "COD",
            "status" to "placed",
            "riderId" to null,
            "createdAt" to FieldValue.serverTimestamp()
        )

        placingOrder = true

        db.collection("orders")
            .add(order)
            .addOnSuccessListener { ref ->
                placingOrder = false
                cart.clear()
                updateCart()
                message("Order placed! Order ID: ${ref.id}")
            }
            .addOnFailureListener {
                placingOrder = false
                message("Order failed: ${it.message}")
            }
    }
}
