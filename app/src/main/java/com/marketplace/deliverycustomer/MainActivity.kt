
package com.marketplace.deliverycustomer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapEventsReceiver
import android.view.Gravity
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher

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
    private lateinit var locationLabel: TextView
    private lateinit var homeHeader: LinearLayout
private lateinit var homeScroll: ScrollView
private lateinit var homeFooter: TextView
private lateinit var searchPreview: TextView
    private lateinit var searchInput: EditText

private var selectedCategory = "explore"
private var searchQuery = ""
    private lateinit var rootLayout: LinearLayout
private lateinit var bodyScroll: ScrollView

    private var mapView: MapView? = null
    private var deliveryMarker: Marker? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    private var merchantId = ""
    private var merchantName = ""
    private var deliveryFee = 40.0
    private var loadingMerchants = false
    private var loadingProducts = false
    private var placingOrder = false
    private var pendingPhone = ""

    private data class CartLine(
        val id: String,
        val name: String,
        val price: Double,
        var quantity: Int = 1
    )

    private val cart = linkedMapOf<String, CartLine>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName

        rootLayout = LinearLayout(this)
rootLayout.orientation = LinearLayout.VERTICAL

bodyScroll = ScrollView(this)

content = LinearLayout(this)
content.orientation = LinearLayout.VERTICAL
content.setPadding(dp(16), dp(16), dp(16), dp(16))

bodyScroll.addView(content)

rootLayout.addView(
    bodyScroll,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.MATCH_PARENT
    )
)

setContentView(rootLayout)

        title = "Delivery Marketplace"

        val user = auth.currentUser

        if (user != null && !user.isAnonymous) {
            showMarketplace(user)
        } else {
            if (user?.isAnonymous == true) {
                auth.signOut()
            }
            showAuthScreen()
        }
    }
    
