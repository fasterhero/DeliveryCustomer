
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
    private lateinit var merchantGrid: GridLayout
    private lateinit var cartItemsContainer: LinearLayout

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
searchInput.setSingleLine(true)
searchInput.isFocusable = false
searchInput.isClickable = true
searchInput.setOnClickListener {
    showSearchScreen()
}
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
    showProfileScreen()
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


private fun showSearchScreen() {
    content.removeAllViews()

    heading("Search", 24f)

    val searchField = EditText(this).apply {
        hint = "Search food, merchants, places"
        setSingleLine(true)
        textSize = 16f
        setText(searchQuery)
    }

    content.addView(
        searchField,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        )
    )

    button("Back to home") {
        val user = auth.currentUser
        if (user != null && !user.isAnonymous) {
            showMarketplace(user)
        } else {
            showAuthScreen()
        }
    }

    val results = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    content.addView(results)

    fun updateResults(query: String) {
        searchQuery = query
        results.removeAllViews()

        if (query.isBlank()) {
            results.addView(TextView(this).apply {
                text = "Search for food, merchants, or places."
                textSize = 14f
                setPadding(0, dp(12), 0, dp(12))
            })
            return
        }

        db.collection("merchants")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { merchants ->
                results.removeAllViews()

                var matches = 0

                for (doc in merchants.documents) {
                    val name = doc.getString("name") ?: "Merchant"

                    if (name.contains(query, ignoreCase = true)) {
                        matches++

                        listButton(results, name) {
                            merchantId = doc.id
                            merchantName = name
                            deliveryFee = (
                                doc.getDouble("deliveryFee")
                                    ?: doc.getLong("deliveryFee")?.toDouble()
                                    ?: 40.0
                            )

                            cart.clear()
                            updateCart()
                            loadProducts(doc.id, name)

                            val user = auth.currentUser
                            if (user != null && !user.isAnonymous) {
                                showMarketplace(user)
                                message("Selected $name. Choose a product below.")
                            }
                        }
                    }
                }

                if (matches == 0) {
                    results.addView(TextView(this).apply {
                        text = "No matching merchants found."
                        textSize = 14f
                    })
                }
            }
            .addOnFailureListener {
                message("Search failed: ${it.message}")
            }
    }

    searchField.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) {}

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) {
            updateResults(s?.toString().orEmpty())
        }

        override fun afterTextChanged(s: Editable?) {}
    })

    updateResults(searchQuery)
}


private fun showProfileScreen() {
    val user = auth.currentUser

    if (user == null || user.isAnonymous) {
        showAuthScreen()
        return
    }

    content.removeAllViews()

    heading("My Profile", 24f)

    val avatar = TextView(this).apply {
        text = "👤"
        textSize = 48f
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
    }
    content.addView(avatar)

    heading("Personal information", 18f)

    val nameField = makeField("Full name").apply {
        setText(user.displayName ?: "")
    }

    val phoneField = makeField("Mobile number").apply {
        setText(pendingPhone)
        inputType = InputType.TYPE_CLASS_PHONE
    }

    val emailField = makeField("Email address").apply {
        setText(user.email ?: "")
        isEnabled = false
        alpha = 0.7f
    }

    button("Save profile") {
        val name = nameField.text.toString().trim()
        val phone = phoneField.text.toString().trim()

        if (name.isBlank() || phone.isBlank()) {
            message("Enter your name and mobile number.")
            return@button
        }

        val profile = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()

        user.updateProfile(profile)
            .addOnSuccessListener {
                pendingPhone = phone

                db.collection("customers")
                    .document(user.uid)
                    .set(
                        mapOf(
                            "name" to name,
                            "phone" to phone,
                            "email" to (user.email ?: "")
                        )
                    )
                    .addOnSuccessListener {
                        message("Profile saved successfully.")
                    }
                    .addOnFailureListener {
                        message(
                            "Name updated, but phone could not be saved: " +
                                "${it.message}"
                        )
                    }
            }
            .addOnFailureListener {
                message("Could not update profile: ${it.message}")
            }
    }

    heading("Security", 18f)

    button("Change password") {
        val email = user.email

        if (email.isNullOrBlank()) {
            message("Your account has no email address.")
            return@button
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                message("Password reset instructions sent to $email")
            }
            .addOnFailureListener {
                message("Could not send reset email: ${it.message}")
            }
    }

    heading("My merchant reviews", 18f)

    text(
        "Your merchant reviews will appear here when the review feature " +
            "is connected."
    )

    button("Back to home") {
        showMarketplace(user)
    }
}


private fun buildCategoryShortcuts() {
    val categories = listOf(
        "Explore" to "explore",
        "Food" to "food",
        "Convenience" to "convenience",
        "Parcel" to "parcel",
        "Pabili" to "pabili"
    )

    val categoryRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    for ((label, category) in categories) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(8), dp(3), dp(8))
        }

        val circle = TextView(this).apply {
            text = when (category) {
                "explore" -> "⌂"
                "food" -> "🍔"
                "convenience" -> "🛍"
                "parcel" -> "📦"
                else -> "🛒"
            }
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 90, 65))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(235, 246, 239))
            }
        }

        item.addView(
            circle,
            LinearLayout.LayoutParams(dp(52), dp(52))
        )

        item.addView(TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, 0)
        })

        item.setOnClickListener {
            selectedCategory = category

            when (category) {
                "parcel" -> message("Parcel delivery will be added next.")
                "pabili" -> message("Pabili service will be added next.")
                else -> {
                    message("$label selected")
                    loadMerchants()
                }
            }
        }

        categoryRow.addView(
            item,
            LinearLayout.LayoutParams(0, dp(88), 1f)
        )
    }

    content.addView(categoryRow)
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