private fun buildHomeHeader() {
    val header = LinearLayout(this)
    header.orientation = LinearLayout.HORIZONTAL
    header.gravity = Gravity.CENTER_VERTICAL
    header.setPadding(dp(12), dp(8), dp(12), dp(8))
    header.setBackgroundColor(Color.WHITE)

    searchInput = EditText(this)
    searchInput.hint = "Search food, merchants, places"
    searchInput.singleLine = true
    searchInput.textSize = 14f
    searchInput.setCompoundDrawablesWithIntrinsicBounds(
        android.R.drawable.ic_menu_search,
        0,
        0,
        0
    )
    searchInput.setPadding(dp(10), dp(8), dp(10), dp(8))

    val searchBackground = GradientDrawable()
    searchBackground.setColor(Color.rgb(245, 245, 245))
    searchBackground.cornerRadius = dp(12).toFloat()
    searchInput.background = searchBackground

    header.addView(
        searchInput,
        LinearLayout.LayoutParams(0, dp(46), 1f)
    )

    val profileButton = ImageButton(this)
    profileButton.setImageResource(android.R.drawable.ic_menu_myplaces)
    profileButton.contentDescription = "Open profile"
    profileButton.background = null
    profileButton.setOnClickListener {
        message("Profile page will be added in the next steps.")
    }

    header.addView(
        profileButton,
        LinearLayout.LayoutParams(dp(48), dp(48))
    )

    rootLayout.addView(
        header,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )
}
    private fun showAuthScreen(register: Boolean = false) {
        content.removeAllViews()
        mapView = null

        heading("DELIVERY MARKETPLACE", 26f)
        heading(
            if (register) "Create your account" else "Welcome back",
            21f
        )

        val nameField = makeField("Full name")
        val phoneField = makeField("Mobile number")

        if (!register) {
            nameField.visibility = android.view.View.GONE
            phoneField.visibility = android.view.View.GONE
        }

        val emailField = makeField("Email address").apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        val passwordField = makeField("Password").apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        button("Log in") {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()

            if (email.isBlank() || password.isBlank()) {
                message("Enter your email and password.")
                return@button
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user

                    if (user == null || user.isAnonymous) {
                        auth.signOut()
                        message("Please use a registered customer account.")
                    } else {
                        showMarketplace(user)
                    }
                }
                .addOnFailureListener {
                    message("Login failed: ${it.message}")
                }
        }

        button("Create new account") {
            val name = nameField.text.toString().trim()
            val phone = phoneField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()

            if (name.isBlank() || phone.isBlank() ||
                email.isBlank() || password.isBlank()
            ) {
                message("Enter your name, phone, email, and password.")
                return@button
            }

            if (password.length < 6) {
                message("Your password must be at least 6 characters.")
                return@button
            }

            pendingPhone = phone

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user

                    if (user == null) {
                        message("Account created, but sign-in failed.")
                        return@addOnSuccessListener
                    }

                    val profile = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()

                    user.updateProfile(profile)
                        .addOnCompleteListener {
                            showMarketplace(user)
                        }
                }
                .addOnFailureListener {
                    message("Registration failed: ${it.message}")
                }
        }

        button(
            if (register) "Back to login" else "Register a new account"
        ) {
            showAuthScreen(!register)
        }

        text(
            "Your account lets your orders be associated with your " +
                "registered customer ID."
        )
    }

    private fun showMarketplace(user: FirebaseUser) {
    content.removeAllViews()
    rootLayout.removeAllViews()

    loadingMerchants = false
    loadingProducts = false

    buildHomeHeader()

    rootLayout.addView(
        bodyScroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    // Keep the rest of your existing showMarketplace() code here,
    // starting with your current heading("Delivery Marketplace")

        loadingMerchants = false
        loadingProducts = false

        heading("Delivery Marketplace", 26f)

        val welcome = TextView(this).apply {
            text = "Signed in as ${user.email ?: "Customer"}"
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        content.addView(welcome)

        button("Log out") {
            auth.signOut()
            cart.clear()
            merchantId = ""
            merchantName = ""
            selectedLatitude = null
            selectedLongitude = null
            showAuthScreen()
        }

        heading("1. Choose your delivery location", 20f)

        text("Tap the map where you want your order delivered. " +
            "You can zoom in and move the map.")

        locationLabel = TextView(this).apply {
            text = "No delivery point selected yet."
            textSize = 14f
            setTextColor(Color.rgb(35, 90, 65))
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(locationLabel)

        val map = MapView(this)
        mapView = map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val mapParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(270)
        )
        content.addView(map, mapParams)

        val startPoint = GeoPoint(9.7392, 118.7353)
        map.controller.setZoom(13.0)
        map.controller.setCenter(startPoint)

        map.overlays.add(
            MapEventsOverlay(
                object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(
                        point: GeoPoint
                    ): Boolean {
                        selectDeliveryPoint(point)
                        return true
                    }

                    override fun longPressHelper(
                        point: GeoPoint
                    ): Boolean {
                        selectDeliveryPoint(point)
                        return true
                    }
                }
            )
        )

        text("Map data © OpenStreetMap contributors")

        button("Use map center as delivery point") {
            selectDeliveryPoint(
                map.mapCenter as GeoPoint
            )
        }

        heading("2. Delivery details", 20f)

        customerName = makeField("Your full name").apply {
            setText(user.displayName ?: "")
        }

        customerPhone = makeField("Mobile number").apply {
            setText(pendingPhone)
        }

        deliveryAddress = makeField(
            "Complete delivery address, house number, street, barangay"
        ).apply {
            setSingleLine(false)
            minLines = 2
            maxLines = 4
        }

        heading("3. Choose a merchant", 20f)

        merchantList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(merchantList)

        button("Refresh merchants") {
            loadMerchants()
        }

        heading("4. Menu", 20f)

        productList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(productList)

        heading("5. Your cart", 20f)

        cartLabel = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }
        content.addView(cartLabel)
        updateCart()

        button("Place cash-on-delivery order") {
            placeOrder()
        }

        loadMerchants()
    }

    private fun selectDeliveryPoint(point: GeoPoint) {
        selectedLatitude = point.latitude
        selectedLongitude = point.longitude

        locationLabel.text = String.format(
            java.util.Locale.US,
            "Selected location:\nLatitude: %.6f\nLongitude: %.6f",
            point.latitude,
            point.longitude
        )

        val map = mapView ?: return

        deliveryMarker?.let {
            map.overlays.remove(it)
        }

        deliveryMarker = Marker(map).apply {
            position = point
            title = "Delivery location"
            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )
        }

        deliveryMarker?.let {
            map.overlays.add(it)
        }

        map.invalidate()
    }

    private fun heading(text: String, size: Float = 20f) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35, 65, 55))
            setPadding(0, dp(18), 0, dp(10))
        })
    }

    private fun text(value: String) {
        content.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, dp(8))
        })
    }

    private fun makeField(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 16f
            setSingleLine(true)
            content.addView(
                this,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun button(
        label: String,
        action: () -> Unit
    ) {
        content.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        })
    }

    private fun listButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        parent.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        })
    }

    private fun message(value: String) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun loadMerchants() {
        if (loadingMerchants || !::merchantList.isInitialized) return

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
        if (user == null || user.isAnonymous) {
            message("Please log in with your customer account.")
            return
        }

        if (merchantId.isBlank() || cart.isEmpty()) {
            message("Choose a merchant and add products first.")
            return
        }

        val latitude = selectedLatitude
        val longitude = selectedLongitude

        if (latitude == null || longitude == null) {
            message("Tap the map to select your delivery location.")
            return
        }

        val name = customerName.text.toString().trim()
        val phone = customerPhone.text.toString().trim()
        val address = deliveryAddress.text.toString().trim()

        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
            message("Enter your name, phone, and full delivery address.")
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
            "deliveryLatitude" to latitude,
            "deliveryLongitude" to longitude,
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

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView?.onDetach()
        mapView = null
        super.onDestroy()
    }
}