val adBanner = TextView(this).apply {
    text = ""
    visibility = View.GONE
    gravity = Gravity.CENTER
    setBackgroundColor(Color.rgb(245, 245, 245))
}

content.addView(
    adBanner,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(120)
    )
)
        
        buildCategoryShortcuts()

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

        heading("Available merchants", 20f)

merchantGrid = GridLayout(this).apply {
    columnCount = 2
    rowCount = GridLayout.UNDEFINED
    useDefaultMargins = false
}

content.addView(
    merchantGrid,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

// Keep merchantList as a compatibility container for existing code.
merchantList = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    visibility = View.GONE
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
        cartItemsContainer = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
}

content.addView(
    cartItemsContainer,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)
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
    merchantGrid.removeAllViews()

    merchantGrid.addView(
        TextView(this).apply {
            text = "Loading merchants..."
            textSize = 14f
        },
        GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, 2)
        }
    )

    db.collection("merchants")
        .whereEqualTo("active", true)
        .get()
        .addOnSuccessListener { results ->
            loadingMerchants = false
            merchantGrid.removeAllViews()

            if (results.isEmpty) {
                merchantGrid.addView(
                    TextView(this).apply {
                        text = "No active merchants found."
                        textSize = 14f
                    }
                )
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

val isOpen = doc.getBoolean("isOpen")
    ?: doc.getBoolean("open")
    ?: true

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), Color.rgb(225, 230, 225))
                    }
                    isClickable = true
                    isFocusable = true
                }

                card.addView(TextView(this).apply {
                    text = "🍽️"
                    textSize = 28f
                })

                card.addView(TextView(this).apply {
                    text = name
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(35, 65, 55))
                })

                card.addView(TextView(this).apply {
    text = if (isOpen) "OPEN NOW" else "CLOSED"
    textSize = 11f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(
        if (isOpen) {
            Color.rgb(30, 130, 75)
        } else {
            Color.rgb(190, 55, 55)
        }
    )
    setPadding(0, dp(6), 0, 0)
})

                card.addView(TextView(this).apply {
                    text = "Delivery ₱${fee.toInt()}"
                    textSize = 12f
                    setTextColor(Color.DKGRAY)
                })

                card.setOnClickListener {
    if (!isOpen) {
        message("$name is currently closed.")
        return@setOnClickListener
    }

    merchantId = id
                    merchantName = name
                    deliveryFee = fee
                    cart.clear()
                    updateCart()
                    loadProducts(id, name)
                    message("Selected $name. Choose a product below.")
                }

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                    )
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }

                merchantGrid.addView(card, params)
            }
        }
        .addOnFailureListener {
            loadingMerchants = false
            merchantGrid.removeAllViews()
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
    if (!::cartLabel.isInitialized ||
        !::cartItemsContainer.isInitialized
    ) return

    cartItemsContainer.removeAllViews()

    if (cart.isEmpty()) {
        val emptyText = TextView(this).apply {
            text = "Your cart is empty."
            textSize = 15f
            setPadding(0, 12, 0, 12)
        }
        cartItemsContainer.addView(emptyText)
    } else {
        cart.values.toList().forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 12, 10, 12)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    setStroke(1, Color.LTGRAY)
                    cornerRadius = 12f
                }
            }

            val itemName = TextView(this).apply {
                text = item.name
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }

            val itemPrice = TextView(this).apply {
                text = "₱${(item.price * item.quantity).toInt()}"
                textSize = 14f
            }

            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val minusButton = Button(this).apply {
                text = "−"
                setOnClickListener {
                    val current = cart[item.id] ?: return@setOnClickListener
                    if (current.quantity > 1) {
                        current.quantity--
                    }
                    updateCart()
                }
            }

            val quantityText = TextView(this).apply {
                text = "  ${item.quantity}  "
                textSize = 16f
                gravity = Gravity.CENTER
            }

            val plusButton = Button(this).apply {
                text = "+"
                setOnClickListener {
                    val current = cart[item.id] ?: return@setOnClickListener
                    current.quantity++
                    updateCart()
                }
            }

            val removeButton = Button(this).apply {
                text = "Remove"
                setTextColor(Color.RED)
                setOnClickListener {
                    cart.remove(item.id)
                    updateCart()
                }
            }

            controls.addView(minusButton)
            controls.addView(quantityText)
            controls.addView(plusButton)
            controls.addView(removeButton)

            row.addView(itemName)
            row.addView(itemPrice)
            row.addView(controls)

            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10
            }

            cartItemsContainer.addView(row, rowParams)
        }
    }

    val subtotal = cart.values.sumOf {
        it.price * it.quantity
    }

    cartLabel.text =
        "Subtotal: ₱${subtotal.toInt()}\n" +
        "Delivery: ₱${deliveryFee.toInt()}\n" +
        "Total: ₱${(subtotal + deliveryFee).toInt()}"
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
